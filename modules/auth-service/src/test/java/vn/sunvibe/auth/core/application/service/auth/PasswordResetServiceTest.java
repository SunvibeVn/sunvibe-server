package vn.sunvibe.auth.core.application.service.auth;

import vn.sunvibe.auth.core.testsupport.AuthTestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.sunvibe.auth.core.application.command.auth.PasswordResetCommand;
import vn.sunvibe.auth.core.application.exception.auth.AccountDisabledException;
import vn.sunvibe.auth.core.application.exception.auth.AuthenticationFailedException;
import vn.sunvibe.auth.core.application.shared.PasswordResetPolicy;
import vn.sunvibe.auth.core.application.shared.RecoveryMailDispatcher;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.domain.exception.auth.InvalidOtpException;
import vn.sunvibe.auth.core.domain.exception.auth.PasswordResetTokenException;
import vn.sunvibe.auth.core.domain.model.auth.RefreshToken;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.testsupport.FakeMailGate;
import vn.sunvibe.auth.core.testsupport.FakeMailPool;
import vn.sunvibe.auth.core.testsupport.InMemoryPasswordResetTokenRepository;
import vn.sunvibe.auth.core.testsupport.InMemoryRefreshTokenRepository;
import vn.sunvibe.auth.core.testsupport.InMemoryUserRepository;
import vn.sunvibe.auth.core.testsupport.RecordingEmailService;
import vn.sunvibe.auth.core.testsupport.RecordingSmsSender;
import vn.sunvibe.shared.time.AppClock;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;
import vn.sunvibe.auth.core.testsupport.TestOtpHasher;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain-JUnit test with hand-written fakes (Mockito isn't on this module's
 * classpath): a locked/disabled user must not reset their password through
 * the public OTP reset endpoint, a successful reset ends every session, and
 * wrong OTP codes are limited per token.
 */
class PasswordResetServiceTest {

    private static final String RESET_TOKEN = "reset-token";
    private static final String OTP = "123456";
    private static final String EMAIL = "a@example.com";
    private static final String ORIGINAL_PASSWORD_HASH = "original-hash";
    private static final UserId USER_ID = UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV");

    private static final PasswordEncoder PASSWORD_ENCODER = new PasswordEncoder() {
        @Override
        public String encode(CharSequence rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return encode(rawPassword).equals(encodedPassword);
        }
    };

    private AppUser user;
    private InMemoryUserRepository users;
    private InMemoryPasswordResetTokenRepository resetTokens;
    private InMemoryRefreshTokenRepository refreshTokens;
    private RecordingEmailService email;
    private RecordingSmsSender sms;
    private PasswordResetService service;
    private RecordingAuditLog audit;
    private final TestOtpHasher hasher = new TestOtpHasher();
    private final FakeMailPool mailPool = new FakeMailPool();

    @BeforeEach
    void setUp() {
        user = AuthTestUsers.withPhone(USER_ID, "Nguyen Van A", "0912345678", Email.create(EMAIL),
                Password.create(ORIGINAL_PASSWORD_HASH), ProfileImage.create(null), IdCardNumber.create("123456789012"),
                new HashSet<>(), Instant.now());
        user.setEmailKey(EMAIL);
        users = new InMemoryUserRepository().with(user);
        resetTokens = new InMemoryPasswordResetTokenRepository();
        resetTokens.save(token(1L, RESET_TOKEN, VerificationChannel.EMAIL, EMAIL, 0L));
        refreshTokens = new InMemoryRefreshTokenRepository();
        audit = new RecordingAuditLog();
        email = new RecordingEmailService();
        sms = new RecordingSmsSender();
        service = new PasswordResetService(resetTokens, users, PASSWORD_ENCODER, email, refreshTokens,
                new PasswordResetPolicy(5, Duration.ofSeconds(60)), audit, sms, AuthTestUsers.phoneOn(), hasher,
                new RecoveryMailDispatcher(mailPool, new FakeMailGate(), audit));
    }

