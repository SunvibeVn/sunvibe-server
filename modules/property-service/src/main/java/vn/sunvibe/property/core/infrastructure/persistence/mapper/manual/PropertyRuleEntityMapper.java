package vn.sunvibe.property.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.domain.model.rule.PropertyRuleApply;
import vn.sunvibe.property.core.domain.model.rule.RuleCategory;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;
import vn.sunvibe.property.core.infrastructure.persistence.entity.PropertyRuleEntity;

@Component
public class PropertyRuleEntityMapper {

    public PropertyRuleApply toDomain(PropertyRuleEntity entity) {
        if (entity == null) return null;

        // Reconstitution path: use no-args + setters to bypass constructor validation.
        // The constructor enforces invariants for new objects only.
        RuleCategory ruleCategory = new RuleCategory();
        ruleCategory.setId(entity.getRuleCategoryId());
        ruleCategory.setCategoryName(entity.getCategory());
        ruleCategory.setDescription(entity.getDescription());
        if (entity.getSeverity() != null) {
            ruleCategory.setDefaultSeverity(RuleCategory.RuleSeverity.valueOf(entity.getSeverity()));
        }

        PropertyRuleApply domain = new PropertyRuleApply();
        domain.setId(entity.getId());
        domain.setPropertyId(PropertyId.of(entity.getPropertyId()));
        domain.setRuleCategoryId(entity.getRuleCategoryId());
        domain.setRuleCategory(ruleCategory);
        domain.setSeverity(entity.getSeverity());
        domain.setCustomNote(entity.getCustomNote());
        domain.setMandatory(entity.getIsMandatory());
        domain.setCreatedAt(entity.getCreatedAt());
        return domain;
    }

    public PropertyRuleEntity toEntity(PropertyRuleApply domain) {
        if (domain == null) return null;

        RuleCategory ruleCategory = domain.getRuleCategory();

        return PropertyRuleEntity.builder()
                .id(domain.getId())
                .propertyId(domain.getPropertyId().getValue())
                .ruleCategoryId(domain.getRuleCategoryId())
                .ruleName(ruleCategory != null ? ruleCategory.getCategoryName() : null)
                .description(ruleCategory != null ? ruleCategory.getDescription() : null)
                .category(ruleCategory != null ? ruleCategory.getCategoryName() : null)
                .severity(domain.getEffectiveSeverity())
                .isMandatory(domain.getIsMandatory())
                .customNote(domain.getCustomNote())
                .createdAt(domain.getCreatedAt())
                .build();
    }
}