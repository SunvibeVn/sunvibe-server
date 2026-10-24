package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** Where an amenity or furnishing came from (survey-checklist spec 4.6). */
@Schema(description = "Where an amenity or furnishing came from")
public enum ChecklistAmenitySource {
    OBSERVED_ON_SITE,
    OWNER_STATED
}
