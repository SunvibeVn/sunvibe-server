package vn.sunvibe.brokerage.core.application.shared.external.lead;

import java.util.Set;

/**
 * The owner leads a staff member may see, read at one moment (E05-S7 spec S7-D2).
 *
 * @param allLeads the staff member sees every lead
 * @param leadIds  the leads assigned to them when {@code allLeads} is false; empty for no lead
 */
public record LeadScopeFacts(boolean allLeads, Set<String> leadIds) {

    public LeadScopeFacts {
        leadIds = Set.copyOf(leadIds);
    }
}
