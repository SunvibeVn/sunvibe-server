package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.RentalScope;

import java.util.List;

/** G1 identification, as sent (survey-checklist spec 7.1). */
@Schema(description = "G1 identification, as sent")
public record ChecklistIdentificationInput(
        @JsonProperty(required = true)
        ChecklistLocationFieldInput observedAddress,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "1..500 characters")
        String differenceNote,
        @JsonProperty(required = true)
        ChecklistPropertyTypeFieldInput propertyType,
        @JsonProperty(required = true)
        ChecklistFitFieldInput fit,
        @JsonProperty(required = true)
        @Schema(nullable = true)
        RentalScope rentalScope,
        @JsonProperty(required = true)
        @Schema(description = "At most 50; WHOLE has exactly one")
        List<ChecklistObservedSpaceInput> spaces,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "1..1000 characters")
        String otherPartsNote,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "1..2000 characters")
        String generalNote) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistIdentificationInput[hidden]";
    }
}
