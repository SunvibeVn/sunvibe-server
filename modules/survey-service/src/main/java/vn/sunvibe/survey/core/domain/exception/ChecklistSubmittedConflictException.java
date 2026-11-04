package vn.sunvibe.survey.core.domain.exception;

import java.io.Serial;

/**
 * A second SUBMITTED revision of one lead and intent cycle reached the database (survey-checklist submission spec I-1,
 * A-4, plan PD-8): two submits raced past their checks. The caller answers a replay when the winner is the same user,
 * else a concurrent modification.
 */
public class ChecklistSubmittedConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ChecklistSubmittedConflictException(Throwable cause) {
        super("A submitted revision of this cycle exists.", cause);
    }
}
