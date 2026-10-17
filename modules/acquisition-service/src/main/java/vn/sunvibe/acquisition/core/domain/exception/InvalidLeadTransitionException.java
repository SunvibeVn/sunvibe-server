package vn.sunvibe.acquisition.core.domain.exception;

import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;

import java.io.Serial;
import java.util.List;

/**
 * A command asks a lead to do something its status does not allow (acquisition module brief BR-3). Carries the
 * current status, the wanted status (null for an assignment or a note on a closed lead) and the statuses the lead may
 * move to now. The message holds statuses only, never lead data.
 */
public class InvalidLeadTransitionException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final OwnerLeadStatus from;
    private final OwnerLeadStatus to;
    private final List<OwnerLeadStatus> allowed;

    public InvalidLeadTransitionException(OwnerLeadStatus from, OwnerLeadStatus toOrNull,
                                          List<OwnerLeadStatus> allowed) {
        super(toOrNull == null
                ? "The lead cannot be changed while it is " + from + "."
                : "The lead cannot move from " + from + " to " + toOrNull + ".");
        this.from = from;
        this.to = toOrNull;
        this.allowed = List.copyOf(allowed);
    }

    public OwnerLeadStatus getFrom() {
        return from;
    }

    /** The wanted status, or null when the command was an assignment or a note on a closed lead. */
    public OwnerLeadStatus getTo() {
        return to;
    }

    public List<OwnerLeadStatus> getAllowed() {
        return allowed;
    }
}
