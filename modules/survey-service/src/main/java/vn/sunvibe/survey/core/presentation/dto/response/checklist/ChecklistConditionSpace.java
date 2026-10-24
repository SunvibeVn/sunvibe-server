package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** G4 values of one space, as read (survey-checklist spec 4.6). */
@Schema(description = "G4 values of one space, as read")
public record ChecklistConditionSpace(
        String spaceId,
        ChecklistOccupancyField occupancy,
        ChecklistDateTerm expectedVacantFrom,
        ChecklistDateTerm availableFrom,
        ChecklistAreaCheckField mainSpace,
        ChecklistAreaCheckField kitchen,
        ChecklistAreaCheckField bathroom) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistConditionSpace[" + spaceId + "]";
    }
}
