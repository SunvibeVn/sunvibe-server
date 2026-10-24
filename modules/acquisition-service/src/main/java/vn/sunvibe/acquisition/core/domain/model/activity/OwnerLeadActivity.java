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
 * the checklist and, for a save, the group as the reference detail (survey-checklist BR-15, spec A-13).
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
            OwnerLeadActivityType.CHECKLIST_SAVED);

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
     * A survey checklist event (survey-checklist BR-15, spec A-13): the actor, the checklist and its number and, only
     * and always for a save, the group as the detail; no note, statuses or assignees, because the event never moves
     * the lead.
     *
     * @throws IllegalArgumentException for a type other than the three checklist types, or a detail that does not
     *                                  match the type
     */
    public static OwnerLeadActivity checklistEvent(long id, OwnerLeadId leadId, OwnerLeadActivityType type,
                                                   String actorUserId, String checklistId, int checklistNo,
                                                   String detailOrNull, Instant occurredAt) {
        if (!CHECKLIST_TYPES.contains(type)) {
            throw new IllegalArgumentException("Not a checklist activity type: " + type);
        }
        if ((type == OwnerLeadActivityType.CHECKLIST_SAVED) != (detailOrNull != null)) {
            throw new IllegalArgumentException("Only a checklist save carries the group, and it always does.");
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

    /** The checklist group a CHECKLIST_SAVED entry concerns, or null. */
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
