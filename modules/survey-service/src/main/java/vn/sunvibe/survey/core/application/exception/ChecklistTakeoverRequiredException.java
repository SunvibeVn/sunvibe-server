package vn.sunvibe.survey.core.application.exception;

import java.io.Serial;

/** The assignee is not the holder and must take the draft over before saving (survey-checklist spec 4.5, BR-14). */
public class ChecklistTakeoverRequiredException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ChecklistTakeoverRequiredException() {
        super("You are the new assignee. Take over this draft before editing.");
    }
}
