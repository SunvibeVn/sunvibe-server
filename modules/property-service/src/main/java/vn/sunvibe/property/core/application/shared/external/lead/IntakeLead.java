package vn.sunvibe.property.core.application.shared.external.lead;

import vn.sunvibe.property.core.domain.model.lead.LeadStatus;

import java.util.Set;

/**
 * What an intake reads of a lead (property-create intake spec 5.1). No phone, email or address.
 *
 * @param assigneeOrNull   the assignee now
 * @param earlierAssignees every user who has been its assignee, the current one included
 * @param closed           the lead is DUPLICATE, REJECTED or WITHDRAWN
 */
public record IntakeLead(String leadId, String leadCode, String statusName, String intent, int intentCycle,
                         String assigneeOrNull, Set<String> earlierAssignees, String wardName, boolean closed) {

    public IntakeLead {
        earlierAssignees = Set.copyOf(earlierAssignees);
    }

    /**
     * The status as an enum. A name it does not know fails here, loudly; LeadStatusContractTest keeps the names of
     * acquisition and property equal, so none ships.
     */
    public LeadStatus status() {
        return LeadStatus.valueOf(statusName);
    }
}
