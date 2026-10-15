package vn.sunvibe.property.core.domain.model.amenity;


import lombok.Data;
import lombok.NoArgsConstructor;
import vn.sunvibe.shared.time.AppClock;
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
@Data
@NoArgsConstructor
public class PropertyAmenityApply {

    private String id;                     // Unique ID for this relationship
    private PropertyId propertyId;         // FK to property
    private Integer amenityTypeId;         // FK to amenity type (master)

    private AmenityType amenityType;       // Reference to master (loaded separately)

    private boolean isIncluded;            // Whether this amenity is included
    private String levelOfNeed;            // Level of need: STANDARD, PREMIUM, OPTIONAL
    private String customNote;             // Custom note for this amenity

    private LocalDateTime createdAt;

    // ==================== Constructor ====================

    public PropertyAmenityApply(PropertyId propertyId, Integer amenityTypeId,
                                AmenityType amenityType, boolean isIncluded) {
        if (propertyId == null) {
            throw new IllegalArgumentException("PropertyId cannot be null");
        }
        if (amenityTypeId == null || amenityTypeId <= 0) {
            throw new IllegalArgumentException("AmenityTypeId must be > 0");
        }
        if (amenityType == null) {
            throw new IllegalArgumentException("AmenityType cannot be null");
        }
        if (!amenityTypeId.equals(amenityType.getId())) {
            throw new IllegalArgumentException("AmenityTypeId does not match AmenityType.id");
        }

        this.id = UUID.randomUUID().toString();
        this.propertyId = propertyId;
        this.amenityTypeId = amenityTypeId;
        this.amenityType = amenityType;
        this.isIncluded = isIncluded;
        this.levelOfNeed = amenityType.getLevelOfNeed();  // Default from master
        this.createdAt = AppClock.currentLocalTime();
    }

    // ==================== Factory Methods ====================

    /**
     * Create amenity apply - NOT included.
     */
    public static PropertyAmenityApply notIncluded(PropertyId propertyId,
                                                   AmenityType amenityType) {
        return new PropertyAmenityApply(propertyId, amenityType.getId(), amenityType, false);
    }

    /**
     * Create amenity apply - included.
     */
    public static PropertyAmenityApply included(PropertyId propertyId,
                                                AmenityType amenityType) {
        return new PropertyAmenityApply(propertyId, amenityType.getId(), amenityType, true);
    }

    /**
     * Load from database (reconstruct).
     */
    public static PropertyAmenityApply load(String id, PropertyId propertyId,
                                            Integer amenityTypeId,
                                            AmenityType amenityType,
                                            boolean isIncluded,
                                            String levelOfNeed,
                                            String customNote,
                                            LocalDateTime createdAt) {
        PropertyAmenityApply apply = new PropertyAmenityApply(propertyId, amenityTypeId, amenityType, isIncluded);
        apply.id = id;
        apply.levelOfNeed = levelOfNeed;
        apply.customNote = customNote;
        apply.createdAt = createdAt;
        return apply;
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
                ", amenityTypeId=" + amenityTypeId +
                ", amenityName='" + amenityType.getAmenityName() + '\'' +
                ", isIncluded=" + isIncluded +
                '}';
    }
}
