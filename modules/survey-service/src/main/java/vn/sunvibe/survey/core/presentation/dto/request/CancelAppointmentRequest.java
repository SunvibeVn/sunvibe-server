package vn.sunvibe.survey.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import vn.sunvibe.survey.core.domain.model.appointment.CancelReason;

/**
 * DTO - CancelAppointmentRequest (Record)
 * <p>
 * Cancel a scheduled appointment (survey-schedule US-2 AC-2) with one of the reasons staff pick: OWNER_CANCELLED,
 * STAFF_CANCELLED, OWNER_ABSENT, NO_ACCESS or OTHER (which needs a description).
 */
@Schema(description = "Cancel a scheduled survey appointment")
public record CancelAppointmentRequest(
        @NotNull(message = "Expected lead version is required.")
        @PositiveOrZero(message = "Expected lead version must be 0 or more.")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        Long expectedLeadVersion,

        @NotNull(message = "Expected appointment version is required.")
        @PositiveOrZero(message = "Expected appointment version must be 0 or more.")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        Long expectedAppointmentVersion,

        @NotNull(message = "Cancel reason is required.")
        @Schema(description = "One of OWNER_CANCELLED, STAFF_CANCELLED, OWNER_ABSENT, NO_ACCESS, OTHER; the system "
                + "reasons are refused", requiredMode = Schema.RequiredMode.REQUIRED)
        CancelReason cancelReason,

        @Size(max = 500, message = "Description must be at most 500 characters.")
        @Schema(description = "Required for OTHER", maxLength = 500)
        String description) {

    @Override
    public String toString() {
        return "CancelAppointmentRequest[" + cancelReason + "]";
    }
}
