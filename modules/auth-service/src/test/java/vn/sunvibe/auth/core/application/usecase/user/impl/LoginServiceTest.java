package vn.sunvibe.auth.core.application.usecase.user.impl;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.util.ReflectionTestUtils;
import vn.sunvibe.auth.core.application.command.auth.LoginCommand;
import vn.sunvibe.auth.core.application.exception.auth.AuthenticationFailedException;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.shared.service.CookieService;

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
                (userId, fingerprint) -> fail("No refresh token expected"),
                null,
                null);
    }

    private LoginCommand command() {
        return new LoginCommand("0912345678", "P@ssw0rd!");
    }

    @Test
    void execute_lockedAccount_throwsAuthenticationFailedWithReason() {
        AuthenticationFailedException exception = assertThrows(AuthenticationFailedException.class,
                () -> serviceFailingWith(new LockedException("locked")).execute(command(), "fingerprint"));

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
        };
        CookieService cookieService = new CookieService();
        ReflectionTestUtils.setField(cookieService, "jwtCookieName", "cookie-jwt");
        ReflectionTestUtils.setField(cookieService, "refreshTokenCookieName", "cookie-jwt-rf");
        ReflectionTestUtils.setField(cookieService, "accessTokenExpirationMinutes", 60L);
        ReflectionTestUtils.setField(cookieService, "refreshTokenExpirationMs", 60_000L);
        LoginService service = new LoginService(
                authentication -> fail("Not used"),
                username -> "access-token",
                (userId, fingerprint) -> "refresh-token",
                repository,
                cookieService);

        AuthResult result = service.loginInternal(userWithoutEmail, "fingerprint");

        assertNull(result.loginResponse().userEmail());
        assertEquals("0912345678", result.loginResponse().username());
    }

    @Test
    void execute_disabledAccount_throwsAuthenticationFailedWithReason() {
        AuthenticationFailedException exception = assertThrows(AuthenticationFailedException.class,
                () -> serviceFailingWith(new DisabledException("disabled")).execute(command(), "fingerprint"));

        assertEquals("Account is locked or disabled.", exception.getMessage());
    }
}
