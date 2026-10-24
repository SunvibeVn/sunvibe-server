package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** Where an area came from (survey-checklist spec 4.6). */
@Schema(description = "Where an area came from")
public enum ChecklistAreaSource {
    MEASURED,
    ESTIMATED,
    OWNER_STATED
}
