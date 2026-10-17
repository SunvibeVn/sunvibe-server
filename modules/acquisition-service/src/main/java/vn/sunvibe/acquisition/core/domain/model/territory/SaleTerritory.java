package vn.sunvibe.acquisition.core.domain.model.territory;

import vn.sunvibe.shared.time.AppClock;

import java.time.Instant;

/**
 * Who looks after one ward (acquisition-owner-lead-queue BR-5): a ward has at most one person. It gives a new lead
 * to the person of its ward, and staff change it by assigning the ward to someone else.
 */
public final class SaleTerritory {

    private final String wardCode;
    private final String userId;
    private final String assignedBy;
    private final Instant assignedAt;

    private SaleTerritory(String wardCode, String userId, String assignedBy, Instant assignedAt) {
        this.wardCode = required(wardCode, "wardCode");
        this.userId = required(userId, "userId");
        this.assignedBy = required(assignedBy, "assignedBy");
        this.assignedAt = required(assignedAt, "assignedAt");
    }

    /**
     * The actor gives the ward to the person from now on; any earlier person of the ward is replaced.
     */
    public static SaleTerritory assign(String wardCode, String userId, String actorId) {
        return new SaleTerritory(wardCode, userId, actorId, AppClock.currentUTC());
    }

    /**
     * Load from database (reconstruct).
     */
    public static SaleTerritory load(String wardCode, String userId, String assignedBy, Instant assignedAt) {
        return new SaleTerritory(wardCode, userId, assignedBy, assignedAt);
    }

    // ==================== Getters ====================

    public String getWardCode() {
        return wardCode;
    }

    public String getUserId() {
        return userId;
    }

    public String getAssignedBy() {
        return assignedBy;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value;
    }
}
