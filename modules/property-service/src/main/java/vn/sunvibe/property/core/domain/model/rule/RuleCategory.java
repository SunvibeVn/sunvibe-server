package vn.sunvibe.property.core.domain.model.rule;


import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * Domain Model - RuleCategory (Master)
 * Represents a rule type shared across multiple properties.
 * Example: PETS, SMOKING, NOISE, GUESTS, PARTIES, etc
 */
@Data
@NoArgsConstructor
public class RuleCategory {

    private Integer id;  // Database ID (not UUID for master data)
    private String categoryName;        // Category name: "PETS", "SMOKING", etc
    private String description;        // Description: e.g. "No pets allowed"
    private RuleSeverity defaultSeverity;  // Default severity level
    private boolean isNegotiable;      // Can this rule be negotiated?
    private String icon;               // Icon for UI

    // ==================== Constructor ====================

    public RuleCategory(Integer id, String categoryName, String description,
                        RuleSeverity defaultSeverity, boolean isNegotiable,
                        String icon) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("RuleCategory ID must be > 0");
        }
        if (categoryName == null || categoryName.isBlank()) {
            throw new IllegalArgumentException("CategoryName cannot be null");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description cannot be null");
        }
        if (defaultSeverity == null) {
            throw new IllegalArgumentException("DefaultSeverity cannot be null");
        }

        this.id = id;
        this.categoryName = categoryName;
        this.description = description;
        this.defaultSeverity = defaultSeverity;
        this.isNegotiable = isNegotiable;
        this.icon = icon;
    }

    // ==================== Factory Methods ====================

    /**
     * Load from database (reconstruct).
     */
    public static RuleCategory load(Integer id, String categoryName, String description,
                                    RuleSeverity defaultSeverity, boolean isNegotiable,
                                    String icon) {
        return new RuleCategory(id, categoryName, description, defaultSeverity,
                isNegotiable, icon);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RuleCategory that = (RuleCategory) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RuleCategory{" +
                "id=" + id +
                ", categoryName='" + categoryName + '\'' +
                ", description='" + description + '\'' +
                ", defaultSeverity=" + defaultSeverity +
                '}';
    }

    // ==================== Enum RuleSeverity ====================

    public enum RuleSeverity {


        LOW("Low"),
        MEDIUM("Medium"),
        HIGH("High"),
        CRITICAL("Critical");

        private final String displayName;

        RuleSeverity(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
