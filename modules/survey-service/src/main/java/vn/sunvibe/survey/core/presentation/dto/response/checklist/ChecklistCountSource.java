package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** Where a count came from (survey-checklist spec 4.6). */
@Schema(description = "Where a count came from")
public enum ChecklistCountSource {
    OBSERVED_ON_SITE,
    OWNER_STATED
}
