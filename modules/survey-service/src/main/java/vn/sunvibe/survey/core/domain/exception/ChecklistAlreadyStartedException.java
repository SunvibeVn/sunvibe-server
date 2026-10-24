package vn.sunvibe.survey.core.domain.exception;

import java.io.Serial;

/**
 * A checklist insert hit one of the two unique indexes (survey-checklist spec 5.5, I-2): the visit already has a
 * draft, or a concurrent start took the same checklist number. Answered 409 CHECKLIST_ALREADY_STARTED.
 */
public class ChecklistAlreadyStartedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ChecklistAlreadyStartedException(Throwable cause) {
        super("A checklist for this visit already exists.", cause);
    }
}
