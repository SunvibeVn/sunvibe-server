package vn.sunvibe.auth.core.application.query.auth;

import java.time.Duration;
import java.time.Instant;

/**
 * Answer to a password-reset request. It has the same shape whether or not a
 * code was actually sent, so it cannot reveal which contacts are registered.
 *
 * @param message      generic text for the client
 * @param resetToken   SMS flow only: to send back with the SMS code. Null for the
 *                     e-mail flow, where the token travels in the e-mail
 * @param expiresAt    when the code stops working
 * @param codeLifetime how long a reset code is valid
 */
public record PasswordForgotResult(String message, String resetToken, Instant expiresAt, Duration codeLifetime) {
}
