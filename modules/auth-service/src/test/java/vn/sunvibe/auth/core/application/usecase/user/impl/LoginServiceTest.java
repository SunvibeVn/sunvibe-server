package vn.sunvibe.auth.core.application.usecase.user.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.application.command.auth.LoginCommand;
import vn.sunvibe.auth.core.application.exception.auth.AccountTemporarilyLockedException;
import vn.sunvibe.auth.core.application.exception.auth.AuthenticationFailedException;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.shared.LoginLockoutPolicy;
import vn.sunvibe.auth.core.application.usecase.auth.impl.AuthResultAssembler;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.testsupport.InMemoryUserRepository;
import vn.sunvibe.auth.core.testsupport.TestCookies;
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

    /** Accepts {@link #PASSWORD} for a known user, like DaoAuthenticationProvider. */
    private final AuthenticationManager passwordChecker = authentication -> {
        authenticateCalls++;
        if (!users.existsByUsername(authentication.getName()) || !PASSWORD.equals(authentication.getCredentials())) {
            throw new BadCredentialsException("Bad credentials");
        }
        return new UsernamePasswordAuthenticationToken(
                User.withUsername(authentication.getName()).password("hashed").build(), null, List.of());
    };

    @BeforeEach
    void setUp() {
        user = AppUser.create(UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"), "Nguyen Van A", Username.create(USERNAME),
                null, Password.create("hashed-password"), ProfileImage.create(null), null,
                new HashSet<>(Set.of("TENANT")), Instant.now());
        users = new InMemoryUserRepository().with(user);
        authenticateCalls = 0;
        refreshTokenIssued = null;
        audit = new RecordingAuditLog();
    }

    private LoginService service(AuthenticationManager authenticationManager) {
        return new LoginService(
                authenticationManager,
                username -> "access-token",
                (userId, clientInfo) -> refreshTokenIssued = "refresh-secret",
                users,
                new AuthResultAssembler(TestCookies.cookieService()),
                users,
                new LoginLockoutPolicy(5, Duration.ofMinutes(15), Duration.ofHours(24)),
                audit);
    }

    private LoginService service() {
        return service(passwordChecker);
    }

    private AuthResult login(String password) {
        return service().execute(new LoginCommand(USERNAME, password), ClientInfo.unknown());
    }

    private LoginService serviceFailingWith(AuthenticationException failure) {
        return service(authentication -> {
            throw failure;
        });
    }

    @Test
    void execute_lockedAccount_throwsAuthenticationFailedWithReason() {
        AuthenticationFailedException exception = assertThrows(AuthenticationFailedException.class,
                () -> serviceFailingWith(new LockedException("locked")).execute(new LoginCommand(USERNAME, PASSWORD), ClientInfo.unknown()));

        assertEquals("Account is locked or disabled.", exception.getMessage());
    }

    @Test
    void execute_disabledAccount_throwsAuthenticationFailedWithReason() {
        AuthenticationFailedException exception = assertThrows(AuthenticationFailedException.class,
                () -> serviceFailingWith(new DisabledException("disabled")).execute(new LoginCommand(USERNAME, PASSWORD), ClientInfo.unknown()));

        assertEquals("Account is locked or disabled.", exception.getMessage());
    }

    @Test
    void loginInternal_userWithoutEmail_returnsNullUserEmail() {
        AuthResult result = service().loginInternal(user, ClientInfo.unknown());

        assertNull(result.loginResponse().userEmail());
        assertEquals(USERNAME, result.loginResponse().username());
    }

    @Test
    void loginInternal_returnsAccessTokenInBodyAndRefreshTokenOnlyInScopedCookie() {
        AuthResult result = service().loginInternal(user, new ClientInfo("Mozilla/5.0", "203.0.113.7"));

        assertEquals("access-token", result.loginResponse().accessToken());
        assertEquals(15 * 60, result.loginResponse().expiresIn());
        String refreshCookie = result.refreshCookie().toString();
        assertTrue(refreshCookie.startsWith(TestCookies.REFRESH_COOKIE + "=refresh-secret"));
        assertTrue(refreshCookie.contains("Path=/api/auth"));
        assertTrue(refreshCookie.contains("HttpOnly"));
        assertTrue(refreshCookie.contains("Secure"));
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

        assertEquals("access-token", result.loginResponse().accessToken());
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
                () -> service().execute(new LoginCommand("0999999999", PASSWORD), ClientInfo.unknown()));

        assertEquals("Incorrect username or password.", exception.getMessage());
        assertEquals(0, user.getFailedLoginAttempts());
        assertEquals(AuditEventType.LOGIN_FAILED, audit.last().type());
        assertNull(audit.last().userId(), "no account to attach");
        assertEquals("0999999999", audit.last().subject());
    }
}
