package vn.sunvibe.auth.core.presentation.dto.response.auth;

import java.time.Instant;

/**
 * The phone number was proven; present the token to {@code POST /api/users/command/register}.
 *
 * @param registrationToken single-use token bound to the verified phone number
 * @param expiresAt         when the token stops being accepted
 */
public record RegistrationTokenResponse(String registrationToken, Instant expiresAt) {
}
