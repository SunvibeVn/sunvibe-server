package vn.sunvibe.property.core.domain.model.unit;

import vn.sunvibe.property.core.domain.model.aggregate.Property;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;
import vn.sunvibe.shared.time.AppClock;

import java.time.Instant;

/**
 * Domain Model - Unit (Aggregate Root)
 * <p>
 * The part of a property that is let or sold (property-units BR-2). For now every property has exactly one unit,
 * the canonical (default) unit (property-units BR-3); later features add more. A unit refers to its property by id
 * only. A unit is immutable: for now nothing changes it once it is created.
 */
public final class Unit {

    private final UnitId id;
    private final PropertyId propertyId;
    private final String unitCode;
    private final UnitType unitType;
    private final int bedrooms;
    private final int bathrooms;
    private final double areaSqm;
    private final UnitAvailabilityState availabilityState;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Long version;             // null until stored; optimistic lock (property-units BR-14)

    // Invariants (property-units spec A-7): reference fields set, code not blank, rooms >= 0, area > 0. Version may be null.
    private Unit(UnitId id, PropertyId propertyId, String unitCode, UnitType unitType, int bedrooms, int bathrooms,
                 double areaSqm, UnitAvailabilityState availabilityState, Instant createdAt, Instant updatedAt,
                 Long version) {
        this.id = required(id, "Unit id");
        this.propertyId = required(propertyId, "Property id");
        if (unitCode == null || unitCode.isBlank()) {
            throw new IllegalArgumentException("Unit code is required.");
        }
        this.unitCode = unitCode;
        this.unitType = required(unitType, "Unit type");
        if (bedrooms < 0) {
            throw new IllegalArgumentException("Bedrooms must be >= 0.");
        }
        this.bedrooms = bedrooms;
        if (bathrooms < 0) {
            throw new IllegalArgumentException("Bathrooms must be >= 0.");
        }
        this.bathrooms = bathrooms;
        if (!(areaSqm > 0)) {               // written this way so that NaN is refused too
            throw new IllegalArgumentException("Area must be > 0.");
        }
        this.areaSqm = areaSqm;
        this.availabilityState = required(availabilityState, "Availability state");
        this.createdAt = required(createdAt, "Created at");
        this.updatedAt = required(updatedAt, "Updated at");
        this.version = version;
    }

    // ==================== Factory Methods ====================

    /**
     * The canonical unit of a new property (property-units BR-4, BR-5, BR-8): it takes the property's code, type,
     * rooms and area, starts AVAILABLE and has no version until it is stored.
     */
    public static Unit createCanonical(Property property) {
        required(property, "Property");
        Instant now = AppClock.currentUTC();
        return new Unit(UnitId.generate(), property.getId(), property.getPropertyCode().getValue(),
                UnitType.from(property.getType()), property.getBedrooms(), property.getBathrooms(),
                property.getSquareMeters(), UnitAvailabilityState.AVAILABLE, now, now, null);
    }

    /**
     * Load from database (reconstruct).
     */
    public static Unit load(UnitId id, PropertyId propertyId, String unitCode, UnitType unitType, int bedrooms,
                            int bathrooms, double areaSqm, UnitAvailabilityState availabilityState,
                            Instant createdAt, Instant updatedAt, Long version) {
        return new Unit(id, propertyId, unitCode, unitType, bedrooms, bathrooms, areaSqm, availabilityState,
                createdAt, updatedAt, version);
    }

    // ==================== Getters ====================

    public UnitId getId() {
        return id;
    }

    public PropertyId getPropertyId() {
        return propertyId;
    }

    public String getUnitCode() {
        return unitCode;
    }

    public UnitType getUnitType() {
        return unitType;
    }

    public int getBedrooms() {
        return bedrooms;
    }

    public int getBathrooms() {
        return bathrooms;
    }

    public double getAreaSqm() {
        return areaSqm;
    }

    public UnitAvailabilityState getAvailabilityState() {
        return availabilityState;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value;
    }
}
