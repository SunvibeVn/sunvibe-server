package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * DTO - ChecklistBasisRequest (Record)
 * <p>
 * What a takeover, an intake declaration or a correction relies on (survey-checklist spec 4.5): the Vietnam date of
 * the confirmation, from the visit's arrival date to today, and a short text without phone numbers. Every key must be
 * present (spec PD-2); the values are checked by the use case.
 */
@Schema(description = "The basis of a takeover, intake declaration or correction")
public record ChecklistBasisRequest(
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "Vietnam date, from the visit's arrival date to today")
        LocalDate confirmedOn,

        @JsonProperty(required = true)
        @Schema(nullable = true, description = "1..500 characters, no phone number")
        String text) {

    /** Staff text: never print it. */
    @Override
    public String toString() {
        return "ChecklistBasisRequest[" + confirmedOn + "]";
    }
}
