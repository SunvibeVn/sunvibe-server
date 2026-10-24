package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** G3 values of one space, as read (survey-checklist spec 4.6). */
@Schema(description = "G3 values of one space, as read")
public record ChecklistDimensionsSpace(
        String spaceId,
        ChecklistAreaField area,
        ChecklistCountField bedrooms,
        ChecklistCountField privateBathrooms,
        ChecklistYesNoCountField sharedBathroomUse,
        @Schema(nullable = true)
        String layoutNote,
        ChecklistDimensionsFieldMeta fieldMeta) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistDimensionsSpace[" + spaceId + "]";
    }
}
