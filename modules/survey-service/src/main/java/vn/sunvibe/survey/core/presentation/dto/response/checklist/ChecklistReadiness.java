package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The gaps known so far, computed on every read; submission is not available in this slice (survey-checklist spec 4.7).
 */
@Schema(description = "The gaps known so far, computed on every read; submission is not available in this slice")
public record ChecklistReadiness(
        boolean submissionAvailable,
        String submissionUnavailableReason,
        List<ChecklistMissingItem> missing,
        List<ChecklistUnconfirmedTerm> unconfirmedTerms,
        List<ChecklistWarning> warnings) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistReadiness[" + missing.size() + "]";
    }
}
