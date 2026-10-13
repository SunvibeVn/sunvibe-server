package vn.sunvibe.auth.core.application.query.auth;

import java.time.Instant;

/**
 * The phone number was proven: the single-use token registration must present.
 *
 * @param registrationToken the plain token; only its hash is stored
 * @param expiresAt         when the token stops being accepted
 */
public record RegistrationTokenResult(String registrationToken, Instant expiresAt) {
}
