package vn.sunvibe.survey.core.application.exception;

import java.io.Serial;

/**
 * The draft is read-only (survey-checklist spec 4.4): the requirement changed, a newer visit exists or the lead is not
 * in SURVEY_SCHEDULED. The reason is on the next read.
 */
public class ChecklistReadOnlyException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ChecklistReadOnlyException() {
        super("This checklist is read-only now.");
    }
}
