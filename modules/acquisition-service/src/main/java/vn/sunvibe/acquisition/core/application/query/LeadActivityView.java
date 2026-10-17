package vn.sunvibe.acquisition.core.application.query;

import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivity;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;

import java.time.Instant;

/**
 * One history entry of a lead as staff see it. The actor is null for the system or the owner who sent the form.
 */
public record LeadActivityView(OwnerLeadActivityType type, String actorUserId, OwnerLeadStatus fromStatus,
                               OwnerLeadStatus toStatus, String fromAssigneeId, String toAssigneeId, String note,
                               Instant nextActionAt, Instant occurredAt) {

    public static LeadActivityView of(OwnerLeadActivity activity) {
        return new LeadActivityView(activity.getType(), activity.getActorUserId(), activity.getFromStatus(),
                activity.getToStatus(), activity.getFromAssigneeId(), activity.getToAssigneeId(), activity.getNote(),
                activity.getNextActionAt(), activity.getOccurredAt());
    }

    /** The note is staff text: never print it. */
    @Override
    public String toString() {
        return "LeadActivityView[" + type + "]";
    }
}
