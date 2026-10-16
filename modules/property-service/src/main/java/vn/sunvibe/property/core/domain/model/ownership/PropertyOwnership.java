package vn.sunvibe.property.core.domain.model.ownership;

import vn.sunvibe.property.core.domain.model.vo.PropertyId;
import vn.sunvibe.shared.time.AppClock;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Domain Model - PropertyOwnership (Aggregate Root)
 * <p>
 * Links a user to a property (brief BR-9). It is a record of its own, not a role: revoking the OWNER role does not
 * touch it (BR-11). In W8 only one kind is written: OWNER, UNVERIFIED, valid from the creation of the property,
 * with no end date (BR-10). The user is held by id only; auth-service owns the user.
 * An ownership is immutable: nothing in W8 changes it after it is created.
 */
public final class PropertyOwnership {

    private final PropertyOwnershipId id;
    private final PropertyId propertyId;
    private final String userId;
    private final OwnershipRelationType relationType;
    private final OwnershipVerificationState verificationState;
    private final LocalDate validFrom;
    private final LocalDate validTo;        // null = no end
    private final Instant createdAt;

    // Invariants: all fields set except validTo, userId not blank, validTo not before validFrom.
    private PropertyOwnership(PropertyOwnershipId id, PropertyId propertyId, String userId,
                              OwnershipRelationType relationType, OwnershipVerificationState verificationState,
                              LocalDate validFrom, LocalDate validTo, Instant createdAt) {
        this.id = required(id, "Ownership id");
        this.propertyId = required(propertyId, "Property id");
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User id is required.");
        }
        this.userId = userId;
        this.relationType = required(relationType, "Relation type");
        this.verificationState = required(verificationState, "Verification state");
        this.validFrom = required(validFrom, "Valid from");
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("Valid to must not be before valid from.");
        }
        this.validTo = validTo;
        this.createdAt = required(createdAt, "Created at");
    }

    // ==================== Factory Methods ====================

    /**
     * The only ownership W8 writes (BR-10): the user is OWNER, UNVERIFIED, valid from the given day, with no end.
     */
    public static PropertyOwnership createUnverifiedOwner(PropertyId propertyId, String userId, LocalDate validFrom) {
        return new PropertyOwnership(PropertyOwnershipId.generate(), propertyId, userId,
                OwnershipRelationType.OWNER, OwnershipVerificationState.UNVERIFIED, validFrom, null,
                AppClock.currentUTC());
    }

    /**
     * Load from database (reconstruct).
     */
    public static PropertyOwnership load(PropertyOwnershipId id, PropertyId propertyId, String userId,
                                         OwnershipRelationType relationType,
                                         OwnershipVerificationState verificationState,
                                         LocalDate validFrom, LocalDate validTo, Instant createdAt) {
        return new PropertyOwnership(id, propertyId, userId, relationType, verificationState, validFrom, validTo,
                createdAt);
    }

    // ==================== Getters ====================

    public PropertyOwnershipId getId() {
        return id;
    }

    public PropertyId getPropertyId() {
        return propertyId;
    }

    public String getUserId() {
        return userId;
    }

    public OwnershipRelationType getRelationType() {
        return relationType;
    }

    public OwnershipVerificationState getVerificationState() {
        return verificationState;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value;
    }
}
