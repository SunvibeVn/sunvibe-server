package vn.sunvibe.auth.core.application.service.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.auth.core.application.exception.auth.SendingMailFailedException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationThrottledException;
import vn.sunvibe.auth.core.application.query.auth.RegistrationTokenResult;
import vn.sunvibe.auth.core.application.query.auth.VerificationSendResult;
import vn.sunvibe.auth.core.application.shared.VerificationPolicy;
import vn.sunvibe.auth.core.application.shared.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.shared.external.email.MailSendGatePort;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.auth.RegistrationEmailChallenge;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.user.EmailOccupancy;
import vn.sunvibe.auth.core.testsupport.InMemoryRegistrationEmailChallengeRepository;
import vn.sunvibe.auth.core.testsupport.NoDatabaseTransactions;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;
import vn.sunvibe.auth.core.testsupport.RecordingEmailService;
import vn.sunvibe.auth.core.testsupport.TestOtpHasher;
import vn.sunvibe.auth.core.testsupport.UnsupportedUserRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** auth-email-access 4.7, 4.8, S-7..S-13, D4-5: a real code for anyone, and a difference only after the proof. */
class RegistrationEmailVerificationServiceTest {

    private static final String EMAIL = "New.User@Example.com";
    private static final String KEY = "new.user@example.com";

    /** The gate as a test wants it. */
    private static final class Gate implements MailSendGatePort {
        boolean full;
        boolean overBudget;
        int entered;
        int left;
        int charged;

        @Override
        public boolean tryEnter() {
            if (!full) {
                entered++;
            }
            return !full;
        }

        @Override
        public boolean tryCharge() {
            if (!overBudget) {
                charged++;
            }
            return !overBudget;
        }

        @Override
        public void leave() {
            left++;
        }
    }

    private final InMemoryRegistrationEmailChallengeRepository challenges = new InMemoryRegistrationEmailChallengeRepository();
    private final RecordingEmailService email = new RecordingEmailService();
    private final RecordingAuditLog audit = new RecordingAuditLog();
    private final Gate gate = new Gate();
    private final NoDatabaseTransactions transactions = new NoDatabaseTransactions();
    private final List<String> locks = new ArrayList<>();
    private boolean lockFree = true;
    private EmailOccupancy occupancy = EmailOccupancy.FREE;
    private int occupancyReads;
    private RegistrationEmailVerificationService service;

    @BeforeEach
    void setUp() {
        AtomicLong ids = new AtomicLong();
        service = new RegistrationEmailVerificationService(new UnsupportedUserRepository() {
            @Override
            public EmailOccupancy emailOccupancy(String key, UserId selfOrNull) {
                occupancyReads++;
                return occupancy;
            }
        }, challenges, email, gate, key -> {
            locks.add(key);
            return lockFree;
        }, ids::incrementAndGet, new TestOtpHasher(),
                new VerificationPolicy(Duration.ofMinutes(10), 5, Duration.ofSeconds(60), 3), audit,
                new TransactionTemplate(transactions));
    }

    private String lastCode() {
        return email.ofKind("REGISTRATION").getLast().code();
    }

