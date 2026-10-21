package vn.sunvibe.property.core.domain.model.vo;

import vn.sunvibe.property.core.domain.model.rule.RuleCategory.RuleSeverity;

import java.util.Objects;

/**
 * The rule category a property's rule points to, with the values the rule row keeps (name, description, severity).
 * It is a reference, not the master data itself: the master row may change later.
 */
public class RuleCategoryRef extends ValueObject<RuleCategoryRef> {

    private final Integer id;            // null only for a stored row without its master id
    private final String name;
    private final String description;
    private final RuleSeverity defaultSeverity;

    private RuleCategoryRef(Integer id, String name, String description, RuleSeverity defaultSeverity) {
        // A stored row may have no master id (the column is nullable, without a foreign key); a new one always has
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("RuleCategoryId must be > 0");
        }
        if (defaultSeverity == null) {
            throw new IllegalArgumentException("DefaultSeverity cannot be null");
        }
        this.id = id;
        this.name = name;
        this.description = description;
        this.defaultSeverity = defaultSeverity;
    }

    public static RuleCategoryRef of(Integer id, String name, String description, RuleSeverity defaultSeverity) {
        return new RuleCategoryRef(id, name, description, defaultSeverity);
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public RuleSeverity getDefaultSeverity() {
        return defaultSeverity;
    }

    @Override
    protected boolean equalsCore(RuleCategoryRef other) {
        return Objects.equals(id, other.id) && Objects.equals(name, other.name)
                && Objects.equals(description, other.description) && defaultSeverity == other.defaultSeverity;
    }

    @Override
    protected int hashCodeCore() {
        return Objects.hash(id, name, description, defaultSeverity);
    }

    @Override
    public String toString() {
        return "RuleCategoryRef{id=" + id + ", name='" + name + "'}";
    }
}
