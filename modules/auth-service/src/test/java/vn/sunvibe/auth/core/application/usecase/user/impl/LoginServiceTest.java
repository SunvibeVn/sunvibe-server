package vn.sunvibe.auth.core.application.usecase.user.impl;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.application.command.auth.LoginCommand;
import vn.sunvibe.auth.core.application.exception.auth.AuthenticationFailedException;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.usecase.auth.impl.AuthResultAssembler;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.testsupport.TestCookies;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A locked/disabled account must surface as {@link AuthenticationFailedException}
 * with a reason, not escape as a raw Spring Security exception.
 */
class LoginServiceTest {

    private LoginService serviceFailingWith(AuthenticationException failure) {
        return new LoginService(
                authentication -> {
                    throw failure;
                },
                username -> fail("No access token expected"),
                (userId, clientInfo) -> fail("No refresh token expected"),
                null,
                null);
    }

    private LoginCommand command() {
        return new LoginCommand("0912345678", "P@ssw0rd!");
    }

    @Test
    void execute_lockedAccount_throwsAuthenticationFailedWithReason() {
        AuthenticationFailedException exception = assertThrows(AuthenticationFailedException.class,
                () -> serviceFailingWith(new LockedException("locked")).execute(command(), ClientInfo.unknown()));

        assertEquals("Account is locked or disabled.", exception.getMessage());
    }

    @Test
    void loginInternal_userWithoutEmail_returnsNullUserEmail() {
        AppUser userWithoutEmail = AppUser.create(
                UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"),
                "Nguyen Van A",
                Username.create("0912345678"),
                null,
                Password.create("hashed-password"),
                ProfileImage.create(null),
                null,
                new HashSet<>(Set.of("TENANT")),
                Instant.now());
        UserDomainRepository repository = new UserDomainRepository() {
            @Override
            public boolean existsByUsername(String userName) { throw new UnsupportedOperationException(); }
            @Override
            public boolean existsByEmail(String email) { throw new UnsupportedOperationException(); }
            @Override
            public boolean existsByIdCardNumber(IdCardNumber idCardNumber) { throw new UnsupportedOperationException(); }
            @Override
            public AppUser save(AppUser user) { return user; }
            @Override
            public AppUser findByUsername(String username) { throw new UnsupportedOperationException(); }
            @Override
            public AppUser findByEmail(String email) { throw new UnsupportedOperationException(); }
            @Override
            public java.util.Optional<AppUser> findById(UserId userId) { throw new UnsupportedOperationException(); }
        };
        LoginService service = new LoginService(
                authentication -> fail("Not used"),
                username -> "access-token",
                (userId, clientInfo) -> "refresh-token",
                repository,
                new AuthResultAssembler(TestCookies.cookieService()));

        AuthResult result = service.loginInternal(userWithoutEmail, ClientInfo.unknown());

        assertNull(result.loginResponse().userEmail());
        assertEquals("0912345678", result.loginResponse().username());
    }

    @Test
    void loginInternal_returnsAccessTokenInBodyAndRefreshTokenOnlyInScopedCookie() {
        AppUser user = AppUser.create(UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"), "Nguyen Van A",
                Username.create("0912345678"), null, Password.create("hashed-password"), ProfileImage.create(null),
                null, new HashSet<>(Set.of("TENANT")), Instant.now());
        ClientInfo device = new ClientInfo("Mozilla/5.0", "203.0.113.7");
        ClientInfo[] sessionDevice = new ClientInfo[1];
        LoginService service = new LoginService(
                authentication -> fail("Not used"),
                username -> "access-token",
                (userId, clientInfo) -> {
                    sessionDevice[0] = clientInfo;
                    return "refresh-secret";
                },
                new UserDomainRepository() {
                    @Override
                    public boolean existsByUsername(String userName) { throw new UnsupportedOperationException(); }
                    @Override
                    public boolean existsByEmail(String email) { throw new UnsupportedOperationException(); }
                    @Override
                    public boolean existsByIdCardNumber(IdCardNumber idCardNumber) { throw new UnsupportedOperationException(); }
                    @Override
                    public AppUser save(AppUser appUser) { return appUser; }
                    @Override
                    public AppUser findByUsername(String username) { throw new UnsupportedOperationException(); }
                    @Override
                    public AppUser findByEmail(String email) { throw new UnsupportedOperationException(); }
                    @Override
                    public java.util.Optional<AppUser> findById(UserId userId) { throw new UnsupportedOperationException(); }
                },
                new AuthResultAssembler(TestCookies.cookieService()));

        AuthResult result = service.loginInternal(user, device);

        assertEquals("access-token", result.loginResponse().accessToken());
        assertEquals(15 * 60, result.loginResponse().expiresIn());
        assertSame(device, sessionDevice[0]);
        String refreshCookie = result.refreshCookie().toString();
        assertTrue(refreshCookie.startsWith(TestCookies.REFRESH_COOKIE + "=refresh-secret"));
        assertTrue(refreshCookie.contains("Path=/api/auth"));
        assertTrue(refreshCookie.contains("HttpOnly"));
        assertTrue(refreshCookie.contains("Secure"));
    }

    @Test
    void execute_disabledAccount_throwsAuthenticationFailedWithReason() {
        AuthenticationFailedException exception = assertThrows(AuthenticationFailedException.class,
                () -> serviceFailingWith(new DisabledException("disabled")).execute(command(), ClientInfo.unknown()));

        assertEquals("Account is locked or disabled.", exception.getMessage());
    }
}
