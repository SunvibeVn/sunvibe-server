package vn.sunvibe.survey.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * DTO - RecordVisitRequest (Record)
 * <p>
 * Record that someone reached the property (survey-schedule US-3 AC-1, AC-1a). An own record is the current
 * surveyor's own visit, without {@code proxy}; any other record is a proxy record of a manager with {@code proxy}.
 */
@Schema(description = "Record a visit of a scheduled survey appointment")
public record RecordVisitRequest(
        @NotNull(message = "Expected lead version is required.")
        @PositiveOrZero(message = "Expected lead version must be 0 or more.")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        Long expectedLeadVersion,

        @NotNull(message = "Expected appointment version is required.")
        @PositiveOrZero(message = "Expected appointment version must be 0 or more.")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        Long expectedAppointmentVersion,

        @NotNull(message = "Arrival time is required.")
        @Schema(description = "Whole minute, not in the future, at most 60 minutes before the start in force then",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant arrivedAt,

        @NotNull(message = "Note is required.")
        @Size(max = 2000, message = "Note must be at most 2000 characters.")
        @Schema(maxLength = 2000, requiredMode = Schema.RequiredMode.REQUIRED)
        String note,

        @NotNull(message = "Performer is required.")
        @Size(max = 26, message = "Performer must be at most 26 characters.")
        @Schema(description = "Who reached the property; must have held the appointment at arrivedAt",
                maxLength = 26, requiredMode = Schema.RequiredMode.REQUIRED)
        String performedByUserId,

        @Valid
        @Schema(description = "Required for every record that is not the current surveyor's own visit")
        ProxyRequest proxy) {

    @Override
    public String toString() {
        return "RecordVisitRequest[hidden]";
    }
}
