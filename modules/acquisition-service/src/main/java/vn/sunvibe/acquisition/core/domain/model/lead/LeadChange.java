package vn.sunvibe.acquisition.core.domain.model.lead;

import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType;

import java.time.Instant;

/**
 * What one command changed on a lead, in the shape of a history entry. The lead returns it; the caller adds the actor
 * and the time and saves it as an {@code OwnerLeadActivity}. Statuses and assignees are null when the command did not
 * touch them. The note is free text written by staff, so it is never logged. The reference names the survey
 * appointment the command concerns (its id and number), null when there is none (survey-schedule spec 4.6, A-12).
 */
public record LeadChange(OwnerLeadActivityType type, OwnerLeadStatus fromStatus, OwnerLeadStatus toStatus,
                         String fromAssigneeId, String toAssigneeId, String note, Instant nextActionAt,
                         String referenceId, Integer referenceNo) {

    /** A change that concerns no survey appointment. */
    public LeadChange(OwnerLeadActivityType type, OwnerLeadStatus fromStatus, OwnerLeadStatus toStatus,
                      String fromAssigneeId, String toAssigneeId, String note, Instant nextActionAt) {
        this(type, fromStatus, toStatus, fromAssigneeId, toAssigneeId, note, nextActionAt, null, null);
    }

    /** The same change naming the survey appointment it affected (for example the one a hold cancelled). */
    public LeadChange withReference(String referenceIdOrNull, Integer referenceNoOrNull) {
        return new LeadChange(type, fromStatus, toStatus, fromAssigneeId, toAssigneeId, note, nextActionAt,
                referenceIdOrNull, referenceNoOrNull);
    }

    /** The note is staff text: never print it. */
    @Override
    public String toString() {
        return "LeadChange[" + type + "]";
    }
}
