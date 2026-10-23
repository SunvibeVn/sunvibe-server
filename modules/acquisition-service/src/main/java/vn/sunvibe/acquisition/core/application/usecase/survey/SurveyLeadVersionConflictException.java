package vn.sunvibe.acquisition.core.application.usecase.survey;

import java.io.Serial;

/**
 * The lead changed since the survey caller read it: the version it named is not the stored one, or a concurrent
 * command saved the lead first (survey-schedule spec 5.5). Nothing was changed.
 */
public class SurveyLeadVersionConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SurveyLeadVersionConflictException() {
        super("The lead was changed by someone else.");
    }
}
