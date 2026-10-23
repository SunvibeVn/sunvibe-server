package vn.sunvibe.survey.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * DTO - ConfirmationRequest (Record)
 * <p>
 * Record ({@code confirmed=true}) or remove the owner's confirmation (survey-schedule US-1 AC-5); the same value again
 * changes nothing.
 */
@Schema(description = "Record or remove the owner's confirmation of a scheduled survey appointment")
public record ConfirmationRequest(
        @NotNull(message = "Expected lead version is required.")
        @PositiveOrZero(message = "Expected lead version must be 0 or more.")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        Long expectedLeadVersion,

        @NotNull(message = "Expected appointment version is required.")
        @PositiveOrZero(message = "Expected appointment version must be 0 or more.")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        Long expectedAppointmentVersion,

        @NotNull(message = "Confirmed is required.")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean confirmed,

        @Size(max = 500, message = "Note must be at most 500 characters.")
        @Schema(maxLength = 500)
        String note) {

    @Override
    public String toString() {
        return "ConfirmationRequest[" + confirmed + "]";
    }
}
