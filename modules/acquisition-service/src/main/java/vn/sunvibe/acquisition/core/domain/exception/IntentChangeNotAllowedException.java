package vn.sunvibe.acquisition.core.domain.exception;

import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;

import java.io.Serial;
import java.util.List;

/**
 * The lead's status does not allow a requirement change (survey-schedule US-5, spec 4.6): only an assigned, contacted,
 * held or survey-scheduled lead may change it. Carries the current status and the statuses that allow it. The message
 * holds statuses only, never lead data.
 */
public class IntentChangeNotAllowedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final OwnerLeadStatus status;
    private final List<OwnerLeadStatus> allowed;

    public IntentChangeNotAllowedException(OwnerLeadStatus status, List<OwnerLeadStatus> allowed) {
        super("The requirement of a lead cannot be changed while it is " + status + ".");
        this.status = status;
        this.allowed = List.copyOf(allowed);
    }

    public OwnerLeadStatus getStatus() {
        return status;
    }

    /** The statuses in which the requirement may be changed. */
    public List<OwnerLeadStatus> getAllowed() {
        return allowed;
    }
}
