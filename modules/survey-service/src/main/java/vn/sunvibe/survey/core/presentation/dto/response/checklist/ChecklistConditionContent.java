package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** G4 condition, as read; shared wet areas only in PARTS scope (survey-checklist spec 7.1). */
@Schema(description = "G4 condition, as read; shared wet areas only in PARTS scope")
public record ChecklistConditionContent(
        @Schema(nullable = true, description = "Null unless the property is in separate parts")
        ChecklistAreaCheckField sharedKitchen,
        @Schema(nullable = true, description = "Null unless the property is in separate parts")
        ChecklistAreaCheckField sharedBathroom,
        List<ChecklistConditionSpace> spaces) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistConditionContent[hidden]";
    }
}
