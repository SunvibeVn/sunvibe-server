package vn.sunvibe.property.core.domain.model.amenity;


import java.math.BigDecimal;
import java.util.Objects;

/**
 * Domain Model - AmenityType (Master)
 * Represents an amenity type shared across multiple properties.
 * Example: WiFi, AC, Kitchen, Washing Machine, etc
 * <p>
 * Types form a two-level list: a top-level type (General Amenities, Kitchen, ...) has no parent category, the others
 * name theirs (category id and name, as the seed stores them).
 */
public class AmenityType {

    private final Integer id;  // Database ID (not UUID for master data)
    private final String amenityName;        // Amenity name: "WiFi", "Air Conditioner", etc
    private final Integer categoryId;        // Parent category (an amenity type); null for a top-level type
    private final String categoryName;       // Parent category name; null for a top-level type
    private final String description;        // Detailed description
    private final boolean isStandard;        // Is this a standard amenity?
    private final String levelOfNeed;        // Level of need: STANDARD, PREMIUM, OPTIONAL
    private final BigDecimal estimatedPrice; // Estimated price (for rental calculation)
    private final String icon;               // Icon for UI

    // ==================== Constructor ====================

    private AmenityType(Integer id, String amenityName, Integer categoryId, String categoryName,
                        String description, boolean isStandard, String levelOfNeed,
                        BigDecimal estimatedPrice, String icon) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("AmenityType ID must be > 0");
        }
        if (amenityName == null || amenityName.isBlank()) {
            throw new IllegalArgumentException("AmenityName cannot be null");
        }
        if (levelOfNeed == null || levelOfNeed.isBlank()) {
            throw new IllegalArgumentException("LevelOfNeed cannot be null");
        }

        this.id = id;
        this.amenityName = amenityName;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.description = description;
        this.isStandard = isStandard;
        this.levelOfNeed = levelOfNeed;
        this.estimatedPrice = estimatedPrice;
        this.icon = icon;
    }

    // ==================== Factory Methods ====================

    /**
     * Load from database (reconstruct).
     */
    public static AmenityType load(Integer id, String amenityName, Integer categoryId, String categoryName,
                                   String description, boolean isStandard, String levelOfNeed,
                                   BigDecimal estimatedPrice, String icon) {
        return new AmenityType(id, amenityName, categoryId, categoryName, description, isStandard,
                levelOfNeed, estimatedPrice, icon);
    }

    // ==================== Getters ====================

    public Integer getId() {
        return id;
    }

    public String getAmenityName() {
        return amenityName;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isStandard() {
        return isStandard;
    }

    public String getLevelOfNeed() {
        return levelOfNeed;
    }

    public BigDecimal getEstimatedPrice() {
        return estimatedPrice;
    }

    public String getIcon() {
        return icon;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AmenityType that = (AmenityType) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "AmenityType{" +
                "id=" + id +
                ", amenityName='" + amenityName + '\'' +
                ", category=" + categoryName +
                '}';
    }

}
