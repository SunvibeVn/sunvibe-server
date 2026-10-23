package vn.sunvibe.survey.core.application.shared.external.lead;

import java.io.Serial;

/** The lead's status does not allow the move a survey command asked for. Carries the status as an enum name. */
public class LeadMoveRefusedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String status;

    public LeadMoveRefusedException(String status) {
        super("The lead's status " + status + " does not allow this survey change.");
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}
