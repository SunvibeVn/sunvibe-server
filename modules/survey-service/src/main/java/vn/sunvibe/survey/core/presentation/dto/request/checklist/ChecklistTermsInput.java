package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** G8 terms, as sent (survey-checklist spec 7.1). */
@Schema(description = "G8 terms, as sent")
public record ChecklistTermsInput(
        @JsonProperty(required = true)
        ChecklistTermsCommonInput common,
        @JsonProperty(required = true)
        List<ChecklistTermsSpaceInput> spaces) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistTermsInput[hidden]";
    }
}
