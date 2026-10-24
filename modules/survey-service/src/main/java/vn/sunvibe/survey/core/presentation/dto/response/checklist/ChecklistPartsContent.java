package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** G2 parts, as read; one entry per G1 space in G1 order (survey-checklist spec 7.1). */
@Schema(description = "G2 parts, as read; one entry per G1 space in G1 order")
public record ChecklistPartsContent(
        ChecklistTextField accessRoute,
        ChecklistTextField sharedAreas,
        ChecklistCountField totalFloors,
        List<ChecklistPartsSpace> spaces) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistPartsContent[hidden]";
    }
}
