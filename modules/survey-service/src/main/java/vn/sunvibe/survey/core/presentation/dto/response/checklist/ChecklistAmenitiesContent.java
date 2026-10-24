package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** G6 amenities, as read (survey-checklist spec 7.1). */
@Schema(description = "G6 amenities, as read")
public record ChecklistAmenitiesContent(
        List<ChecklistAmenityItem> sharedItems,
        List<ChecklistAmenitiesSpace> spaces,
        List<ChecklistOtherItem> otherItems) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistAmenitiesContent[hidden]";
    }
}
