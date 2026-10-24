package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** G6 values of one space, as read (survey-checklist spec 4.6). */
@Schema(description = "G6 values of one space, as read")
public record ChecklistAmenitiesSpace(
        String spaceId,
        ChecklistFurnishingField furnishing,
        List<ChecklistAmenityItem> items) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistAmenitiesSpace[" + spaceId + "]";
    }
}
