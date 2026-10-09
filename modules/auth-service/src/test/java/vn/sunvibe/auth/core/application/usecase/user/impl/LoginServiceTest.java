package vn.sunvibe.auth.core.application.usecase.user.impl;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import vn.sunvibe.auth.core.application.command.auth.LoginCommand;
import vn.sunvibe.auth.core.application.exception.auth.AuthenticationFailedException;

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
    void execute_disabledAccount_throwsAuthenticationFailedWithReason() {
        AuthenticationFailedException exception = assertThrows(AuthenticationFailedException.class,
                () -> serviceFailingWith(new DisabledException("disabled")).execute(command(), "fingerprint"));

        assertEquals("Account is locked or disabled.", exception.getMessage());
    }
}
