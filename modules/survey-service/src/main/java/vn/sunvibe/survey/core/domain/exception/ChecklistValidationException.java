package vn.sunvibe.survey.core.domain.exception;

import vn.sunvibe.survey.core.domain.model.checklist.Violation;

import java.io.Serial;
import java.util.List;

/**
 * A checklist body breaks template rules (survey-checklist spec 5.5 save step 1 and 9): every violation of the body,
 * each with its JSON path. The message names no value.
 */
public class ChecklistValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient List<Violation> violations;

    public ChecklistValidationException(List<Violation> violations) {
        super("The checklist body breaks " + violations.size() + " rule(s).");
        if (violations.isEmpty()) {
            throw new IllegalArgumentException("A validation failure needs a violation.");
        }
        this.violations = List.copyOf(violations);
    }

    public List<Violation> violations() {
        return violations;
    }
}
