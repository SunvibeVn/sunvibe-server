package vn.sunvibe.property.core.domain.model.intake;

import java.time.Instant;

/**
 * A manager's processing restriction (property-create intake D5-16): it holds for the lead, so a new intake cannot dodge
 * it, optionally for one unit. The reasons stay out of every log and of History.
 */
public final class IntakeSuspension {

    private final String id;
    private final String leadId;
    private final String intakeIdOrNull;
    private final String unitIdOrNull;
    private final String reason;
    private final String createdByUserId;
    private final Instant createdAt;
    private String releaseReason;
    private String releasedByUserId;
    private Instant releasedAt;

    private IntakeSuspension(String id, String leadId, String intakeIdOrNull, String unitIdOrNull, String reason,
                             String createdByUserId, Instant createdAt, String releaseReason, String releasedByUserId,
                             Instant releasedAt) {
        this.id = id;
        this.leadId = leadId;
        this.intakeIdOrNull = intakeIdOrNull;
        this.unitIdOrNull = unitIdOrNull;
        this.reason = reason;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
        this.releaseReason = releaseReason;
        this.releasedByUserId = releasedByUserId;
        this.releasedAt = releasedAt;
    }

    public static IntakeSuspension create(String id, String leadId, String intakeIdOrNull, String unitIdOrNull,
                                          String reason, String createdByUserId, Instant now) {
        return new IntakeSuspension(id, leadId, intakeIdOrNull, unitIdOrNull, reason, createdByUserId, now, null, null, null);
    }

    public static IntakeSuspension load(String id, String leadId, String intakeIdOrNull, String unitIdOrNull, String reason,
                                        String createdByUserId, Instant createdAt, String releaseReason,
                                        String releasedByUserId, Instant releasedAt) {
        return new IntakeSuspension(id, leadId, intakeIdOrNull, unitIdOrNull, reason, createdByUserId, createdAt,
                releaseReason, releasedByUserId, releasedAt);
    }

    public boolean isActive() {
        return releasedAt == null;
    }

    public boolean onWholeLead() {
        return unitIdOrNull == null;
    }

    public void release(String reasonText, String managerUserId, Instant now) {
        releaseReason = reasonText;
        releasedByUserId = managerUserId;
        releasedAt = now;
    }

    public String getId() {
        return id;
    }

    public String getLeadId() {
        return leadId;
    }

    public String getIntakeIdOrNull() {
        return intakeIdOrNull;
    }

    public String getUnitIdOrNull() {
        return unitIdOrNull;
    }

    public String getReason() {
        return reason;
    }

    public String getCreatedByUserId() {
        return createdByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getReleaseReason() {
        return releaseReason;
    }

    public String getReleasedByUserId() {
        return releasedByUserId;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    @Override
    public String toString() {
        return "IntakeSuspension[" + id + "]";
    }
}
