package vn.sunvibe.property.core.application.usecase.intake;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * One line of the owner's list (property-create intake spec 7.3): before the code only id, status and expiry show, and
 * {@code details} and {@code checkOpen} belong to a CONFIRMED item alone.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OwnerConfirmationItem(String requestId, @Schema(allowableValues = {"PENDING", "CONFIRMED"}) String status,
                                    Instant expiresAt,
                                    @Schema(description = "Only for a CONFIRMED item") OwnerRequestSnapshot details,
                                    @Schema(description = "Only for a CONFIRMED item") Boolean checkOpen) {

    private static final String PENDING = "PENDING";
    private static final String CONFIRMED = "CONFIRMED";

    public static OwnerConfirmationItem pending(String requestId, Instant expiresAt) {
        return new OwnerConfirmationItem(requestId, PENDING, expiresAt, null, null);
    }

    public static OwnerConfirmationItem confirmed(String requestId, Instant expiresAt, OwnerRequestSnapshot details,
                                                  boolean checkOpen) {
        return new OwnerConfirmationItem(requestId, CONFIRMED, expiresAt, details, checkOpen);
    }
}
