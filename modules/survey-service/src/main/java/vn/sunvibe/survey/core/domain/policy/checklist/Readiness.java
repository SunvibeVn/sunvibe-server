package vn.sunvibe.survey.core.domain.policy.checklist;

import java.util.List;

/**
 * The gaps known so far for a full-assessment submission, computed on every read (survey-checklist spec 4.7, US-2
 * AC-4); nothing here blocks a save. {@code submissionAvailable} is gates S2..S7 of submission spec 4.4, the reason the
 * first of them that fails.
 */
public record Readiness(boolean submissionAvailable, String submissionUnavailableReason, List<MissingItem> missing,
                        List<UnconfirmedTerm> unconfirmedTerms, List<ChecklistWarning> warnings) {

    /** spec 4.7: photos are not available, or uploads are off (submission spec A-3). */
    public static final String EVIDENCE_NOT_AVAILABLE = "EVIDENCE_NOT_AVAILABLE";

    /** survey-checklist submission spec A-3: the submission switch is off. */
    public static final String SUBMISSION_NOT_AVAILABLE = "SUBMISSION_NOT_AVAILABLE";
    /** Gate S3 (submission spec 4.4): a SCHEDULED appointment on the lead. */
    public static final String APPOINTMENT_SCHEDULED = "APPOINTMENT_SCHEDULED";
    /** Gate S4: readiness items are missing. */
    public static final String MISSING_ITEMS = "MISSING_ITEMS";
    /** Gate S5: a selected photo is still pending. */
    public static final String PHOTOS_NOT_FINISHED = "PHOTOS_NOT_FINISHED";
    /** Gate S6: more selected photos than the cap. */
    public static final String PHOTO_LIMIT = "PHOTO_LIMIT";
    /** Gate S7: a selected photo is held or lost its content. */
    public static final String PHOTO_UNAVAILABLE = "PHOTO_UNAVAILABLE";

    public int missingCount() {
        return missing.size();
    }
}
