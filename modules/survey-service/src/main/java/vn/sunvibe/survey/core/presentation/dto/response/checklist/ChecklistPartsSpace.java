package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** G2 values of one space, as read (survey-checklist spec 4.6). */
@Schema(description = "G2 values of one space, as read")
public record ChecklistPartsSpace(
        String spaceId,
        ChecklistTextField floor,
        ChecklistTextField privateAreas,
        ChecklistActualAccessField actualAccess) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistPartsSpace[" + spaceId + "]";
    }
}