    private static String wrong(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    // ---------- send ----------

    @Test
    void send_usesTheCanonicalKey_answersMasked() {
        VerificationSendResult result = service.sendCode(EMAIL);

        assertEquals(KEY, email.sent.getFirst().to());
        assertEquals(KEY, challenges.all().getFirst().getEmailKey());
        assertEquals(VerificationChannel.EMAIL, result.channel());
        assertFalse(result.toString().contains("new.user"), "masked");
        assertEquals(List.of("reg-email:" + KEY), locks);
        assertEquals(List.of(AuditEventType.REGISTRATION_EMAIL_CODE_SENT), audit.types());
    }

    /** D4-5: the answer cannot depend on an account, because no account is read. */
    @Test
    void send_readsNoAccount() {
        service.sendCode(EMAIL);

        assertEquals(0, occupancyReads);
    }

    @Test
    void send_cooldown_429() {
        service.sendCode(EMAIL);

        VerificationThrottledException refused = assertThrows(VerificationThrottledException.class,
                () -> service.sendCode(EMAIL));

        assertEquals(VerificationThrottledException.Reason.RESEND_TOO_SOON, refused.getReason());
        assertEquals(1, email.sent.size());
    }

    @Test
    void send_dailyCap_429() {
        for (int i = 0; i < 3; i++) {
            service.sendCode(EMAIL);
            challenges.age(Duration.ofSeconds(90));
        }

        VerificationThrottledException refused = assertThrows(VerificationThrottledException.class,
                () -> service.sendCode(EMAIL));

        assertEquals(VerificationThrottledException.Reason.DAILY_LIMIT, refused.getReason());
        assertEquals(3, email.sent.size());
    }

    @Test
    void send_newCode_burnsTheUnusedOne() {
        service.sendCode(EMAIL);
        String first = lastCode();
        challenges.age(Duration.ofSeconds(90));

        service.sendCode(EMAIL);

        VerificationException refused = assertThrows(VerificationException.class, () -> service.verifyCode(EMAIL, first));
        assertEquals(VerificationException.Reason.CODE_INCORRECT, refused.getReason(), "only the latest code is compared");
    }

    /** spec 4.7: a new code burns only an unused code; a verified registration token stays usable. */
    @Test
    void send_newCode_keepsVerifiedToken() {
        service.sendCode(EMAIL);
        RegistrationTokenResult token = service.verifyCode(EMAIL, lastCode());
        challenges.age(Duration.ofSeconds(90));

        service.sendCode(EMAIL);

        RegistrationEmailChallenge verified = challenges.all().getFirst();
        assertEquals(RegistrationEmailChallenge.hashToken(token.registrationToken()), verified.getTokenHash());
        assertTrue(verified.getTokenExpiresAt().isAfter(verified.getVerifiedAt()), "the token was not revoked");
    }

    /** The unique index is the backstop of the lock: PostgreSQL has aborted the transaction, so nothing else runs. */
    @Test
    void send_indexViolation_429_noFurtherSql() {
        challenges.indexViolationOnInsert = true;

        VerificationThrottledException refused = assertThrows(VerificationThrottledException.class,
                () -> service.sendCode(EMAIL));

        assertEquals(VerificationThrottledException.Reason.RESEND_TOO_SOON, refused.getReason());
        assertTrue(email.sent.isEmpty());
        assertEquals(0, gate.charged);
    }

    @Test
    void send_lockMiss_429() {
        lockFree = false;

        VerificationThrottledException refused = assertThrows(VerificationThrottledException.class,
                () -> service.sendCode(EMAIL));

        assertEquals(VerificationThrottledException.Reason.RESEND_TOO_SOON, refused.getReason());
        assertTrue(challenges.all().isEmpty());
    }

    /** spec 5.4: the gate comes before the transaction, so a refused request never holds a connection. */
    @Test
    void send_gateFull_503_noConnectionHeld() {
        gate.full = true;

        assertThrows(SendingMailFailedException.class, () -> service.sendCode(EMAIL));

        assertEquals(0, transactions.begun);
        assertTrue(email.sent.isEmpty());
        assertEquals(0, gate.left, "no place was taken");
    }

    @Test
    void send_overBudget_503_noMail() {
        gate.overBudget = true;

        assertThrows(SendingMailFailedException.class, () -> service.sendCode(EMAIL));

        assertTrue(email.sent.isEmpty());
        assertEquals(1, gate.left, "the place is given back");
    }

    @Test
    void send_throttled_chargesNothing_leavesTheGate() {
        service.sendCode(EMAIL);
        int charged = gate.charged;

        assertThrows(VerificationThrottledException.class, () -> service.sendCode(EMAIL));

        assertEquals(charged, gate.charged);
        assertEquals(gate.entered, gate.left);
    }

    /** S-2: a use case called directly refuses what the DTO would have. */
    @Test
    void useCase_refusedValue_throws_noRow() {
        assertThrows(AuthValidationException.class, () -> service.sendCode("not an email"));

        assertTrue(challenges.all().isEmpty());
        assertEquals(0, gate.entered);
    }

    // ---------- verify ----------

    @Test
    void verify_free_returnsToken() {
        service.sendCode(EMAIL);

        RegistrationTokenResult result = service.verifyCode("  new.user@EXAMPLE.com ", lastCode());

        assertNotNull(result.registrationToken());
        RegistrationEmailChallenge challenge = challenges.all().getFirst();
        assertEquals(RegistrationEmailChallenge.hashToken(result.registrationToken()), challenge.getTokenHash());
        assertNotEquals(result.registrationToken(), challenge.getTokenHash(), "only the hash is kept");
        assertEquals(AuditEventType.REGISTRATION_EMAIL_VERIFIED, audit.last().type());
    }

    @Test
    void verify_wrong_expired_used_burned() {
        assertEquals(VerificationException.Reason.CODE_EXPIRED, refusedReason(EMAIL, "123456"), "none sent");

        service.sendCode(EMAIL);
        String code = lastCode();
        assertEquals(VerificationException.Reason.CODE_INCORRECT, refusedReason(EMAIL, wrong(code)));
        assertEquals(1, challenges.all().getFirst().getFailedAttempts(), "the wrong code is counted");

        for (int i = 2; i < 5; i++) {
            assertEquals(VerificationException.Reason.CODE_INCORRECT, refusedReason(EMAIL, wrong(code)));
        }
        assertEquals(VerificationException.Reason.CODE_ATTEMPTS_EXCEEDED, refusedReason(EMAIL, wrong(code)));
        assertEquals(VerificationException.Reason.CODE_EXPIRED, refusedReason(EMAIL, code), "a burned code stays burned");

        challenges.age(Duration.ofSeconds(90));
        service.sendCode(EMAIL);
        String used = lastCode();
        service.verifyCode(EMAIL, used);
        assertEquals(VerificationException.Reason.CODE_EXPIRED, refusedReason(EMAIL, used), "a used code is gone");

        challenges.age(Duration.ofSeconds(90));
        service.sendCode(EMAIL);
        String expired = lastCode();
        challenges.age(Duration.ofMinutes(11));
        assertEquals(VerificationException.Reason.CODE_EXPIRED, refusedReason(EMAIL, expired));
    }

    /** spec 4.8: only the holder of the mailbox learns this, and the code is used up. */
    @Test
    void verify_verifiedAccount_409Exists_codeUsed() {
        service.sendCode(EMAIL);
        occupancy = EmailOccupancy.VERIFIED;

        assertEquals(VerificationException.Reason.EMAIL_ACCOUNT_EXISTS, refusedReason(EMAIL, lastCode()));

        assertNotNull(challenges.all().getFirst().getConsumedAt());
        assertNull(challenges.all().getFirst().getTokenHash(), "no token");
    }

    @Test
    void verify_unverifiedOrKeyless_409NotAvailable() {
        service.sendCode(EMAIL);
        occupancy = EmailOccupancy.OTHER;

        assertEquals(VerificationException.Reason.EMAIL_NOT_AVAILABLE, refusedReason(EMAIL, lastCode()));

        assertNotNull(challenges.all().getFirst().getConsumedAt());
    }

    /** A wrong code learns nothing about an account. */
    @Test
    void verify_wrongCode_readsNoAccount() {
        service.sendCode(EMAIL);
        occupancy = EmailOccupancy.VERIFIED;

        assertEquals(VerificationException.Reason.CODE_INCORRECT, refusedReason(EMAIL, wrong(lastCode())));

        assertEquals(0, occupancyReads);
    }

    private VerificationException.Reason refusedReason(String address, String code) {
        return assertThrows(VerificationException.class, () -> service.verifyCode(address, code)).getReason();
    }
}
