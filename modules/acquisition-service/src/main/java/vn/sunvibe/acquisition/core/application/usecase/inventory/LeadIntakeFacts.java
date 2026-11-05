package vn.sunvibe.acquisition.core.application.usecase.inventory;

import java.util.Set;

/**
 * What the inventory intake reads of a lead (property-create intake spec 5.1): status and intent as enum names, the current
 * intent cycle, the assignee, every user who was its assignee before, and the lead's code and ward. It holds no phone,
 * email or address.
 *
 * @param assignedUserIdOrNull the assignee now; null on an unassigned lead
 * @param earlierAssignees     every user named as an assignee on the lead's History, the current one included
 * @param closed               the lead is DUPLICATE, REJECTED or WITHDRAWN
 */
public record LeadIntakeFacts(String leadId, String requestCode, String status, String intent, int intentCycle,
                              String assignedUserIdOrNull, Set<String> earlierAssignees, String wardName,
                              boolean closed) {

    public LeadIntakeFacts {
        earlierAssignees = Set.copyOf(earlierAssignees);
    }
}
