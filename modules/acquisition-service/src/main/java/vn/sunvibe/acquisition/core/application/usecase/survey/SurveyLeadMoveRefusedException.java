package vn.sunvibe.acquisition.core.application.usecase.survey;

import java.io.Serial;

/**
 * The lead's status does not allow the move a survey command asked for (survey-schedule spec 4.3). Carries the
 * current status as an enum name; the message holds statuses only.
 */
public class SurveyLeadMoveRefusedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String status;

    public SurveyLeadMoveRefusedException(String status) {
        super("The lead's status " + status + " does not allow this survey change.");
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}
