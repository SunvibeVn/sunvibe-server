package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** Where a rental term or availability date came from (survey-checklist spec 4.6). */
@Schema(description = "Where a rental term or availability date came from")
public enum ChecklistTermsSource {
    OBSERVED_ON_SITE,
    OWNER_STATED,
    OWNER_FORM
}
