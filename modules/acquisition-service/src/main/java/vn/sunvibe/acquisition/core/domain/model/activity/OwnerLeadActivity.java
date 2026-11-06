package vn.sunvibe.acquisition.core.domain.model.activity;

import vn.sunvibe.acquisition.core.domain.model.lead.LeadChange;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.shared.time.AppClock;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * One entry in the history of an owner lead: what happened, who did it (empty for the system) and when. The id is a
 * Snowflake number given by the caller. Entries of survey and requirement commands carry no text, only the reference
 * of the survey appointment they concern (survey-schedule BR-8, spec 4.9). Entries of survey checklists reference
 * the checklist and, for a save, the group as the reference detail (survey-checklist BR-15, spec A-13); a review keeps
 * its outcome and kind there, with the lead's move (survey-checklist submission spec 4.9).
 */
public final class OwnerLeadActivity {

    private final Long id;
    private final OwnerLeadId leadId;
    private final OwnerLeadActivityType type;
    private final String actorUserId;
    private final OwnerLeadStatus fromStatus;
    private final OwnerLeadStatus toStatus;
    private final String fromAssigneeId;
    private final String toAssigneeId;
    private final String note;
    private final Instant nextActionAt;
    private final Instant occurredAt;
    private final String referenceId;
    private final Integer referenceNo;
    private final String referenceDetail;

    private static final Set<OwnerLeadActivityType> CHECKLIST_TYPES = EnumSet.of(
            OwnerLeadActivityType.CHECKLIST_STARTED, OwnerLeadActivityType.CHECKLIST_TAKEN_OVER,
            OwnerLeadActivityType.CHECKLIST_SAVED, OwnerLeadActivityType.CHECKLIST_MEDIA_ADDED,
            OwnerLeadActivityType.CHECKLIST_MEDIA_REMOVED, OwnerLeadActivityType.CHECKLIST_SUBMITTED,
            OwnerLeadActivityType.CHECKLIST_REVISION_STARTED);
    /** The types an inventory intake writes (property-create intake spec 4.12). */
    private static final Set<OwnerLeadActivityType> INVENTORY_TYPES = EnumSet.of(
            OwnerLeadActivityType.INVENTORY_INTAKE_COMPLETED, OwnerLeadActivityType.INVENTORY_INTAKE_SUSPENDED,
            OwnerLeadActivityType.INVENTORY_INTAKE_RESTORED);
    /** The checklist types whose entry carries a detail: the group of a save, the category of a photo. */
    private static final Set<OwnerLeadActivityType> DETAILED_CHECKLIST_TYPES = EnumSet.of(
            OwnerLeadActivityType.CHECKLIST_SAVED, OwnerLeadActivityType.CHECKLIST_MEDIA_ADDED,
            OwnerLeadActivityType.CHECKLIST_MEDIA_REMOVED);

    private OwnerLeadActivity(Long id, OwnerLeadId leadId, OwnerLeadActivityType type, String actorUserId,
                              OwnerLeadStatus fromStatus, OwnerLeadStatus toStatus, String fromAssigneeId,
                              String toAssigneeId, String note, Instant nextActionAt, Instant occurredAt,
                              String referenceId, Integer referenceNo, String referenceDetail) {
        this.id = required(id, "id");
        this.leadId = required(leadId, "leadId");
        this.type = required(type, "type");
        this.actorUserId = actorUserId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.fromAssigneeId = fromAssigneeId;
        this.toAssigneeId = toAssigneeId;
        this.note = note;
        this.nextActionAt = nextActionAt;
        this.occurredAt = required(occurredAt, "occurredAt");
        if ((referenceId == null) != (referenceNo == null)) {
            throw new IllegalArgumentException("referenceId and referenceNo come together.");
        }
        this.referenceId = referenceId;
        this.referenceNo = referenceNo;
        if (referenceDetail != null && referenceId == null) {
            throw new IllegalArgumentException("referenceDetail needs a reference.");
        }
        this.referenceDetail = referenceDetail;
    }

    /**
     * The owner sent the form; nobody acted, so there is no actor.
     */
    public static OwnerLeadActivity submitted(Long id, OwnerLeadId leadId) {
        return new OwnerLeadActivity(id, leadId, OwnerLeadActivityType.SUBMITTED, null, null, null, null, null, null,
                null, AppClock.currentUTC(), null, null, null);
    }

    /**
     * The system gave a new lead to the person responsible for its ward, so the lead went from NEW to ASSIGNED.
     */
    public static OwnerLeadActivity autoAssigned(Long id, OwnerLeadId leadId, String toAssigneeId) {
        return new OwnerLeadActivity(id, leadId, OwnerLeadActivityType.AUTO_ASSIGNED, null, OwnerLeadStatus.NEW,
                OwnerLeadStatus.ASSIGNED, null, toAssigneeId, null, null, AppClock.currentUTC(), null, null, null);
    }

