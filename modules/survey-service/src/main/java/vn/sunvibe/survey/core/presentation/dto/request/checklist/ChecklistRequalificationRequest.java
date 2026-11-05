package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.RequalificationKind;

import java.time.LocalDate;

/**
 * DTO - ChecklistRequalificationRequest (Record)
 * <p>
 * Withdraw a PASSED revision from use (property-create requalification spec 7, A-5, A-6). Every key must be present;
 * the values are checked by the use case.
 */
@Schema(description = "Withdraw a PASSED survey checklist revision from use")
public record ChecklistRequalificationRequest(
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "The checklist version the caller read")
        Long expectedVersion,
        @JsonProperty(required = true)
        @Schema(nullable = true)
        RequalificationKind reasonKind,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "What is wrong: 1..2000 characters, no phone number")
        String reason,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "Who or what reported it: 1..500 characters, no phone number")
        String eventSource,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "What it affects: 1..500 characters, no phone number")
        String scope,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "Vietnam date the source confirmed it, from the day of the decision to "
                + "today")
        LocalDate basisDate) {

    /** Staff text: never print it. */
    @Override
    public String toString() {
        return "ChecklistRequalificationRequest[" + reasonKind + "]";
    }
}
