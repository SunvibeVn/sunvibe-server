package vn.sunvibe.property.core.domain.model.intake;

import java.time.Instant;

/**
 * A request that an owner confirm an account link (property-create intake spec 4.6, D5-6). {@code targetUserId} is null when
 * the address matched no confirmable account: the row exists all the same, so a Sale learns nothing from it.
 */
public final class OwnerRequest {

    /** A-5: wrong codes across details, confirm and decline before the request is void. */
    public static final int MAX_WRONG_CODES = 5;

    private final String id;
    private final String intakeId;
    private final String targetUserId;
    private final String codeHash;
    private final Instant expiresAt;
    private final String createdByUserId;
    private final Instant createdAt;
    private int failedAttempts;
    private String displaySnapshot;
    private Instant consumedAt;
    private Instant voidedAt;
    private Instant declinedAt;

    private OwnerRequest(String id, String intakeId, String targetUserId, String codeHash, int failedAttempts,
                         String displaySnapshot, Instant expiresAt, Instant consumedAt, Instant voidedAt,
                         Instant declinedAt, String createdByUserId, Instant createdAt) {
        this.id = id;
        this.intakeId = intakeId;
        this.targetUserId = targetUserId;
        this.codeHash = codeHash;
        this.failedAttempts = failedAttempts;
        this.displaySnapshot = displaySnapshot;
        this.expiresAt = expiresAt;
        this.consumedAt = consumedAt;
        this.voidedAt = voidedAt;
        this.declinedAt = declinedAt;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
    }

    public static OwnerRequest create(String id, String intakeId, String targetUserIdOrNull, String codeHash,
                                      String displaySnapshotOrNull, Instant expiresAt, String createdByUserId, Instant now) {
        return new OwnerRequest(id, intakeId, targetUserIdOrNull, codeHash, 0, displaySnapshotOrNull, expiresAt, null, null,
                null, createdByUserId, now);
    }

    public static OwnerRequest load(String id, String intakeId, String targetUserId, String codeHash, int failedAttempts,
                                    String displaySnapshot, Instant expiresAt, Instant consumedAt, Instant voidedAt,
                                    Instant declinedAt, String createdByUserId, Instant createdAt) {
        return new OwnerRequest(id, intakeId, targetUserId, codeHash, failedAttempts, displaySnapshot, expiresAt, consumedAt,
                voidedAt, declinedAt, createdByUserId, createdAt);
    }

    /** No code can be used on it any more, for whatever reason; the date alone does not make it closed in the store. */
    public boolean isClosed() {
        return consumedAt != null || voidedAt != null || declinedAt != null;
    }

    public boolean isOpen(Instant now) {
        return !isClosed() && now.isBefore(expiresAt);
    }

    public void voidNow(Instant now) {
        if (voidedAt == null && consumedAt == null && declinedAt == null) {
            voidedAt = now;
        }
        displaySnapshot = null;
    }

    public void decline(Instant now) {
        declinedAt = now;
        displaySnapshot = null;
    }

    public void consume(Instant now) {
        consumedAt = now;
    }

    /** Spec 4.6 step 1: a dead request keeps no address. */
    public void clearSnapshot() {
        displaySnapshot = null;
    }

    public String getId() {
        return id;
    }

    public String getIntakeId() {
        return intakeId;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public String getDisplaySnapshot() {
        return displaySnapshot;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public Instant getDeclinedAt() {
        return declinedAt;
    }

    public String getCreatedByUserId() {
        return createdByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** The hash and the snapshot stay out of every log. */
    @Override
    public String toString() {
        return "OwnerRequest[" + id + "]";
    }
}
