package vn.sunvibe.survey.core.application.shared.external.lead;

import java.io.Serial;

/** The lead changed since the survey command read it (stale version or a concurrent save). Nothing was changed. */
public class LeadVersionConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LeadVersionConflictException() {
        super("The lead was changed by someone else.");
    }
}
