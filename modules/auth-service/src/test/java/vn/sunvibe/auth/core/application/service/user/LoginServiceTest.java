package vn.sunvibe.auth.core.application.service.user;

import vn.sunvibe.auth.core.testsupport.AuthTestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.application.command.auth.LoginCommand;
import vn.sunvibe.auth.core.application.exception.auth.AccountTemporarilyLockedException;
import vn.sunvibe.auth.core.application.exception.auth.AuthenticationFailedException;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.exception.auth.AuthChannelDisabledException;
import vn.sunvibe.auth.core.application.shared.AccountAccessPolicy;
import vn.sunvibe.auth.core.application.shared.LoginLockoutPolicy;
import vn.sunvibe.auth.core.application.shared.PhoneChannelPolicy;
import vn.sunvibe.auth.core.application.usecase.user.LoginIdentifier;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;
import vn.sunvibe.auth.core.application.shared.VerifiedCredentials;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.testsupport.InMemoryUserRepository;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LoginServiceTest {

    private static final String USERNAME = "0912345678";
    private static final String PASSWORD = "Str0ng@Pass";

    private AppUser user;
    private InMemoryUserRepository users;
    private int authenticateCalls;
    private String refreshTokenIssued;
    private RecordingAuditLog audit;
    private PhoneChannelPolicy phoneChannel;
    private LoginAccountFinder accounts;
    /** What happens to the row between the password check and the lock. */
    private Runnable afterPasswordCheck = () -> { };

    /** Accepts {@link #PASSWORD} for a known user, like DaoAuthenticationProvider. */
    private final AuthenticationManager passwordChecker = authentication -> {
        authenticateCalls++;
        boolean known = LoginIdentifier.parse(authentication.getName()).flatMap(accounts::find).isPresent();
        if (!known || !PASSWORD.equals(authentication.getCredentials())) {
            throw new BadCredentialsException("Bad credentials");
        }
        long checkedVersion = user.getCredentialVersion();
        afterPasswordCheck.run();
        return new UsernamePasswordAuthenticationToken(new Checked(user.getId().getValue(), checkedVersion), null, List.of());
    };

    /** The principal of a checked login: the account and the credential version the hash belonged to. */
    private record Checked(String userId, long credentialVersion) implements VerifiedCredentials {
    }

    @BeforeEach
    void setUp() {
        user = AuthTestUsers.withPhone(UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"), "Nguyen Van A", USERNAME,
                null, Password.create("hashed-password"), ProfileImage.create(null), null,
                new HashSet<>(Set.of("TENANT")), Instant.now());
        users = new InMemoryUserRepository().with(user);
        authenticateCalls = 0;
        refreshTokenIssued = null;
        audit = new RecordingAuditLog();
        channel(PhoneChannelPolicy.Mode.ON, null);
    }

    private void channel(PhoneChannelPolicy.Mode mode, Instant legacyLoginUntil) {
        phoneChannel = new PhoneChannelPolicy(mode, legacyLoginUntil, audit);
        accounts = new LoginAccountFinder(users, phoneChannel);
    }

    private LoginService service(AuthenticationManager authenticationManager) {
        return new LoginService(
                authenticationManager,
                appUser -> "access-token",
                (userId, clientInfo) -> refreshTokenIssued = "refresh-secret",
                users,
                users,
                new LoginLockoutPolicy(5, Duration.ofMinutes(15), Duration.ofHours(24)),
                accounts,
                phoneChannel,
                audit,
                new AccountAccessPolicy(phoneChannel));
    }

    private LoginService service() {
        return service(passwordChecker);
    }

    private AuthResult login(String password) {
        return service().execute(LoginCommand.byPhone(USERNAME, password), ClientInfo.unknown());
    }

    private LoginService serviceFailingWith(AuthenticationException failure) {
        return service(authentication -> {
            throw failure;
        });
    }

    @Test
    void execute_lockedAccount_throwsAuthenticationFailedWithReason() {
        AuthenticationFailedException exception = assertThrows(AuthenticationFailedException.class,
                () -> serviceFailingWith(new LockedException("locked")).execute(LoginCommand.byPhone(USERNAME, PASSWORD), ClientInfo.unknown()));

        assertEquals("Account is locked or disabled.", exception.getMessage());
    }

    @Test
    void execute_disabledAccount_throwsAuthenticationFailedWithReason() {
        AuthenticationFailedException exception = assertThrows(AuthenticationFailedException.class,
                () -> serviceFailingWith(new DisabledException("disabled")).execute(LoginCommand.byPhone(USERNAME, PASSWORD), ClientInfo.unknown()));

        assertEquals("Account is locked or disabled.", exception.getMessage());
    }

    /** auth-email-access S-32: closed for anyone, before any lookup or password check. */
    @Test
    void login_phoneOff_403_noLookup() {
        channel(PhoneChannelPolicy.Mode.OFF, null);

        assertThrows(AuthChannelDisabledException.class, () -> login(PASSWORD));

        assertEquals(0, authenticateCalls);
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(refreshTokenIssued);
    }

    /** auth-email-access 4.5: after the end date legacy-login behaves as off, with no restart. */
    @Test
    void legacy_afterEndDate_403() {
        channel(PhoneChannelPolicy.Mode.LEGACY_LOGIN, Instant.now().minus(Duration.ofMinutes(1)));

        assertThrows(AuthChannelDisabledException.class, () -> login(PASSWORD));

        assertEquals(0, authenticateCalls);
    }

    @Test
    void legacy_beforeEndDate_signsIn() {
        channel(PhoneChannelPolicy.Mode.LEGACY_LOGIN, Instant.now().plus(Duration.ofDays(1)));

        assertEquals("refresh-secret", login(PASSWORD).refreshToken());
    }

    /** auth-email-access 5.5: with phone on, a value the policy refuses answers 400 on username, as the old regex did. */
    @Test
    void on_refusedUsername_throwsValidation() {
        AuthValidationException refused = assertThrows(AuthValidationException.class,
                () -> service().execute(LoginCommand.byPhone("0123456789", PASSWORD), ClientInfo.unknown()));

        assertEquals(AuthFieldName.USER_NAME, refused.getField());
        assertEquals(0, authenticateCalls);
    }

    private void verifiedEmail(String address) {
        user.setEmail(Email.create(address));
        user.setEmailKey(Email.create(address).getValue());
        user.setEmailVerifiedAt(Instant.now());
    }

    private AuthResult loginByEmail(String address, String password) {
        return service().execute(LoginCommand.byEmail(address, password), ClientInfo.unknown());
    }

    /** auth-email-access S-14: the email path does not depend on the phone channel. */
    @Test
    void email_phoneOff_signsIn() {
        verifiedEmail("a@example.com");
        channel(PhoneChannelPolicy.Mode.OFF, null);

        assertEquals("refresh-secret", loginByEmail("  A@Example.COM ", PASSWORD).refreshToken());
    }

    @Test
    void login_unverifiedEmail_touchesNoCounter() {
        verifiedEmail("a@example.com");
        user.setEmailVerifiedAt(null);

        assertThrows(AuthenticationFailedException.class, () -> loginByEmail("a@example.com", "wrong"));

        assertEquals(1, authenticateCalls, "the hash still runs");
        assertEquals(0, user.getFailedLoginAttempts());
    }

    @Test
    void login_keylessEmail_isNotFound() {
        verifiedEmail("a@example.com");
        user.setEmailKey(null);

        assertThrows(AuthenticationFailedException.class, () -> loginByEmail("a@example.com", PASSWORD));

        assertEquals(1, authenticateCalls);
        assertNull(refreshTokenIssued);
    }

    @Test
    void login_lockedStatus_refusedAfterTheHash_notCounted() {
        verifiedEmail("a@example.com");
        user.setUserStatus(UserStatus.LOCK);

        assertThrows(AuthenticationFailedException.class, () -> loginByEmail("a@example.com", PASSWORD));
        assertThrows(AuthenticationFailedException.class, () -> loginByEmail("a@example.com", "wrong"));

        assertEquals(2, authenticateCalls);
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(refreshTokenIssued);
    }

    /** D4-10. */
    @Test
    void login_pendingVerifiedEmail_becomesActive() {
        verifiedEmail("a@example.com");
        assertEquals(UserStatus.PENDING, user.getUserStatus());

        loginByEmail("a@example.com", PASSWORD);

        assertEquals(UserStatus.ACTIVE, user.getUserStatus());
        assertEquals(0, users.saveCount, "a guarded write, not the whole row");
    }

    /** Only an email sign-in proves the mailbox. */
    @Test
    void login_pendingByPhone_staysPending() {
        login(PASSWORD);

        assertEquals(UserStatus.PENDING, user.getUserStatus());
    }

    @Test
    void login_emailChangedAfterCheck_401() {
        verifiedEmail("a@example.com");
        afterPasswordCheck = () -> verifiedEmail("new@example.com");

        assertThrows(AuthenticationFailedException.class, () -> loginByEmail("a@example.com", PASSWORD));

        assertNull(refreshTokenIssued);
    }

    /** auth-email-access S-16: a change that commits after the check leaves no session. */
    @Test
    void login_versionChangedAfterPasswordCheck_401_noSession() {
        afterPasswordCheck = () -> user.changePassword("hash-of-the-reset");

        assertThrows(AuthenticationFailedException.class, () -> login(PASSWORD));

        assertNull(refreshTokenIssued);
        assertEquals(0, users.loginSuccessWrites);
    }

    @Test
    void login_statusLockedAfterCheck_401() {
        afterPasswordCheck = () -> user.setUserStatus(UserStatus.LOCK);

        assertThrows(AuthenticationFailedException.class, () -> login(PASSWORD));

        assertNull(refreshTokenIssued);
    }

    /** SV-001: the aggregate read before the check is never written back. */
    @Test
    void login_success_usesGuardedUpdate_neverSave() {
        user.setFailedLoginAttempts(3);

        login(PASSWORD);

        assertEquals(0, users.saveCount, "no write of the whole row");
        assertEquals(1, users.loginSuccessWrites);
        assertEquals(0, user.getFailedLoginAttempts());
        assertEquals(List.of("lock-user"), users.calls, "the row is locked before the session starts");
    }

    @Test
    void loginInternal_userWithoutEmail_returnsNullUserEmail() {
        AuthResult result = service().loginInternal(user, ClientInfo.unknown());

        assertNull(result.email());
        assertEquals(USERNAME, result.username());
    }

    @Test
    void loginInternal_returnsTheNewTokenPairAndAccountSummary() {
        AuthResult result = service().loginInternal(user, new ClientInfo("Mozilla/5.0", "203.0.113.7"));

        assertEquals("access-token", result.accessToken());
        assertEquals("refresh-secret", result.refreshToken());
        assertEquals(Set.of("TENANT"), result.roles());
        assertFalse(result.phoneVerified());
        assertNotNull(user.getLastLogin());
    }

    @Test
    void execute_wrongPassword_countsTheFailure() {
        AuthenticationFailedException exception = assertThrows(AuthenticationFailedException.class, () -> login("wrong"));

        assertEquals("Incorrect username or password.", exception.getMessage());
        assertEquals(1, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
    }

    @Test
    void execute_fifthWrongPasswordLocksTheAccount() {
        for (int i = 0; i < 4; i++) {
            assertThrows(AuthenticationFailedException.class, () -> login("wrong"));
        }

        AccountTemporarilyLockedException locked = assertThrows(AccountTemporarilyLockedException.class, () -> login("wrong"));

        assertEquals(Duration.ofMinutes(15), locked.getRetryAfter());
        assertTrue(user.isLoginTemporarilyLocked(Instant.now()));
        assertEquals(AuditEventType.ACCOUNT_TEMPORARILY_LOCKED, audit.last().type());
        assertEquals(5, audit.types().stream().filter(t -> t == AuditEventType.LOGIN_FAILED).count());
    }

    /** During the lock even the right password is refused, without being checked. */
    @Test
    void execute_lockedAccountIsRefusedBeforeThePasswordIsChecked() {
        user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(10)));

        AccountTemporarilyLockedException locked = assertThrows(AccountTemporarilyLockedException.class, () -> login(PASSWORD));

        assertEquals(0, authenticateCalls);
        assertNull(refreshTokenIssued);
        assertEquals(List.of(AuditEventType.LOGIN_BLOCKED), audit.types());
        assertTrue(locked.getRetryAfter().compareTo(Duration.ofMinutes(9)) > 0);
        assertTrue(locked.getMessage().contains("10 minute"));
    }

    @Test
    void execute_expiredLockAllowsLoginAndResetsTheCounter() {
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(Instant.now().minusSeconds(1));

        AuthResult result = login(PASSWORD);

        assertEquals("access-token", result.accessToken());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
        assertNotNull(user.getLastLogin());
        assertEquals(List.of(AuditEventType.LOGIN_SUCCEEDED), audit.types());
        assertEquals(user.getId().getValue(), audit.last().userId());
    }

    @Test
    void execute_successResetsEarlierFailures() {
        assertThrows(AuthenticationFailedException.class, () -> login("wrong"));
        assertThrows(AuthenticationFailedException.class, () -> login("wrong"));

        login(PASSWORD);

        assertEquals(0, user.getFailedLoginAttempts());
    }

    @Test
    void execute_unknownUsernameIsNotCountedAndLooksLikeAWrongPassword() {
        AuthenticationFailedException exception = assertThrows(AuthenticationFailedException.class,
                () -> service().execute(LoginCommand.byPhone("0999999999", PASSWORD), ClientInfo.unknown()));

        assertEquals("Incorrect username or password.", exception.getMessage());
        assertEquals(0, user.getFailedLoginAttempts());
        assertEquals(AuditEventType.LOGIN_FAILED, audit.last().type());
        assertNull(audit.last().userId(), "no account to attach");
        assertEquals("099****999", audit.last().subject());
    }
}
