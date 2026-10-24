package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** G8 terms, as read; surveyed or offered, not agreed lease terms (survey-checklist spec 7.1). */
@Schema(description = "G8 terms, as read; surveyed or offered, not agreed lease terms")
public record ChecklistTermsContent(
        ChecklistTermsCommon common,
        List<ChecklistTermsSpace> spaces) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistTermsContent[hidden]";
    }
}
