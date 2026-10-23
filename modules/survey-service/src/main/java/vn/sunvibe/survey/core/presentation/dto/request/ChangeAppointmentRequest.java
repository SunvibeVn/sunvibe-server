package vn.sunvibe.survey.core.presentation.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Optional;

/**
 * DTO - ChangeAppointmentRequest (Record)
 * <p>
 * Change a scheduled appointment (survey-schedule US-2 AC-1, spec A-4): the full new value of every editable field and
 * a reason. Every key must be present; access note and on-site contact may be null to clear them (a missing key is
 * 400 on that key). Changing start, duration or place removes the owner's confirmation.
 */
@Schema(description = "Change a scheduled survey appointment; every key is required, optional values may be null")
public record ChangeAppointmentRequest(
        @NotNull(message = "Expected lead version is required.")
        @PositiveOrZero(message = "Expected lead version must be 0 or more.")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        Long expectedLeadVersion,

        @NotNull(message = "Expected appointment version is required.")
        @PositiveOrZero(message = "Expected appointment version must be 0 or more.")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        Long expectedAppointmentVersion,

        @NotNull(message = "Reason is required.")
        @Size(max = 500, message = "Reason must be at most 500 characters.")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 500)
        String reason,

        @NotNull(message = "Start is required.")
        @Schema(description = "Whole minute; a new start lies from the current minute to 90 days ahead",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant startAt,

        @NotNull(message = "Duration is required.")
        @Schema(description = "15..480 in steps of 15", minimum = "15", maximum = "480",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Integer durationMinutes,

        @NotNull(message = "Meeting place is required.")
        @Valid
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        MeetingLocationRequest meetingLocation,

        @JsonProperty(required = true)
        @Schema(description = "Null to clear; no door codes, passwords or phone numbers", maxLength = 1000,
                nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        Optional<String> accessNote,

        @JsonProperty(required = true)
        @Schema(description = "Null to clear", maxLength = 100, nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED)
        Optional<String> onSiteContactName,

        @JsonProperty(required = true)
        @Schema(description = "Null to clear", maxLength = 100, nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED)
        Optional<String> onSiteContactRole) {

    @Override
    public String toString() {
        return "ChangeAppointmentRequest[hidden]";
    }
}
