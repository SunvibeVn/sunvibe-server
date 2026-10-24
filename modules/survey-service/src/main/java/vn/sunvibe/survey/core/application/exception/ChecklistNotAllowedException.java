package vn.sunvibe.survey.core.application.exception;

import java.io.Serial;
import java.util.List;

/**
 * A checklist cannot be started on this appointment now, or the caller already holds the draft (survey-checklist spec
 * 5.5, 7.3): the field that says why ({@code appointmentId}, {@code intent}, {@code status}, {@code holder}) and the
 * values that would be allowed, empty when none applies.
 */
public class ChecklistNotAllowedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String field;
    private final List<String> allowedValues;

    public ChecklistNotAllowedException(String field, List<String> allowedValues) {
        super(message(field));
        this.field = field;
        this.allowedValues = List.copyOf(allowedValues);
    }

    public String getField() {
        return field;
    }

    public List<String> getAllowedValues() {
        return allowedValues;
    }

    private static String message(String field) {
        return switch (field) {
            case "appointmentId" -> "A checklist can be started only on the lead's current visit.";
            case "intent" -> "Checklists are for long-term rental leads.";
            case "status" -> "The lead is not in Survey Scheduled.";
            case "holder" -> "You already hold this draft.";
            default -> "This checklist action is not allowed now.";
        };
    }
}
