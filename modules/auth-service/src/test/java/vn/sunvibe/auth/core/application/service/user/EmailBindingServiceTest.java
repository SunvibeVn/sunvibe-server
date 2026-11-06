package vn.sunvibe.auth.core.application.service.user;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.application.command.auth.LoginCommand;
import vn.sunvibe.auth.core.application.command.user.EmailBindConfirmCommand;
import vn.sunvibe.auth.core.application.command.user.EmailBindSendCommand;
import vn.sunvibe.auth.core.application.exception.auth.AccountDisabledException;
import vn.sunvibe.auth.core.application.exception.auth.AccountTemporarilyLockedException;
import vn.sunvibe.auth.core.application.exception.auth.SendingMailFailedException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationThrottledException;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.shared.VerificationPolicy;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.usecase.user.LoginUseCase;
import vn.sunvibe.auth.core.application.usecase.user.ReauthenticationResult;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.auth.VerificationCode;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationPurpose;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.Email;
import vn.sunvibe.auth.core.domain.model.user.vo.Password;
import vn.sunvibe.auth.core.domain.model.user.vo.ProfileImage;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;
import vn.sunvibe.auth.core.testsupport.AuthTestUsers;
import vn.sunvibe.auth.core.testsupport.FakeMailGate;
import vn.sunvibe.auth.core.testsupport.InMemoryEmailNotificationRepository;
import vn.sunvibe.auth.core.testsupport.InMemoryRefreshTokenRepository;
import vn.sunvibe.auth.core.testsupport.InMemoryUserRepository;
import vn.sunvibe.auth.core.testsupport.InMemoryVerificationCodeRepository;
import vn.sunvibe.auth.core.testsupport.NoDatabaseTransactions;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;
import vn.sunvibe.auth.core.testsupport.RecordingEmailService;
import vn.sunvibe.auth.core.testsupport.SignedIn;
import vn.sunvibe.auth.core.testsupport.TestOtpHasher;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** auth-email-access 4.9, 5.5, S-21..S-26, D4-12. */
class EmailBindingServiceTest {

    private static final UserId USER_ID = UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV");
    private static final UserId OTHER_ID = UserId.create("01BX5ZZKBKACTAV9WEVGEMMVRZ");
    private static final String PASSWORD = "Right@Pass1";
    private static final String NEW_EMAIL = "New.Address@Example.com";
    private static final String NEW_KEY = "new.address@example.com";

    private final InMemoryVerificationCodeRepository codes = new InMemoryVerificationCodeRepository();
    private final InMemoryEmailNotificationRepository notices = new InMemoryEmailNotificationRepository();
    private final InMemoryRefreshTokenRepository refreshTokens = new InMemoryRefreshTokenRepository();
    /** For each mail: whether every older proof was still open when it left. */
    private final List<Boolean> proofsOpenAtMail = new ArrayList<>();
    private boolean mailDown;
    private final RecordingEmailService email = new RecordingEmailService() {
        @Override
        public void sendEmailBindCodeEmail(String to, String code) {
            if (mailDown) {
                throw new SendingMailFailedException("down");
            }
            proofsOpenAtMail.add(codes.all().stream().allMatch(c -> c.getConsumedAt() == null));
            super.sendEmailBindCodeEmail(to, code);
        }
    };
    private final RecordingAuditLog audit = new RecordingAuditLog();
    private final FakeMailGate gate = new FakeMailGate();
    private final NoDatabaseTransactions transactions = new NoDatabaseTransactions();
    private final List<String> locks = new ArrayList<>();
    private final List<AppUser> sessions = new ArrayList<>();
    private boolean lockFree = true;
    private ReauthenticationResult reauthentication;
    private AppUser user;
    private InMemoryUserRepository users;
    private EmailBindingService service;

