package vn.sunvibe.property.core.domain.model.amenity;


import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Domain Model - AmenityType (Master)
 * Represents an amenity type shared across multiple properties.
 * Example: WiFi, AC, Kitchen, Washing Machine, etc
 */
@Data
@NoArgsConstructor
public class AmenityType {

    private Integer id;  // Database ID (not UUID for master data)
    private String amenityName;        // Amenity name: "WiFi", "Air Conditioner", etc
    private AmenityType amenityType;  // Type: KITCHEN, BEDROOM, etc
    private String description;        // Detailed description
    private boolean isStandard;        // Is this a standard amenity?
    private String levelOfNeed;        // Level of need: STANDARD, PREMIUM, OPTIONAL
    private BigDecimal estimatedPrice; // Estimated price (for rental calculation)
    private String icon;               // Icon for UI

    // ==================== Constructor ====================

    public AmenityType(Integer id, String amenityName, AmenityType amenityType,
                       String description, boolean isStandard, String levelOfNeed,
                       BigDecimal estimatedPrice, String icon) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("AmenityType ID must be > 0");
        }
        if (amenityName == null || amenityName.isBlank()) {
            throw new IllegalArgumentException("AmenityName cannot be null");
        }
        if (amenityType == null) {
            throw new IllegalArgumentException("Category cannot be null");
        }
        if (levelOfNeed == null || levelOfNeed.isBlank()) {
            throw new IllegalArgumentException("LevelOfNeed cannot be null");
        }

        this.id = id;
        this.amenityName = amenityName;
        this.amenityType = amenityType;
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
    public static AmenityType load(Integer id, String amenityName, AmenityType category,
                                   String description, boolean isStandard, String levelOfNeed,
                                   BigDecimal estimatedPrice, String icon) {
        return new AmenityType(id, amenityName, category, description, isStandard,
                levelOfNeed, estimatedPrice, icon);
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
                ", category=" + amenityType +
                '}';
    }

}
