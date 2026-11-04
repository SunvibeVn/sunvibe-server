package vn.sunvibe.survey.core.application.exception;

import java.io.Serial;

/**
 * The caller may not decide this revision (survey-checklist submission spec 4.2, 7.3): an excluded user, or an account
 * that cannot sign in (plan PD-6). 403 CHECKLIST_REVIEW_NOT_ALLOWED on {@code reviewer}.
 */
public class ChecklistReviewNotAllowedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ChecklistReviewNotAllowedException() {
        super("The caller may not decide this revision.");
    }
}
