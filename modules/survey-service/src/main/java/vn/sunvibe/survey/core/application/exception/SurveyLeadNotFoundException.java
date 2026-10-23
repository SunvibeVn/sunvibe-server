package vn.sunvibe.survey.core.application.exception;

import java.io.Serial;

/**
 * The lead, or the appointment, does not exist or the caller may not see its lead (survey-schedule BR-1, E-1). One
 * answer for all causes, so a SALE never learns that someone else's lead or appointment exists.
 */
public class SurveyLeadNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SurveyLeadNotFoundException() {
        super("Lead not found.");
    }
}
