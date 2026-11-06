package vn.sunvibe.auth.core.application.service.auth;

import vn.sunvibe.auth.core.testsupport.AuthTestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.sunvibe.auth.core.application.command.auth.PasswordForgotCommand;
import vn.sunvibe.auth.core.application.command.auth.PasswordForgotSmsCommand;
import vn.sunvibe.auth.core.application.query.auth.PasswordForgotResult;
import vn.sunvibe.auth.core.application.shared.PasswordResetPolicy;
import vn.sunvibe.auth.core.application.shared.RecoveryMailDispatcher;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.testsupport.FakeMailGate;
import vn.sunvibe.auth.core.testsupport.FakeMailPool;
import vn.sunvibe.auth.core.testsupport.InMemoryPasswordResetTokenRepository;
import vn.sunvibe.auth.core.testsupport.InMemoryUserRepository;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;
import vn.sunvibe.auth.core.testsupport.RecordingEmailService;
import vn.sunvibe.auth.core.testsupport.RecordingSmsSender;
import vn.sunvibe.auth.core.testsupport.TestOtpHasher;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class PasswordForgotServiceTest {

    private static final String EMAIL = "a@example.com";
    private static final String PHONE = "0912345678";
    private static final String PHONE_KEY = "+84912345678";

    private AppUser user;
    private InMemoryPasswordResetTokenRepository resetTokens;
    private final RecordingEmailService email = new RecordingEmailService();
    private final RecordingSmsSender sms = new RecordingSmsSender();
    private final RecordingAuditLog audit = new RecordingAuditLog();
    private final TestOtpHasher hasher = new TestOtpHasher();
    private final FakeMailPool mailPool = new FakeMailPool();
    private final FakeMailGate gate = new FakeMailGate();
    private final List<String> locks = new ArrayList<>();
    private boolean lockFree = true;
    private PasswordForgotService service;

    @BeforeEach
    void setUp() {
        user = AuthTestUsers.withPhone(UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"), "Nguyen Van A", PHONE,
                Email.create(EMAIL), Password.create("hashed"), ProfileImage.create(null), null, new HashSet<>(), Instant.now());
        user.bindVerifiedEmail(Email.create(EMAIL), Instant.now());
        resetTokens = new InMemoryPasswordResetTokenRepository();
        AtomicLong ids = new AtomicLong();
        service = new PasswordForgotService(new InMemoryUserRepository().with(user), resetTokens, email,
                ids::incrementAndGet, new PasswordResetPolicy(5, Duration.ofSeconds(60), 3), audit, sms, AuthTestUsers.phoneOn(),
                key -> {
                    locks.add(key);
                    return lockFree;
                }, hasher, new RecoveryMailDispatcher(mailPool, gate, audit));
    }

    private PasswordForgotResult forgot(String address) {
        return service.execute(new PasswordForgotCommand(address));
    }

    private PasswordForgotResult forgotBySms(String phone) {
        return service.executeBySms(new PasswordForgotSmsCommand(phone));
    }

    private List<RecordingEmailService.Mail> resetMails() {
        return email.ofKind("RESET");
    }

    // ---------- e-mail ----------

    @Test
    void execute_verifiedEmailGetsACode() {
        PasswordForgotResult response = forgot(EMAIL);

        assertEquals(PasswordForgotService.GENERIC_MESSAGE, response.message());
        assertNull(response.resetToken(), "the e-mail flow never returns the token");
        assertEquals(1, resetMails().size());
        assertEquals(1, resetTokens.all().size());
        assertEquals(VerificationChannel.EMAIL, resetTokens.all().getFirst().getChannel());
        PasswordResetToken stored = resetTokens.all().getFirst();
        assertEquals(hasher.hash(stored.getId(), resetMails().getFirst().code()), stored.getOtpHash());
        assertEquals(user.getCredentialVersion(), stored.getCredentialVersion(), "bound to the version of the account");
        assertEquals(List.of("reset:" + EMAIL), locks);
    }

    /** spec 5.6: the mail leaves after the commit, so the answer and a rollback do not depend on it. */
    @Test
    void execute_mailLeavesAfterTheCommit() {
        mailPool.holding = true;

        forgot(EMAIL);

        assertTrue(resetMails().isEmpty());
        assertTrue(audit.events.isEmpty(), "nothing was sent yet");
        mailPool.held.getFirst().run();
        assertEquals(1, resetMails().size());
        assertEquals("EMAIL SENT", audit.last().details());
    }

    /** The mail carries the value; the row only its hash. */
    @Test
    void execute_mailCarriesTheToken_rowItsHash() {
        forgot(EMAIL);

        assertEquals(resetTokens.all().getFirst().getToken(), PasswordResetToken.hashToken(email.lastResetToken));
    }

    /** spec 4.7: a parallel request holds the address; this one looks like any other. */
    @Test
    void forgot_lockMiss_neutral() {
        lockFree = false;

        PasswordForgotResult response = forgot(EMAIL);

        assertEquals(PasswordForgotService.GENERIC_MESSAGE, response.message());
        assertNull(response.resetToken());
        assertNotNull(response.expiresAt());
        assertTrue(resetTokens.all().isEmpty());
        assertTrue(resetMails().isEmpty());
        assertEquals("EMAIL LOCK_MISS", audit.last().details());
    }

    @Test
    void forgot_gateRefused_neutral() {
        gate.full = true;

        PasswordForgotResult response = forgot(EMAIL);

        assertEquals(PasswordForgotService.GENERIC_MESSAGE, response.message());
        assertTrue(resetMails().isEmpty());
        assertEquals("EMAIL SEND_FAILED", audit.last().details());
    }

    /** No address of a person in the audit trail. */
    @Test
    void execute_auditSubjectIsMasked() {
        forgot(EMAIL);
        forgot("nobody@example.com");

        audit.events.forEach(event -> assertFalse(String.valueOf(event.subject()).contains("a@example.com")));
        assertEquals("n***@example.com", audit.last().subject());
    }

    /** Someone may have typed another person's address at sign-up: only a verified e-mail can reset. */
    @Test
    void execute_unverifiedEmailGetsNothing() {
        user.setEmailVerifiedAt(null);

        PasswordForgotResult response = forgot(EMAIL);

        assertEquals(PasswordForgotService.GENERIC_MESSAGE, response.message());
        assertTrue(resetMails().isEmpty());
        assertEquals("EMAIL NO_ELIGIBLE_ACCOUNT", audit.last().details());
    }

    /** The answer must not tell an attacker which e-mails are registered. */
    @Test
    void execute_unknownEmailAnswersExactlyTheSameWithoutSendingAnything() {
        PasswordForgotResult known = forgot(EMAIL);
        PasswordForgotResult unknown = forgot("nobody@example.com");

        assertEquals(known.message(), unknown.message());
        assertEquals(known.codeLifetime(), unknown.codeLifetime());
        assertNotNull(unknown.expiresAt());
        assertEquals(1, resetMails().size(), "only the known address gets an e-mail");
        assertEquals("EMAIL SENT", audit.events.get(0).details());
        assertEquals("EMAIL NO_ELIGIBLE_ACCOUNT", audit.events.get(1).details());
        assertNull(audit.events.get(1).userId());
    }

    @Test
    void execute_blockedAccountGetsNoCode() {
        user.setUserStatus(UserStatus.LOCK);

        forgot(EMAIL);

        assertTrue(resetMails().isEmpty());
    }

    @Test
    void execute_emailIsMatchedCaseInsensitively() {
        forgot("  A@Example.COM ");

        assertEquals(1, resetMails().size());
    }

    /** Repeated clicks or a script must not flood the inbox. */
    @Test
    void execute_doesNotResendWithinTheCooldown() {
        forgot(EMAIL);
        forgot(EMAIL);

        assertEquals(1, resetMails().size());
        assertEquals(1, resetTokens.all().size());
        assertEquals("EMAIL COOLDOWN_NOT_SENT", audit.last().details());
    }

    @Test
    void execute_newCodeBurnsThePreviousOnes() {
        forgot(EMAIL);
        Long firstId = resetTokens.all().getFirst().getId();
        resetTokens.age(firstId, Duration.ofSeconds(120));

        forgot(EMAIL);

        assertEquals(2, resetMails().size());
        assertFalse(resetTokens.get(firstId).isValid(), "the older code no longer works");
        assertEquals(1, resetTokens.all().stream().filter(t -> t.isValid()).count());
    }

    /** Caps blind OTP guessing: at most N codes per destination per day. */
    @Test
    void execute_stopsSendingAfterTheDailyLimit() {
        for (int i = 0; i < 3; i++) {
            forgot(EMAIL);
            resetTokens.all().forEach(t -> resetTokens.age(t.getId(), Duration.ofSeconds(120)));
        }

        forgot(EMAIL);

        assertEquals(3, resetMails().size());
        assertEquals("EMAIL DAILY_LIMIT", audit.last().details());
    }

    // ---------- SMS ----------

    /** An account without a verified email keeps the baseline of SMS reset (auth-email-access D4-9). */
    private void withoutVerifiedEmail() {
        user.setEmailVerifiedAt(null);
    }

    /** auth-email-access D4-9: whoever holds an unproven number gets no reset of an account with a proven mailbox. */
    @Test
    void executeBySms_verifiedEmailUnverifiedPhone_sendsNothing() {
        PasswordForgotResult response = forgotBySms(PHONE);

        assertEquals(PasswordForgotService.GENERIC_SMS_MESSAGE, response.message());
        assertTrue(sms.sent.isEmpty());
        assertTrue(resetTokens.all().isEmpty());
    }

    @Test
    void executeBySms_textsACodeAndReturnsTheToken() {
        withoutVerifiedEmail();
        PasswordForgotResult response = forgotBySms(PHONE);

        assertEquals(PasswordForgotService.GENERIC_SMS_MESSAGE, response.message());
        assertEquals(1, sms.sent.size());
        assertEquals(PHONE_KEY, sms.sent.getFirst().phoneNumber());
        var token = resetTokens.all().getFirst();
        assertEquals(VerificationChannel.PHONE, token.getChannel());
        assertEquals(token.getToken(), PasswordResetToken.hashToken(response.resetToken()));
        assertEquals(token.getOtpHash(), hasher.hash(token.getId(), sms.lastCode()));
        assertFalse(response.toString().contains(sms.lastCode()), "the OTP only travels by SMS");
    }

    /** Works for an unverified phone too: the SMS reaches whoever owns the number. */
    @Test
    void executeBySms_worksBeforeThePhoneIsVerified() {
        withoutVerifiedEmail();
        assertFalse(user.isPhoneVerified());

        forgotBySms(PHONE);

        assertEquals(1, sms.sent.size());
    }

    @Test
    void executeBySms_unknownPhoneLooksTheSameButSendsNothing() {
        PasswordForgotResult unknown = forgotBySms("0999999999");

        assertEquals(PasswordForgotService.GENERIC_SMS_MESSAGE, unknown.message());
        assertNotNull(unknown.resetToken(), "a decoy token keeps the answer indistinguishable");
        assertTrue(sms.sent.isEmpty());
        assertTrue(resetTokens.all().isEmpty());
    }

    @Test
    void executeBySms_withinCooldownSendsNothingAndKeepsTheFirstTokenValid() {
        withoutVerifiedEmail();
        PasswordForgotResult first = forgotBySms(PHONE);
        forgotBySms(PHONE);

        assertEquals(1, sms.sent.size());
        assertEquals(1, resetTokens.all().size());
        assertTrue(resetTokens.all().getFirst().isValid());
        assertEquals(PasswordResetToken.hashToken(first.resetToken()), resetTokens.all().getFirst().getToken());
        assertEquals("PHONE COOLDOWN_NOT_SENT", audit.last().details());
    }

    /**
     * A repeated request must look exactly like one for an unknown number:
     * handing out the same token again would reveal that the number is registered.
     */
    @Test
    void executeBySms_repeatWithinCooldownIsIndistinguishableFromAnUnknownNumber() {
        PasswordForgotResult first = forgotBySms(PHONE);
        PasswordForgotResult repeated = forgotBySms(PHONE);
        PasswordForgotResult unknownFirst = forgotBySms("0999999999");
        PasswordForgotResult unknownRepeated = forgotBySms("0999999999");

        assertNotEquals(first.resetToken(), repeated.resetToken());
        assertNotEquals(unknownFirst.resetToken(), unknownRepeated.resetToken());
        assertEquals(first.message(), unknownRepeated.message());
    }

    @Test
    void execute_repeatWithinCooldownGetsAFreshExpiryLikeAnUnknownAddress() throws InterruptedException {
        PasswordForgotResult first = forgot(EMAIL);
        Thread.sleep(5);
        PasswordForgotResult repeated = forgot(EMAIL);

        assertTrue(repeated.expiresAt().isAfter(first.expiresAt()),
                "the expiry of the code already sent must not be echoed back");
    }
}
