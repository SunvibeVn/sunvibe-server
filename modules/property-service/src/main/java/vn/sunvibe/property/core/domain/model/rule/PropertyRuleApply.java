package vn.sunvibe.property.core.domain.model.rule;

import vn.sunvibe.shared.time.AppClock;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;
import vn.sunvibe.property.core.domain.model.vo.RuleCategoryRef;

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
public class PropertyRuleApply {

    private String id;                            // Unique ID for this relationship
    private final PropertyId propertyId;          // FK to property
    private final RuleCategoryRef ruleCategory;   // The rule category (master), with the values this row keeps

    private String severity;              // Override severity if needed (nullable)
    private String customNote;            // Custom note for this rule
    private Boolean isMandatory;          // Override mandatory flag (nullable)

    private LocalDateTime createdAt;

    // ==================== Constructor ====================

    private PropertyRuleApply(PropertyId propertyId, RuleCategoryRef ruleCategory) {
        if (propertyId == null) {
            throw new IllegalArgumentException("PropertyId cannot be null");
        }
        if (ruleCategory == null) {
            throw new IllegalArgumentException("RuleCategory cannot be null");
        }

        this.id = UUID.randomUUID().toString();
        this.propertyId = propertyId;
        this.ruleCategory = ruleCategory;
        this.createdAt = AppClock.currentLocalTime();
    }

    // ==================== Factory Methods ====================

    /**
     * Create a rule apply with default settings from RuleCategory.
     */
    public static PropertyRuleApply createDefault(PropertyId propertyId, RuleCategory ruleCategory) {
        return new PropertyRuleApply(propertyId, reference(ruleCategory));
    }

    /**
     * Create a rule apply with an overridden severity.
     */
    public static PropertyRuleApply createWithOverride(PropertyId propertyId, RuleCategory ruleCategory,
                                                       String overrideSeverity) {
        PropertyRuleApply apply = new PropertyRuleApply(propertyId, reference(ruleCategory));
        apply.setSeverity(overrideSeverity);
        return apply;
    }

    /**
     * Load from database (reconstruct).
     */
    public static PropertyRuleApply load(String id, PropertyId propertyId, RuleCategoryRef ruleCategory,
                                         String severity, String customNote, Boolean isMandatory,
                                         LocalDateTime createdAt) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Id cannot be blank");
        PropertyRuleApply apply = new PropertyRuleApply(propertyId, ruleCategory);
        apply.id = id;
        apply.severity = severity;
        apply.customNote = customNote;
        apply.isMandatory = isMandatory;
        apply.createdAt = createdAt;
        return apply;
    }

    private static RuleCategoryRef reference(RuleCategory ruleCategory) {
        if (ruleCategory == null) {
            throw new IllegalArgumentException("RuleCategory cannot be null");
        }
        return RuleCategoryRef.of(ruleCategory.getId(), ruleCategory.getCategoryName(),
                ruleCategory.getDescription(), ruleCategory.getDefaultSeverity());
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

    // ==================== Getters ====================

    public String getId() {
        return id;
    }

    public PropertyId getPropertyId() {
        return propertyId;
    }

    public Integer getRuleCategoryId() {
        return ruleCategory.getId();
    }

    public RuleCategoryRef getRuleCategory() {
        return ruleCategory;
    }

    public String getSeverity() {
        return severity;
    }

    public String getCustomNote() {
        return customNote;
    }

    public Boolean getIsMandatory() {
        return isMandatory;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
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
                ", ruleCategoryId=" + ruleCategory.getId() +
                ", categoryName='" + ruleCategory.getName() + '\'' +
                ", severity='" + getEffectiveSeverity() + '\'' +
                '}';
    }
}
