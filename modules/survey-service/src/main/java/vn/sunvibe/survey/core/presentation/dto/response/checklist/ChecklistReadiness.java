package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The gaps known so far, computed on every read (survey-checklist spec 4.7, survey-checklist submission spec 4.4).
 */
@Schema(description = "The gaps known so far, computed on every read")
public record ChecklistReadiness(
        boolean submissionAvailable,
        @Schema(nullable = true, allowableValues = {"EVIDENCE_NOT_AVAILABLE", "SUBMISSION_NOT_AVAILABLE",
                "APPOINTMENT_SCHEDULED", "MISSING_ITEMS", "PHOTOS_NOT_FINISHED", "PHOTO_LIMIT", "PHOTO_UNAVAILABLE"},
                description = "Null when submission is available, else the first gate that fails: "
                        + "EVIDENCE_NOT_AVAILABLE (photos are not available or uploads are off), "
                        + "SUBMISSION_NOT_AVAILABLE (the submission switch is off), APPOINTMENT_SCHEDULED, "
                        + "MISSING_ITEMS, PHOTOS_NOT_FINISHED, PHOTO_LIMIT, PHOTO_UNAVAILABLE")
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
