package vn.sunvibe.acquisition.core.domain.model.territory;

import java.time.Instant;

/**
 * One change of the person who looks after a ward (acquisition-owner-lead-queue BR-10): the person before (empty when
 * the ward had nobody), the person after (empty when the ward was left unassigned), who made the change and when. The
 * id is a Snowflake number given by the caller. It is never changed after it is written.
 */
public final class SaleTerritoryChange {

    private final Long id;
    private final String wardCode;
    private final String oldUserId;
    private final String newUserId;
    private final String actorUserId;
    private final Instant occurredAt;

    private SaleTerritoryChange(Long id, String wardCode, String oldUserId, String newUserId, String actorUserId,
                                Instant occurredAt) {
        this.id = required(id, "id");
        this.wardCode = required(wardCode, "wardCode");
        this.oldUserId = oldUserId;
        this.newUserId = newUserId;
        this.actorUserId = required(actorUserId, "actorUserId");
        this.occurredAt = required(occurredAt, "occurredAt");
    }

    /**
     * The person of a ward changed from the old one to the new one (either may be null) at the given moment.
     */
    public static SaleTerritoryChange create(Long id, String wardCode, String oldUserIdOrNull,
                                             String newUserIdOrNull, String actorUserId, Instant occurredAt) {
        return new SaleTerritoryChange(id, wardCode, oldUserIdOrNull, newUserIdOrNull, actorUserId, occurredAt);
    }

    /**
     * Load from database (reconstruct).
     */
    public static SaleTerritoryChange load(Long id, String wardCode, String oldUserIdOrNull,
                                           String newUserIdOrNull, String actorUserId, Instant occurredAt) {
        return new SaleTerritoryChange(id, wardCode, oldUserIdOrNull, newUserIdOrNull, actorUserId, occurredAt);
    }

    // ==================== Getters ====================

    public Long getId() {
        return id;
    }

    public String getWardCode() {
        return wardCode;
    }

    public String getOldUserId() {
        return oldUserId;
    }

    public String getNewUserId() {
        return newUserId;
    }

    public String getActorUserId() {
        return actorUserId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value;
    }
}
