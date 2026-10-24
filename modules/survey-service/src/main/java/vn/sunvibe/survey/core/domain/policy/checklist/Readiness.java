package vn.sunvibe.survey.core.domain.policy.checklist;

import java.util.List;

/**
 * The gaps known so far for a full-assessment submission, computed on every read (survey-checklist spec 4.7, US-2
 * AC-4): submission is never available in this slice; nothing here blocks a save.
 */
public record Readiness(boolean submissionAvailable, String submissionUnavailableReason, List<MissingItem> missing,
                        List<UnconfirmedTerm> unconfirmedTerms, List<ChecklistWarning> warnings) {

    /** spec 4.7: photos and submission open with survey-media (spec C-1). */
    public static final String EVIDENCE_NOT_AVAILABLE = "EVIDENCE_NOT_AVAILABLE";

    public int missingCount() {
        return missing.size();
    }
}
