package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** G5 safety, as read (survey-checklist spec 7.1). */
@Schema(description = "G5 safety, as read")
public record ChecklistSafetyContent(
        ChecklistTextField egress,
        ChecklistObservationField accessRestrictions,
        ChecklistHazardListField hazards) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistSafetyContent[hidden]";
    }
}
