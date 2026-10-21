package vn.sunvibe.auth.core.presentation.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.shared.dto.response.error.BaseErrorModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The 404 of the role admin API has a fixed message: a lookup by username or email puts the phone number or the
 * address in the exception message, which must never reach the caller (.claude/rules/security.md).
 */
class AuthExceptionHandlerTest {

    private final AuthExceptionHandler handler = new AuthExceptionHandler();

    @Test
    void handleUserNotFound_messageWithPhoneNumber_answers404WithFixedMessage() {
        ResponseEntity<BaseErrorModel> response = handler.handleUserNotFound(
                new UserNotFoundException("User with username 0901234567 not found."));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().code());
        assertEquals("User not found.", response.getBody().message());
    }
}
