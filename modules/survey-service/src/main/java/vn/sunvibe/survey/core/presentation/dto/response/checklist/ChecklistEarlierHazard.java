package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * An important or unclassified hazard of an earlier checklist of the cycle, as recorded (survey-checklist spec 7.2).
 */
@Schema(description = "An important or unclassified hazard of an earlier checklist of the cycle, as recorded")
public record ChecklistEarlierHazard(
        String checklistId,
        int checklistNo,
        ChecklistHazard hazard,
        @Schema(description = "Empty: the hazard warns EARLIER_HAZARD_OPEN")
        List<ChecklistAddressedBy> addressedBy) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistEarlierHazard[" + checklistId + "]";
    }
}
