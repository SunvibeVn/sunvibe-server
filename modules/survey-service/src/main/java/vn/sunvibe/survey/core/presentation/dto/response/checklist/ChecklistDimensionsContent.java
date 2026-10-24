package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** G3 dimensions, as read; one entry per G1 space in G1 order (survey-checklist spec 7.1). */
@Schema(description = "G3 dimensions, as read; one entry per G1 space in G1 order")
public record ChecklistDimensionsContent(
        List<ChecklistDimensionsSpace> spaces) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistDimensionsContent[hidden]";
    }
}
