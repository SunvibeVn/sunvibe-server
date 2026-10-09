package vn.sunvibe.auth.core.presentation.dto.response.auth;

import java.time.Instant;

/**
 * @param sentTo            partly hidden phone/e-mail the code went to
 * @param expiresAt         when the code stops working
 * @param resendAvailableAt earliest time a new code can be asked for
 */
public record VerificationSendResponse(String channel, String sentTo, Instant expiresAt, Instant resendAvailableAt) {
}
