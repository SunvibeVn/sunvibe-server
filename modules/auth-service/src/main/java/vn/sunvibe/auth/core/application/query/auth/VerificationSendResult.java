package vn.sunvibe.auth.core.application.query.auth;

import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;

import java.time.Instant;

/**
 * A verification code was sent.
 *
 * @param channel           PHONE or EMAIL
 * @param maskedDestination where it went, partly hidden (e.g. 091****678, a***@example.com)
 * @param expiresAt         when the code stops working
 * @param resendAvailableAt earliest time a new code can be asked for
 */
public record VerificationSendResult(VerificationChannel channel, String maskedDestination, Instant expiresAt,
                                     Instant resendAvailableAt) {
}
