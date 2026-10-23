package vn.sunvibe.survey.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * DTO - BookAppointmentRequest (Record)
 * <p>
 * Book a survey appointment for an owner lead (survey-schedule spec 7.1), guarded by the lead version the caller last
 * saw. Missing keys and outer lengths are bean violations; the cleaned values (whole minutes, durations, phone numbers
 * in free text) are checked by the command. Messages are English and never repeat the value.
 */
@Schema(description = "Book a survey appointment for an owner lead")
public record BookAppointmentRequest(
        @NotNull(message = "Expected lead version is required.")
        @PositiveOrZero(message = "Expected lead version must be 0 or more.")
        @Schema(description = "The lead version the caller last saw", minimum = "0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long expectedLeadVersion,

        @NotNull(message = "Start is required.")
        @Schema(description = "Whole minute, from the current minute to 90 days ahead",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant startAt,

        @Schema(description = "15..480 in steps of 15; 60 when left out", minimum = "15", maximum = "480")
        Integer durationMinutes,

        @Valid
        @Schema(description = "Left out: the lead's own place")
        MeetingLocationRequest meetingLocation,

        @Size(max = 500, message = "Location reason must be at most 500 characters.")
        @Schema(description = "Required when the meeting place differs from the lead's place", maxLength = 500)
        String locationReason,

        @Size(max = 1000, message = "Access note must be at most 1000 characters.")
        @Schema(description = "No door codes, passwords or phone numbers", maxLength = 1000)
        String accessNote,

        @Size(max = 100, message = "On-site contact name must be at most 100 characters.")
        @Schema(maxLength = 100)
        String onSiteContactName,

        @Size(max = 100, message = "On-site contact role must be at most 100 characters.")
        @Schema(maxLength = 100)
        String onSiteContactRole,

        @Schema(description = "Staff recorded that the owner agreed; false when left out")
        Boolean ownerConfirmed,

        @Size(max = 500, message = "Confirmation note must be at most 500 characters.")
        @Schema(description = "Only with ownerConfirmed", maxLength = 500)
        String confirmationNote,

        @Size(max = 500, message = "Re-survey reason must be at most 500 characters.")
        @Schema(description = "Required when the lead already has a visit in its current requirement cycle",
                maxLength = 500)
        String resurveyReason) {

    /** Notes, places and contacts are staff text: never print them. */
    @Override
    public String toString() {
        return "BookAppointmentRequest[hidden]";
    }
}
