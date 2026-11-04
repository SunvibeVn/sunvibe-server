package vn.sunvibe.survey.core.application.shared;

/**
 * The checklist switch the commands read at each request (survey-checklist submission spec A-3): the owner opens review
 * separately from the upload go-live. A port, so a test varies it at run time.
 */
public interface ChecklistSwitches {

    /** Submission is on; with uploads off it still answers 409 CHECKLIST_SUBMISSION_NOT_AVAILABLE. */
    boolean submissionEnabled();
}
