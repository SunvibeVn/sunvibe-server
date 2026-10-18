package vn.sunvibe.acquisition.core.application.query;

import vn.sunvibe.acquisition.core.application.shared.StaffNames;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivity;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;

import java.time.Instant;
import java.util.Map;

/**
 * One history entry of a lead as staff see it. The actor is null for the system or the owner who sent the form. Each
 * name is the full name of the id before it, null when that id is null or has no user row
 * (acquisition-owner-lead-queue-ui C-18).
 */
public record LeadActivityView(OwnerLeadActivityType type, String actorUserId, String actorName,
                               OwnerLeadStatus fromStatus, OwnerLeadStatus toStatus, String fromAssigneeId,
                               String fromAssigneeName, String toAssigneeId, String toAssigneeName, String note,
                               Instant nextActionAt, Instant occurredAt) {

    /** The view of a history entry; {@code names} maps staff ids to full names and may lack any of them. */
    public static LeadActivityView of(OwnerLeadActivity activity, Map<String, String> names) {
        return new LeadActivityView(activity.getType(), activity.getActorUserId(),
                StaffNames.nameOf(names, activity.getActorUserId()), activity.getFromStatus(),
                activity.getToStatus(), activity.getFromAssigneeId(),
                StaffNames.nameOf(names, activity.getFromAssigneeId()), activity.getToAssigneeId(),
                StaffNames.nameOf(names, activity.getToAssigneeId()), activity.getNote(),
                activity.getNextActionAt(), activity.getOccurredAt());
    }

    /** The note is staff text: never print it. */
    @Override
    public String toString() {
        return "LeadActivityView[" + type + "]";
    }
}