    private PasswordResetToken token(long id, String value, VerificationChannel channel, String destination, Long version) {
        Instant now = AppClock.currentUTC();
        return PasswordResetToken.restore(id, PasswordResetToken.hashToken(value), USER_ID.getValue(), channel, destination,
                now, now.plusSeconds(900), false, null, 0, hasher.hash(id, OTP), version);
    }

    private void assertRefusedWithoutAChange() {
        PasswordResetTokenException refused = assertThrows(PasswordResetTokenException.class,
                () -> service.verifyOtpAndResetPassword(request(OTP)));

        assertEquals(PasswordResetTokenException.Reason.TOKEN_INVALID, refused.getReason());
        assertEquals(ORIGINAL_PASSWORD_HASH, user.getPassword().getHashedValue());
        assertEquals(0, users.saveCount);
        assertTrue(refreshTokens.revocations.isEmpty());
    }

    /** auth-email-access spec 4.7: never rebound by string. */
    @Test
    void reset_otherAddressOnTheAccount_refused() {
        user.setEmailKey("moved@example.com");

        assertRefusedWithoutAChange();
    }

    /** I-5: a change of credentials voids what was issued before it. */
    @Test
    void reset_versionMoved_refused() {
        user.setCredentialVersion(1);

        assertRefusedWithoutAChange();
    }

    @Test
    void reset_rowWithoutVersion_refused() {
        resetTokens.save(token(1L, RESET_TOKEN, VerificationChannel.EMAIL, EMAIL, null));

        assertRefusedWithoutAChange();
    }

    /** spec 5.5: the order is OTP, then version: a wrong code tells nothing about the account. */
    @Test
    void reset_wrongCodeOnAVoidToken_countedAsWrongCode() {
        user.setCredentialVersion(1);

        assertThrows(InvalidOtpException.class, () -> service.verifyOtpAndResetPassword(request("000000")));
    }

    /** D4-10. */
    @Test
    void reset_verifiedEmailOfAPendingAccount_activates() {
        user.setEmailVerifiedAt(AppClock.currentUTC());
        assertEquals(UserStatus.PENDING, user.getUserStatus());

        service.verifyOtpAndResetPassword(request(OTP));

        assertEquals(UserStatus.ACTIVE, user.getUserStatus());
    }

    @Test
    void reset_clearsTheReauthCounterToo() {
        user.setReauthFailedAttempts(4);
        user.setReauthLockedUntil(Instant.now().plus(Duration.ofMinutes(15)));

        service.verifyOtpAndResetPassword(request(OTP));

        assertEquals(0, user.getReauthFailedAttempts());
        assertNull(user.getReauthLockedUntil());
    }

    /** spec 5.4: the users row is held here, so no mail is sent from under it. */
    @Test
    void reset_confirmationLeavesAfterTheCommit() {
        mailPool.holding = true;

        service.verifyOtpAndResetPassword(request(OTP));

        assertTrue(email.sent.isEmpty());
        mailPool.held.getFirst().run();
        assertEquals(List.of(new RecordingEmailService.Mail("RESET_CONFIRMATION", EMAIL, null)), email.sent);
    }

    @Test
    void reset_auditSubjectIsMasked() {
        service.verifyOtpAndResetPassword(request(OTP));

        assertEquals("a***@example.com", audit.last().subject());
    }

    private static PasswordResetCommand request(String otp) {
        return new PasswordResetCommand(RESET_TOKEN, otp, "N3wP@ssw0rd!");
    }

    /** auth-email-access spec 5.4: the token owner as a plain value, the user row, then the token. */
    @Test
    void reset_locksUserBeforeToken() {
        resetTokens.calls = users.calls;

        service.verifyOtpAndResetPassword(request(OTP));

        assertEquals(List.of("read-token-owner", "lock-user", "lock-token"), users.calls);
    }

