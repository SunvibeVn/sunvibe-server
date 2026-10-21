package vn.sunvibe.property.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.domain.model.rule.PropertyRuleApply;
import vn.sunvibe.property.core.domain.model.rule.RuleCategory;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;
import vn.sunvibe.property.core.domain.model.vo.RuleCategoryRef;
import vn.sunvibe.property.core.infrastructure.persistence.entity.PropertyRuleEntity;

@Component
public class PropertyRuleEntityMapper {

    public PropertyRuleApply toDomain(PropertyRuleEntity entity) {
        if (entity == null) return null;

        // The rule row keeps the category's name, description and the effective severity; they are rebuilt as a
        // reference, not as master data
        RuleCategoryRef ruleCategory = RuleCategoryRef.of(entity.getRuleCategoryId(), entity.getCategory(),
                entity.getDescription(), RuleCategory.RuleSeverity.valueOf(entity.getSeverity()));
        return PropertyRuleApply.load(
                entity.getId(),
                PropertyId.of(entity.getPropertyId()),
                ruleCategory,
                entity.getSeverity(),
                entity.getCustomNote(),
                entity.getIsMandatory(),
                entity.getCreatedAt()
        );
    }

    public PropertyRuleEntity toEntity(PropertyRuleApply domain) {
        if (domain == null) return null;

        RuleCategoryRef ruleCategory = domain.getRuleCategory();

        return PropertyRuleEntity.builder()
                .id(domain.getId())
                .propertyId(domain.getPropertyId().getValue())
                .ruleCategoryId(domain.getRuleCategoryId())
                .ruleName(ruleCategory.getName())
                .description(ruleCategory.getDescription())
                .category(ruleCategory.getName())
                .severity(domain.getEffectiveSeverity())
                .isMandatory(domain.getIsMandatory())
                .customNote(domain.getCustomNote())
                .createdAt(domain.getCreatedAt())
                .build();
    }
}