package vn.sunvibe.property.core.domain.model.rule;

import lombok.Data;
import lombok.NoArgsConstructor;
import vn.sunvibe.shared.time.AppClock;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain Model - PropertyRuleApply
 * <p>
 * Represents the relationship between Property and RuleCategory.
 * Allows overriding severity and adding custom notes per property.
 * <p>
 * Relationship: Property (1) ──M── PropertyRuleApply ──N── RuleCategory
 */
@Data
@NoArgsConstructor
public class PropertyRuleApply {

    private String id;                    // Unique ID for this relationship
    private PropertyId propertyId;        // FK to property
    private Integer ruleCategoryId;       // FK to rule category (master)

    private RuleCategory ruleCategory;    // Reference to master (loaded separately)

    private String severity;              // Override severity if needed (nullable)
    private String customNote;            // Custom note for this rule
    private Boolean isMandatory;          // Override mandatory flag (nullable)

    private LocalDateTime createdAt;

    // ==================== Constructor ====================

    public PropertyRuleApply(PropertyId propertyId, Integer ruleCategoryId,
                             RuleCategory ruleCategory) {
        if (propertyId == null) {
            throw new IllegalArgumentException("PropertyId cannot be null");
        }
        if (ruleCategoryId == null || ruleCategoryId <= 0) {
            throw new IllegalArgumentException("RuleCategoryId must be > 0");
        }
        if (ruleCategory == null) {
            throw new IllegalArgumentException("RuleCategory cannot be null");
        }
        if (!ruleCategoryId.equals(ruleCategory.getId())) {
            throw new IllegalArgumentException("RuleCategoryId does not match RuleCategory.id");
        }

        this.id = UUID.randomUUID().toString();
        this.propertyId = propertyId;
        this.ruleCategoryId = ruleCategoryId;
        this.ruleCategory = ruleCategory;
        this.createdAt = AppClock.currentLocalTime();
    }

    // ==================== Factory Methods ====================

    /**
     * Create a rule apply with default settings from RuleCategory.
     */
    public static PropertyRuleApply createDefault(PropertyId propertyId,
                                                  RuleCategory ruleCategory) {
        return new PropertyRuleApply(propertyId, ruleCategory.getId(), ruleCategory);
    }

    /**
     * Create a rule apply with an overridden severity.
     */
    public static PropertyRuleApply createWithOverride(PropertyId propertyId,
                                                       RuleCategory ruleCategory,
                                                       String overrideSeverity) {
        PropertyRuleApply apply = new PropertyRuleApply(propertyId, ruleCategory.getId(), ruleCategory);
        apply.setSeverity(overrideSeverity);
        return apply;
    }

    /**
     * Load from database (reconstruct).
     */
    public static PropertyRuleApply load(String id, PropertyId propertyId,
                                         Integer ruleCategoryId,
                                         RuleCategory ruleCategory,
                                         String severity, String customNote,
                                         Boolean isMandatory,
                                         LocalDateTime createdAt) {
        PropertyRuleApply apply = new PropertyRuleApply(propertyId, ruleCategoryId, ruleCategory);
        apply.id = id;
        apply.severity = severity;
        apply.customNote = customNote;
        apply.isMandatory = isMandatory;
        apply.createdAt = createdAt;
        return apply;
    }

    // ==================== Business Methods ====================

    /**
     * Get effective severity - uses override if set, otherwise uses the default.
     */
    public String getEffectiveSeverity() {
        if (severity != null) {
            return severity;
        }
        return ruleCategory.getDefaultSeverity().name();
    }

    /**
     * Get effective mandatory flag - uses override if set, otherwise uses the default.
     */
    public boolean isEffectivelyMandatory() {
        if (isMandatory != null) {
            return isMandatory;
        }
        // Default is true when no override is set
        return true;
    }

    /**
     * Override severity for this rule (relative to property).
     */
    public void setSeverity(String severity) {
        if (severity != null && !severity.isBlank()) {
            this.severity = severity;
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

    /**
     * Override mandatory flag
     */
    public void setMandatory(Boolean mandatory) {
        this.isMandatory = mandatory;
    }

    /**
     * Clear override - revert to default
     */
    public void clearOverride() {
        this.severity = null;
        this.isMandatory = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PropertyRuleApply that = (PropertyRuleApply) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "PropertyRuleApply{" +
                "id='" + id + '\'' +
                ", propertyId=" + propertyId +
                ", ruleCategoryId=" + ruleCategoryId +
                ", categoryName='" + ruleCategory.getCategoryName() + '\'' +
                ", severity='" + getEffectiveSeverity() + '\'' +
                '}';
    }
}
