package vn.sunvibe.property.core.domain.model.amenity;


import vn.sunvibe.shared.time.AppClock;
import vn.sunvibe.property.core.domain.model.vo.AmenityTypeRef;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain Model - PropertyAmenityApply
 * <p>
 * Represents the relationship between Property and AmenityType.
 * Allows specifying whether an amenity is included, its level of need, and custom notes.
 * <p>
 * Relationship: Property (1) ──M── PropertyAmenityApply ──N── AmenityType
 */
public class PropertyAmenityApply {

    private String id;                           // Unique ID for this relationship
    private final PropertyId propertyId;         // FK to property
    private final AmenityTypeRef amenityType;    // The amenity type (master), with the names this row keeps

    private boolean isIncluded;                  // Whether this amenity is included
    private String levelOfNeed;                  // Level of need: STANDARD, PREMIUM, OPTIONAL
    private String customNote;                   // Custom note for this amenity

    private LocalDateTime createdAt;

    // ==================== Constructor ====================

    private PropertyAmenityApply(PropertyId propertyId, AmenityTypeRef amenityType, boolean isIncluded,
                                 String levelOfNeed) {
        if (propertyId == null) {
            throw new IllegalArgumentException("PropertyId cannot be null");
        }
        if (amenityType == null) {
            throw new IllegalArgumentException("AmenityType cannot be null");
        }

        this.id = UUID.randomUUID().toString();
        this.propertyId = propertyId;
        this.amenityType = amenityType;
        this.isIncluded = isIncluded;
        this.levelOfNeed = levelOfNeed;
        this.createdAt = AppClock.currentLocalTime();
    }

    // ==================== Factory Methods ====================

    /**
     * Create amenity apply - NOT included.
     */
    public static PropertyAmenityApply notIncluded(PropertyId propertyId, AmenityType amenityType) {
        return fromMaster(propertyId, amenityType, false);
    }

    /**
     * Create amenity apply - included.
     */
    public static PropertyAmenityApply included(PropertyId propertyId, AmenityType amenityType) {
        return fromMaster(propertyId, amenityType, true);
    }

    /**
     * Load from database (reconstruct).
     */
    public static PropertyAmenityApply load(String id, PropertyId propertyId, AmenityTypeRef amenityType,
                                            boolean isIncluded, String levelOfNeed, String customNote,
                                            LocalDateTime createdAt) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Id cannot be blank");
        PropertyAmenityApply apply = new PropertyAmenityApply(propertyId, amenityType, isIncluded, levelOfNeed);
        apply.id = id;
        apply.customNote = customNote;
        apply.createdAt = createdAt;
        return apply;
    }

    /** The level of need starts as the master's. */
    private static PropertyAmenityApply fromMaster(PropertyId propertyId, AmenityType amenityType,
                                                   boolean isIncluded) {
        if (amenityType == null) {
            throw new IllegalArgumentException("AmenityType cannot be null");
        }
        AmenityTypeRef reference = AmenityTypeRef.of(amenityType.getId(), amenityType.getAmenityName(),
                amenityType.getCategoryName(), amenityType.getDescription());
        return new PropertyAmenityApply(propertyId, reference, isIncluded, amenityType.getLevelOfNeed());
    }

    // ==================== Business Methods ====================

    /**
     * Include this amenity.
     */
    public void include() {
        this.isIncluded = true;
    }

    /**
     * Exclude this amenity.
     */
    public void exclude() {
        this.isIncluded = false;
    }

    /**
     * Set the level of need.
     */
    public void setLevelOfNeed(String level) {
        if (level != null && !level.isBlank()) {
            this.levelOfNeed = level;
        }
    }

    /**
     * Add a custom note.
     */
    public void addCustomNote(String note) {
        if (note != null && !note.isBlank()) {
            this.customNote = note;
        }
    }

    // ==================== Getters ====================

    public String getId() {
        return id;
    }

    public PropertyId getPropertyId() {
        return propertyId;
    }

    public Integer getAmenityTypeId() {
        return amenityType.getId();
    }

    public AmenityTypeRef getAmenityType() {
        return amenityType;
    }

    public boolean isIncluded() {
        return isIncluded;
    }

    public String getLevelOfNeed() {
        return levelOfNeed;
    }

    public String getCustomNote() {
        return customNote;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PropertyAmenityApply that = (PropertyAmenityApply) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "PropertyAmenityApply{" +
                "id='" + id + '\'' +
                ", propertyId=" + propertyId +
                ", amenityTypeId=" + amenityType.getId() +
                ", amenityName='" + amenityType.getName() + '\'' +
                ", isIncluded=" + isIncluded +
                '}';
    }
}
