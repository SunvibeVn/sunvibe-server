package vn.sunvibe.acquisition.core.domain.model.log;

import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;

import java.time.Instant;

/**
 * One time a staff member opened the detail of a lead, which holds the owner's contact data
 * (acquisition-owner-lead-queue BR-7): who looked, at which lead and when. The id is a Snowflake number given by the
 * caller. It is never changed after it is written.
 */
public final class OwnerLeadAccessLog {

    private final Long id;
    private final OwnerLeadId leadId;
    private final String userId;
    private final Instant accessedAt;

    private OwnerLeadAccessLog(Long id, OwnerLeadId leadId, String userId, Instant accessedAt) {
        this.id = required(id, "id");
        this.leadId = required(leadId, "leadId");
        this.userId = required(userId, "userId");
        this.accessedAt = required(accessedAt, "accessedAt");
    }

    /**
     * A staff member opened the detail of a lead at the given moment.
     */
    public static OwnerLeadAccessLog create(Long id, OwnerLeadId leadId, String userId, Instant accessedAt) {
        return new OwnerLeadAccessLog(id, leadId, userId, accessedAt);
    }

    /**
     * Load from database (reconstruct).
     */
    public static OwnerLeadAccessLog load(Long id, OwnerLeadId leadId, String userId, Instant accessedAt) {
        return new OwnerLeadAccessLog(id, leadId, userId, accessedAt);
    }

    // ==================== Getters ====================

    public Long getId() {
        return id;
    }

    public OwnerLeadId getLeadId() {
        return leadId;
    }

    public String getUserId() {
        return userId;
    }

    public Instant getAccessedAt() {
        return accessedAt;
    }

    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value;
    }
}
