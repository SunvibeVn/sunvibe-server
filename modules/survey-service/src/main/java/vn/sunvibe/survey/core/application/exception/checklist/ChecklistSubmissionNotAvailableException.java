package vn.sunvibe.survey.core.application.exception.checklist;

import java.io.Serial;

/** Gate S2 (survey-checklist submission spec 4.4, A-3): uploads or submission are off. */
public class ChecklistSubmissionNotAvailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ChecklistSubmissionNotAvailableException() {
        super("Submission is not available.");
    }
}
