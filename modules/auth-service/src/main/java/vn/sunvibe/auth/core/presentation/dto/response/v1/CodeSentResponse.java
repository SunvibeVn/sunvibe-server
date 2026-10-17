package vn.sunvibe.auth.core.presentation.dto.response.v1;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** A verification code was sent. */
@Schema(description = "A verification code was sent")
public record CodeSentResponse(
        @Schema(description = "PHONE or EMAIL", example = "PHONE")
        String channel,
        @Schema(description = "Partly hidden phone or e-mail the code went to", example = "091****678")
        String sentTo,
        @Schema(description = "When the code stops working")
        Instant expiresAt,
        @Schema(description = "Earliest time a new code can be asked for")
        Instant resendAvailableAt) {
}
