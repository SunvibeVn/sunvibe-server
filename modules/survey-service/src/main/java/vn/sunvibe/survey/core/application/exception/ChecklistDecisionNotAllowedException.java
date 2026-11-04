package vn.sunvibe.survey.core.application.exception;

import java.io.Serial;
import java.util.List;

/** The revision cannot take this decision now (survey-checklist submission spec 4.5, 7.3): one field per failed rule. */
public class ChecklistDecisionNotAllowedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient List<String> fields;

    public ChecklistDecisionNotAllowedException(List<String> fields) {
        super("The revision cannot take this decision now.");
        this.fields = List.copyOf(fields);
    }

    public List<String> fields() {
        return fields;
    }
}
