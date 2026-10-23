package vn.sunvibe.survey.core.application.exception;

import java.io.Serial;

/** A held lead has no current visit, so its survey work cannot be resumed (survey-schedule BR-11, B-6). */
public class NoCurrentVisitException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public NoCurrentVisitException() {
        super("There is no visit to resume.");
    }
}
