package vn.sunvibe.property.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.domain.model.rule.RuleCategory;
import vn.sunvibe.property.core.infrastructure.persistence.entity.RuleCategoryEntity;

@Component
public class RuleCategoryEntityMapper {

    public RuleCategory toDomain(RuleCategoryEntity entity) {
        if (entity == null) return null;
        return RuleCategory.load(
                entity.getId(),
                entity.getCategoryName(),
                entity.getDescription(),
                RuleCategory.RuleSeverity.valueOf(entity.getDefaultSeverity()),
                Boolean.TRUE.equals(entity.getIsNegotiable()),
                entity.getIcon()
        );
    }

    public RuleCategoryEntity toEntity(RuleCategory domain) {
        if (domain == null) return null;
        return RuleCategoryEntity.builder()
                .id(domain.getId())
                .categoryName(domain.getCategoryName())
                .description(domain.getDescription())
                .defaultSeverity(domain.getDefaultSeverity().name())
                .isNegotiable(domain.isNegotiable())
                .icon(domain.getIcon())
                .build();
    }
}