    @BeforeEach
    void setUp() {
        user = AuthTestUsers.withPhone(USER_ID, "Nguyen Van A", "0912345678", null, Password.create("hashed"),
                ProfileImage.create(null), null, new HashSet<>(Set.of("OWNER", "SALE")), Instant.now());
        user.setUserStatus(UserStatus.ACTIVE);
        user.setCredentialVersion(2);
        users = new InMemoryUserRepository().with(user);
        reauthentication = ReauthenticationResult.ok(2);
        AtomicLong ids = new AtomicLong();
        service = new EmailBindingService(users, codes, notices, refreshTokens, (id, password) -> reauthentication,
                new LoginUseCase() {
                    @Override
                    public AuthResult execute(LoginCommand loginCommand, ClientInfo clientInfo) {
                        throw new AssertionError("The new session must not go through the password login");
                    }

                    @Override
                    public AuthResult loginInternal(AppUser appUser, ClientInfo clientInfo) {
                        sessions.add(appUser);
                        return AuthResult.of(appUser, "access", "refresh", null);
                    }
                }, email, gate, key -> {
                    locks.add(key);
                    return lockFree;
                }, ids::incrementAndGet, new VerificationPolicy(Duration.ofMinutes(10), 5, Duration.ofSeconds(60), 3),
                audit, new TransactionTemplate(transactions), new TestOtpHasher());
        SignedIn.as(USER_ID.getValue());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void send(String address) {
        service.sendCode(new EmailBindSendCommand(address, PASSWORD));
    }

    private AuthResult confirm(String address, String code) {
        return service.confirm(new EmailBindConfirmCommand(address, code, ClientInfo.unknown()));
    }

    private String lastCode() {
        return email.ofKind("EMAIL_BIND").getLast().code();
    }

    private static String wrong(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    private AppUser otherAccountHolding(String emailKey, boolean verified) {
        AppUser other = AuthTestUsers.withPhone(OTHER_ID, "Other User", "0987654321", null, Password.create("hashed"),
                ProfileImage.create(null), null, new HashSet<>(), Instant.now());
        other.setEmail(Email.create(emailKey));
        other.setEmailKey(emailKey);
        other.setEmailVerifiedAt(verified ? Instant.now() : null);
        users.with(other);
        return other;
    }

    private VerificationException.Reason refusedReason(String address, String code) {
        return assertThrows(VerificationException.class, () -> confirm(address, code)).getReason();
    }

    // ---------- send ----------

    @Test
    void send_mailsTheCode_boundToAddressAndVersion() {
        send(NEW_EMAIL);

        VerificationCode code = codes.all().getFirst();
        assertEquals(NEW_KEY, email.sent.getFirst().to());
        assertEquals(NEW_KEY, code.getDestination());
        assertEquals(VerificationPurpose.EMAIL_BIND, code.getPurpose());
        assertEquals(2L, code.getCredentialVersion());
        assertEquals(List.of("bind-user:" + USER_ID.getValue(), "bind-dest:" + NEW_KEY), locks);
        assertEquals(List.of(AuditEventType.EMAIL_BIND_CODE_SENT), audit.types());
    }

    @Test
    void send_wrongPassword_400_counted() {
        reauthentication = ReauthenticationResult.wrong();

        AuthValidationException refused = assertThrows(AuthValidationException.class, () -> send(NEW_EMAIL));

        assertEquals(AuthFieldName.CURRENT_PASSWORD, refused.getField());
        assertTrue(email.sent.isEmpty());
        assertEquals(0, gate.entered);
        assertEquals(AuditEventType.EMAIL_BIND_FAILED, audit.last().type());
    }

    @Test
    void send_atLimit_423() {
        reauthentication = ReauthenticationResult.locked(Duration.ofMinutes(15));

        AccountTemporarilyLockedException refused = assertThrows(AccountTemporarilyLockedException.class, () -> send(NEW_EMAIL));

        assertEquals(Duration.ofMinutes(15), refused.getRetryAfter());
        assertEquals(0, transactions.begun);
    }

    /** spec 4.9: the password comes first and no row lock is held after it. */
    @Test
    void send_holdsNoRowLock_alsoWithCounterAboveZero() {
        user.setReauthFailedAttempts(3);

        send(NEW_EMAIL);

        assertTrue(users.calls.isEmpty(), "the send takes advisory locks only");
    }

    @Test
    void bind_ownUnverifiedStoredAddress_ok() {
        user.setEmail(Email.from("Mine@Example.com"));

        send("mine@example.com");
        confirm("mine@example.com", lastCode());

        assertEquals("mine@example.com", user.getEmailKey());
        assertTrue(user.isEmailVerified());
    }

    @Test
    void send_ownVerifiedAddress_alreadyVerified() {
        user.bindVerifiedEmail(Email.create("mine@example.com"), Instant.now());

        VerificationException refused = assertThrows(VerificationException.class, () -> send("MINE@example.com"));

        assertEquals(VerificationException.Reason.ALREADY_VERIFIED, refused.getReason());
        assertTrue(email.sent.isEmpty());
    }

    /** S-23: before the proof nothing shows that another account holds the address. */
    @Test
    void send_anotherAccountsAddress_same200() {
        otherAccountHolding(NEW_KEY, true);

        send(NEW_EMAIL);

        assertEquals(NEW_KEY, email.sent.getFirst().to());
    }

    @Test
    void send_cooldown_429() {
        send(NEW_EMAIL);

        VerificationThrottledException refused = assertThrows(VerificationThrottledException.class, () -> send("b@example.com"));

        assertEquals(VerificationThrottledException.Reason.RESEND_TOO_SOON, refused.getReason());
    }

    @Test
    void send_perUserCap() {
        for (int i = 0; i < 3; i++) {
            send("a" + i + "@example.com");
            codes.all().forEach(c -> codes.age(c.getId(), Duration.ofSeconds(90)));
        }

        VerificationThrottledException refused = assertThrows(VerificationThrottledException.class, () -> send(NEW_EMAIL));

        assertEquals(VerificationThrottledException.Reason.DAILY_LIMIT, refused.getReason());
    }

    @Test
    void send_perDestinationCap() {
        for (int i = 0; i < 3; i++) {
            codes.save(VerificationCode.issueForBind((long) (100 + i), "user-" + i, NEW_KEY, "123456", 0, Instant.now(),
                    Duration.ofMinutes(10)));
        }

        VerificationThrottledException refused = assertThrows(VerificationThrottledException.class, () -> send(NEW_EMAIL));

        assertEquals(VerificationThrottledException.Reason.DAILY_LIMIT, refused.getReason());
        assertTrue(email.sent.isEmpty());
    }

    @Test
    void send_lockMiss_429() {
        lockFree = false;

        assertThrows(VerificationThrottledException.class, () -> send(NEW_EMAIL));

        assertTrue(codes.all().isEmpty());
    }

    /** A place not given back would stop every mail once the four are gone. */
    @Test
    void send_alwaysLeavesTheGate() {
        send(NEW_EMAIL);
        assertEquals(1, gate.left);

        codes.all().forEach(c -> codes.age(c.getId(), Duration.ofSeconds(90)));
        mailDown = true;
        assertThrows(SendingMailFailedException.class, () -> send(NEW_EMAIL));
        assertEquals(2, gate.left);

        mailDown = false;
        lockFree = false;
        assertThrows(VerificationThrottledException.class, () -> send(NEW_EMAIL));
        assertEquals(gate.entered, gate.left);
    }

    @Test
    void send_gateRefused_503() {
        gate.full = true;

        assertThrows(SendingMailFailedException.class, () -> send(NEW_EMAIL));

        assertEquals(0, transactions.begun);
    }

    /** spec 5.4: the older proof stays until the mail left, so a confirm never waits on SMTP behind this row. */
    @Test
    void send_previousProof_burnedAfterMail() {
        send(NEW_EMAIL);
        String first = lastCode();
        codes.all().forEach(c -> codes.age(c.getId(), Duration.ofSeconds(90)));

        send(NEW_EMAIL);

        assertEquals(List.of(true, true), proofsOpenAtMail, "burned only after the mail left");
        assertNotNull(codes.all().getFirst().getConsumedAt(), "the first code is burned");
        assertNull(codes.all().getLast().getConsumedAt());
        assertEquals(VerificationException.Reason.CODE_INCORRECT, refusedReason(NEW_EMAIL, first));
    }

    // ---------- confirm ----------

    @Test
    void confirm_keepsIdRolesOwnershipActors() {
        send(NEW_EMAIL);

        AuthResult result = confirm(NEW_EMAIL, lastCode());

        assertEquals(USER_ID, user.getId());
        assertEquals(Set.of("OWNER", "SALE"), user.getUserRoles());
        assertEquals(NEW_KEY, user.getEmailKey());
        assertEquals(NEW_KEY, user.getEmail().getValue());
        assertTrue(user.isEmailVerified());
        assertEquals(3L, user.getCredentialVersion(), "older proofs and access tokens die");
        assertEquals("refresh", result.refreshToken());
    }

    @Test
    void confirm_pendingBecomesActive_rolesKept() {
        user.setUserStatus(UserStatus.PENDING);
        send(NEW_EMAIL);

        confirm(NEW_EMAIL, lastCode());

        assertEquals(UserStatus.ACTIVE, user.getUserStatus());
        assertEquals(Set.of("OWNER", "SALE"), user.getUserRoles());
    }

    /** S-23: found only after the proof; the code is used up and nothing changes. */
    @Test
    void confirm_otherAccount_409_nothingChanged_codeUsed() {
        send(NEW_EMAIL);
        otherAccountHolding(NEW_KEY, false);

        assertEquals(VerificationException.Reason.EMAIL_NOT_AVAILABLE, refusedReason(NEW_EMAIL, lastCode()));

        assertNull(user.getEmail());
        assertEquals(2L, user.getCredentialVersion());
        assertNotNull(codes.all().getFirst().getConsumedAt());
        assertTrue(sessions.isEmpty());
        assertTrue(refreshTokens.revocations.isEmpty());
    }

    @Test
    void confirm_wrongCode_countedAndBurned() {
        send(NEW_EMAIL);
        String code = lastCode();

        for (int i = 0; i < 4; i++) {
            assertEquals(VerificationException.Reason.CODE_INCORRECT, refusedReason(NEW_EMAIL, wrong(code)));
        }
        assertEquals(VerificationException.Reason.CODE_ATTEMPTS_EXCEEDED, refusedReason(NEW_EMAIL, wrong(code)));

        assertEquals(VerificationException.Reason.CODE_EXPIRED, refusedReason(NEW_EMAIL, code));
    }

    @Test
    void confirm_wrongDestination_refused() {
        send(NEW_EMAIL);

        assertEquals(VerificationException.Reason.CODE_EXPIRED, refusedReason("other@example.com", lastCode()));

        assertNull(user.getEmail());
    }

    /** I-5: a proof made before a change of credentials is void. */
    @Test
    void confirm_versionChanged_refused() {
        send(NEW_EMAIL);
        user.changePassword("hash-of-the-reset");

        assertEquals(VerificationException.Reason.CODE_EXPIRED, refusedReason(NEW_EMAIL, lastCode()));

        assertNull(user.getEmail());
    }

    /** spec 4.7: a code of another purpose on the same channel never answers. */
    @Test
    void confirm_filtersPurpose() {
        codes.save(VerificationCode.issue(50L, USER_ID.getValue(), VerificationChannel.EMAIL, NEW_KEY, "123456", Instant.now(),
                Duration.ofMinutes(10)));

        assertEquals(VerificationException.Reason.CODE_EXPIRED, refusedReason(NEW_EMAIL, "123456"));
    }

    @Test
    void confirm_endsOtherSessions_newCookie() {
        send(NEW_EMAIL);

        confirm(NEW_EMAIL, lastCode());

        assertEquals(List.of("user:" + USER_ID.getValue() + ":EMAIL_CHANGED"), refreshTokens.revocations);
        assertEquals(List.of(user), sessions);
    }

    /** S-22: a code never moves LOCK or TERMINATED. */
    @Test
    void bindAndReset_leaveLockAndTerminatedAlone() {
        send(NEW_EMAIL);
        String code = lastCode();
        for (UserStatus status : List.of(UserStatus.LOCK, UserStatus.TERMINATED)) {
            user.setUserStatus(status);

            assertThrows(AccountDisabledException.class, () -> confirm(NEW_EMAIL, code));

            assertEquals(status, user.getUserStatus());
            assertNull(user.getEmail());
        }
    }

    @Test
    void replace_writesOneNotice() {
        user.bindVerifiedEmail(Email.create("old@example.com"), Instant.now());
        reauthentication = ReauthenticationResult.ok(3);
        send(NEW_EMAIL);

        confirm(NEW_EMAIL, lastCode());

        assertEquals(1, notices.all().size());
        assertEquals("old@example.com", notices.all().getFirst().getDestination());
        assertEquals(AuditEventType.EMAIL_REPLACED, audit.last().type());
    }

    @Test
    void firstBind_writesNoNotice() {
        send(NEW_EMAIL);

        confirm(NEW_EMAIL, lastCode());

        assertTrue(notices.all().isEmpty());
        assertEquals(AuditEventType.EMAIL_BOUND, audit.last().type());
    }

    @Test
    void notice_namesNoNewAddress() {
        user.bindVerifiedEmail(Email.create("old@example.com"), Instant.now());
        reauthentication = ReauthenticationResult.ok(3);
        send(NEW_EMAIL);

        confirm(NEW_EMAIL, lastCode());

        assertFalse(notices.all().getFirst().toString().contains(NEW_KEY));
        assertFalse(notices.all().getFirst().getDestination().contains("new.address"));
    }
}