    /** auth-email-access I-5: sessions, access tokens and proofs of the old password die with it. */
    @Test
    void reset_raisesVersion() {
        service.verifyOtpAndResetPassword(request(OTP));

        assertEquals(1L, user.getCredentialVersion(), "the token was issued for version 0");
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"LOCK", "TERMINATED", "DELETED"})
    void verifyOtpAndResetPassword_rejectsBlockedUserWithoutChangingAnything(UserStatus status) {
        user.setUserStatus(status);

        AuthenticationFailedException exception = assertThrows(AuthenticationFailedException.class,
                () -> service.verifyOtpAndResetPassword(request(OTP)));

        assertEquals("Account is locked or disabled.", exception.getMessage());
        assertEquals(ORIGINAL_PASSWORD_HASH, user.getPassword().getHashedValue());
        assertEquals(0, users.saveCount);
        assertFalse(resetTokens.get(1L).isUsed());
        assertTrue(refreshTokens.revocations.isEmpty());
        assertTrue(email.sent.isEmpty());
    }

    /** Whoever knew the old password may hold a session: a reset must end them all. */
    @Test
    void verifyOtpAndResetPassword_revokesEverySessionOfTheUser() {
        refreshTokens.save(RefreshToken.startSession(1L, USER_ID, "hash-of-old-session", AppClock.currentUTC(),
                Duration.ofDays(7), Duration.ofDays(30), "Mozilla/5.0", "203.0.113.7"));

        service.verifyOtpAndResetPassword(request(OTP));

        assertEquals("hashed:N3wP@ssw0rd!", user.getPassword().getHashedValue());
        assertEquals(List.of("user:" + USER_ID.getValue() + ":PASSWORD_RESET"), refreshTokens.revocations);
        assertTrue(refreshTokens.get(1L).isRevoked());
        assertTrue(resetTokens.get(1L).isUsed());
        assertEquals(List.of(new RecordingEmailService.Mail("RESET_CONFIRMATION", EMAIL, null)), email.sent);
        assertEquals(List.of(AuditEventType.PASSWORD_RESET_COMPLETED), audit.types());
        assertEquals(USER_ID.getValue(), audit.last().userId());
    }

    @Test
    void verifyOtpAndResetPassword_updatesTheLastUpdateTimestamp() {
        user.setLastUpdate(Instant.now().minus(Duration.ofDays(30)));

        service.verifyOtpAndResetPassword(request(OTP));

        assertTrue(user.getLastUpdate().isAfter(Instant.now().minus(Duration.ofMinutes(1))));
    }

    @Test
    void verifyOtpAndResetPassword_liftsALoginLockout() {
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(15)));

        service.verifyOtpAndResetPassword(request(OTP));

        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
    }

    @Test
    void verifyOtpAndResetPassword_wrongOtpIsCounted() {
        assertThrows(InvalidOtpException.class, () -> service.verifyOtpAndResetPassword(request("000000")));

        assertEquals(1, resetTokens.get(1L).getFailedAttempts());
        assertFalse(resetTokens.get(1L).isUsed());
        assertEquals(ORIGINAL_PASSWORD_HASH, user.getPassword().getHashedValue());
    }

    /** 6 digits are guessable: after 5 wrong codes the token is burned, even for the right code. */
    @Test
    void verifyOtpAndResetPassword_fifthWrongOtpBurnsTheToken() {
        for (int i = 0; i < 4; i++) {
            assertThrows(InvalidOtpException.class, () -> service.verifyOtpAndResetPassword(request("000000")));
        }

        PasswordResetTokenException burned = assertThrows(PasswordResetTokenException.class,
                () -> service.verifyOtpAndResetPassword(request("000000")));
        assertEquals(PasswordResetService.TOO_MANY_ATTEMPTS, burned.getMessage());
        assertEquals("TOKEN_BURNED_AFTER_WRONG_OTP", audit.last().details());
        assertEquals(5, audit.events.size());

        PasswordResetTokenException rightCodeTooLate = assertThrows(PasswordResetTokenException.class,
                () -> service.verifyOtpAndResetPassword(request(OTP)));
        assertEquals(PasswordResetService.INVALID_RESET_CODE, rightCodeTooLate.getMessage());
        assertEquals(ORIGINAL_PASSWORD_HASH, user.getPassword().getHashedValue());
    }

    /**
     * An SMS code reached the phone, so its owner is proven: the number becomes
     * verified and a PENDING account (possibly registered by someone else) is theirs.
     */
    @Test
    void verifyOtpAndResetPassword_bySmsVerifiesThePhone() {
        resetTokens.save(token(2L, "sms-token", VerificationChannel.PHONE, "0912345678", 0L));
        assertEquals(UserStatus.PENDING, user.getUserStatus());

        service.verifyOtpAndResetPassword(new PasswordResetCommand("sms-token", OTP, "N3wP@ssw0rd!"));

        assertTrue(user.isPhoneVerified());
        assertEquals(UserStatus.ACTIVE, user.getUserStatus());
        assertEquals(1, sms.sent.size(), "the owner is told by SMS");
        assertTrue(email.sent.isEmpty());
        assertTrue(audit.types().contains(AuditEventType.PHONE_VERIFIED));
    }

    @Test
    void verifyOtpAndResetPassword_byEmailDoesNotVerifyThePhone() {
        service.verifyOtpAndResetPassword(request(OTP));

        assertFalse(user.isPhoneVerified());
    }

    @Test
    void verifyOtpAndResetPassword_unknownTokenIsRejected() {
        PasswordResetCommand unknown = new PasswordResetCommand("forged", OTP, "N3wP@ssw0rd!");

        assertThrows(PasswordResetTokenException.class, () -> service.verifyOtpAndResetPassword(unknown));
    }

    @Test
    void verifyOtpAndResetPassword_tokenCannotBeUsedTwice() {
        service.verifyOtpAndResetPassword(request(OTP));

        assertThrows(PasswordResetTokenException.class, () -> service.verifyOtpAndResetPassword(request(OTP)));
    }

    @Test
    void reset_unknownToken_carriesReasonTokenInvalid() {
        PasswordResetCommand unknown = new PasswordResetCommand("forged", OTP, "N3wP@ssw0rd!");

        PasswordResetTokenException exception = assertThrows(PasswordResetTokenException.class,
                () -> service.verifyOtpAndResetPassword(unknown));

        assertEquals(PasswordResetTokenException.Reason.TOKEN_INVALID, exception.getReason());
        assertEquals(PasswordResetService.INVALID_RESET_CODE, exception.getMessage());
    }

    @Test
    void reset_usedToken_carriesReasonTokenInvalid() {
        service.verifyOtpAndResetPassword(request(OTP));

        PasswordResetTokenException exception = assertThrows(PasswordResetTokenException.class,
                () -> service.verifyOtpAndResetPassword(request(OTP)));

        assertEquals(PasswordResetTokenException.Reason.TOKEN_INVALID, exception.getReason());
        assertEquals(PasswordResetService.INVALID_RESET_CODE, exception.getMessage());
    }

    @Test
    void reset_burnedToken_carriesReasonAttemptsExceeded() {
        for (int i = 0; i < 4; i++) {
            assertThrows(InvalidOtpException.class, () -> service.verifyOtpAndResetPassword(request("000000")));
        }

        PasswordResetTokenException burned = assertThrows(PasswordResetTokenException.class,
                () -> service.verifyOtpAndResetPassword(request("000000")));

        assertEquals(PasswordResetTokenException.Reason.ATTEMPTS_EXCEEDED, burned.getReason());
        assertEquals(PasswordResetService.TOO_MANY_ATTEMPTS, burned.getMessage());
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"LOCK", "TERMINATED"})
    void reset_lockedAccount_throwsAccountDisabled(UserStatus status) {
        user.setUserStatus(status);

        AccountDisabledException exception = assertThrows(AccountDisabledException.class,
                () -> service.verifyOtpAndResetPassword(request(OTP)));

        assertInstanceOf(AuthenticationFailedException.class, exception);
        assertEquals("Account is locked or disabled.", exception.getMessage());
    }
}
