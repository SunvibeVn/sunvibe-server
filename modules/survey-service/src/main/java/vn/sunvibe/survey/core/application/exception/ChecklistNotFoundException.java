package vn.sunvibe.survey.core.application.exception;

import java.io.Serial;

/**
 * The checklist or checkpoint does not exist, or its lead is not visible to the caller (survey-checklist spec 4.4,
 * 7.3): one answer for both, so a caller learns nothing about leads they cannot see.
 */
public class ChecklistNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ChecklistNotFoundException() {
        super("Checklist not found.");
    }
}
