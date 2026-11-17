package vn.sunvibe.brokerage.core.application.shared.external.lead;

import java.util.Collection;
import java.util.List;

/**
 * The current assignees of a set of basis leads with no viewer, owned by acquisition-service; the adapter lives in the
 * app module (ADR-009; E05-S6 spec S6-D10, S6-D17). Only for group (ii) of the listing brief, read for listing's own
 * commands, inside the caller's transaction; nothing is written.
 */
public interface BasisLeadAssigneePort {

    /** The assignee of each of those leads; an unknown or malformed id is absent. */
    List<LeadAssigneeFacts> assigneesOf(Collection<String> leadIds);
}
