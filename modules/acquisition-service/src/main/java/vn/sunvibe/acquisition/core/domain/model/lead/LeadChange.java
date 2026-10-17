package vn.sunvibe.acquisition.core.domain.model.lead;

import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType;

import java.time.Instant;

/**
 * What one command changed on a lead, in the shape of a history entry. The lead returns it; the caller adds the actor
 * and the time and saves it as an {@code OwnerLeadActivity}. Statuses and assignees are null when the command did not
 * touch them. The note is free text written by staff, so it is never logged.
 */
public record LeadChange(OwnerLeadActivityType type, OwnerLeadStatus fromStatus, OwnerLeadStatus toStatus,
                         String fromAssigneeId, String toAssigneeId, String note, Instant nextActionAt) {

    /** The note is staff text: never print it. */
    @Override
    public String toString() {
        return "LeadChange[" + type + "]";
    }
}
