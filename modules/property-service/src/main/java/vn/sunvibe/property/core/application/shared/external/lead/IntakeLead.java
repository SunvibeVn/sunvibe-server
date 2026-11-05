package vn.sunvibe.property.core.application.shared.external.lead;

import java.util.Set;

/**
 * What an intake reads of a lead (property-create intake spec 5.1). No phone, email or address.
 *
 * @param assigneeOrNull   the assignee now
 * @param earlierAssignees every user who has been its assignee, the current one included
 * @param closed           the lead is DUPLICATE, REJECTED or WITHDRAWN
 */
public record IntakeLead(String leadId, String leadCode, String status, String intent, int intentCycle,
                         String assigneeOrNull, Set<String> earlierAssignees, String wardName, boolean closed) {

    public IntakeLead {
        earlierAssignees = Set.copyOf(earlierAssignees);
    }
}