    /**
     * The history entry of one change a lead returned: who did it (the actor) and when it happened.
     */
    public static OwnerLeadActivity from(long id, OwnerLeadId leadId, String actorUserIdOrNull, LeadChange change,
                                         Instant occurredAt) {
        return new OwnerLeadActivity(id, leadId, change.type(), actorUserIdOrNull, change.fromStatus(),
                change.toStatus(), change.fromAssigneeId(), change.toAssigneeId(), change.note(),
                change.nextActionAt(), occurredAt, change.referenceId(), change.referenceNo(), null);
    }

    /**
     * The entry of a survey move that keeps a detail with its statuses: a review, with its outcome and kind and the
     * checklist as the reference (survey-checklist submission spec 4.9, plan PD-5). No reason, note or limitation.
     */
    public static OwnerLeadActivity reviewed(long id, OwnerLeadId leadId, String actorUserId, LeadChange change,
                                             String detail, Instant occurredAt) {
        return new OwnerLeadActivity(id, leadId, change.type(), required(actorUserId, "actorUserId"),
                change.fromStatus(), change.toStatus(), null, null, null, null, occurredAt, change.referenceId(),
                change.referenceNo(), required(detail, "detail"));
    }

    /**
     * An inventory intake event (property-create intake spec 4.12): the reference is the intake and its number, or neither
     * for a suspension with no intake. No statuses, no assignees and no text: the event never moves the lead, and the
     * actor is null for an owner's withdrawal.
     */
    public static OwnerLeadActivity inventoryEvent(long id, OwnerLeadId leadId, OwnerLeadActivityType type,
                                                   String actorUserIdOrNull, String intakeIdOrNull,
                                                   Integer intakeNoOrNull, Instant occurredAt) {
        if (!INVENTORY_TYPES.contains(type)) {
            throw new IllegalArgumentException("Not an inventory activity type: " + type);
        }
        return new OwnerLeadActivity(id, leadId, type, actorUserIdOrNull, null, null, null, null, null, null, occurredAt,
                intakeIdOrNull, intakeNoOrNull, null);
    }

    /**
     * A survey checklist event (survey-checklist BR-15, spec A-13; survey-media spec 5.3): the actor, the checklist
     * and its number and, only and always for a save, the group as the detail, and for a photo added or removed its
     * evidence category; no note, statuses or assignees, because the event never moves the lead.
     *
     * @throws IllegalArgumentException for a type other than the checklist activity types, or a detail that does not
     *                                  match the type
     */
    public static OwnerLeadActivity checklistEvent(long id, OwnerLeadId leadId, OwnerLeadActivityType type,
                                                   String actorUserId, String checklistId, int checklistNo,
                                                   String detailOrNull, Instant occurredAt) {
        if (!CHECKLIST_TYPES.contains(type)) {
            throw new IllegalArgumentException("Not a checklist activity type: " + type);
        }
        if (DETAILED_CHECKLIST_TYPES.contains(type) != (detailOrNull != null)) {
            throw new IllegalArgumentException("Only a checklist save (its group) and a photo entry (its category) "
                    + "carry a detail, and they always do.");
        }
        return new OwnerLeadActivity(id, leadId, type, required(actorUserId, "actorUserId"), null, null, null, null,
                null, null, occurredAt, required(checklistId, "checklistId"), checklistNo, detailOrNull);
    }

    /**
     * Load from database (reconstruct).
     */
    public static OwnerLeadActivity load(Long id, OwnerLeadId leadId, OwnerLeadActivityType type, String actorUserId,
                                         OwnerLeadStatus fromStatus, OwnerLeadStatus toStatus,
                                         String fromAssigneeId, String toAssigneeId, String note,
                                         Instant nextActionAt, Instant occurredAt, String referenceId,
                                         Integer referenceNo, String referenceDetail) {
        return new OwnerLeadActivity(id, leadId, type, actorUserId, fromStatus, toStatus, fromAssigneeId,
                toAssigneeId, note, nextActionAt, occurredAt, referenceId, referenceNo, referenceDetail);
    }

    // ==================== Getters ====================

    public Long getId() {
        return id;
    }

    public OwnerLeadId getLeadId() {
        return leadId;
    }

    public OwnerLeadActivityType getType() {
        return type;
    }

    public String getActorUserId() {
        return actorUserId;
    }

    public OwnerLeadStatus getFromStatus() {
        return fromStatus;
    }

    public OwnerLeadStatus getToStatus() {
        return toStatus;
    }

    public String getFromAssigneeId() {
        return fromAssigneeId;
    }

    public String getToAssigneeId() {
        return toAssigneeId;
    }

    public String getNote() {
        return note;
    }

    public Instant getNextActionAt() {
        return nextActionAt;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    /** The id of the survey appointment or checklist the entry concerns, by type, or null. */
    public String getReferenceId() {
        return referenceId;
    }

    /** The number of that appointment or checklist within the lead, or null. */
    public Integer getReferenceNo() {
        return referenceNo;
    }

    /** The group of a CHECKLIST_SAVED entry, the category of a photo entry, the decision of a review, or null. */
    public String getReferenceDetail() {
        return referenceDetail;
    }

    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value;
    }
}
