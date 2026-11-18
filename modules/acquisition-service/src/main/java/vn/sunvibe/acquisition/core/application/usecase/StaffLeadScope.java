package vn.sunvibe.acquisition.core.application.usecase;

import java.util.Set;

/**
 * The owner leads a staff member may see, as one answer for a list: every lead, or the ids of the leads assigned to
 * them now, in any status.
 *
 * @param allLeads        the staff member sees every lead; then no id is listed
 * @param assignedLeadIds the ids of the leads assigned to them when {@code allLeads} is false; empty for no lead
 */
public record StaffLeadScope(boolean allLeads, Set<String> assignedLeadIds) {

    public StaffLeadScope {
        assignedLeadIds = Set.copyOf(assignedLeadIds);
        if (allLeads && !assignedLeadIds.isEmpty()) {
            throw new IllegalArgumentException("A scope of every lead lists no lead id.");
        }
    }
}
