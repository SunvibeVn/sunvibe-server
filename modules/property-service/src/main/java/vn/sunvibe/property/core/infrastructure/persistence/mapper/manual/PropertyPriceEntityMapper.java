package vn.sunvibe.property.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.domain.model.price.PropertyPrice;
import vn.sunvibe.property.core.domain.model.vo.Money;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;
import vn.sunvibe.property.core.infrastructure.persistence.entity.PropertyPriceEntity;

/**
 * Mapper - PropertyPriceEntityMapper
 * Converts between PropertyPrice (Domain) and PropertyPriceEntity (Entity).
 */
@Component
public class PropertyPriceEntityMapper {

    /**
     * Entity → Domain
     */
    public PropertyPrice toDomain(PropertyPriceEntity entity) {
        if (entity == null) {
            return null;
        }

        PropertyId propertyId = PropertyId.of(entity.getPropertyId());
        Money monthlyPrice = Money.vnd(entity.getMonthlyPrice());
        Money depositPrice = Money.vnd(entity.getDepositPrice());

        return PropertyPrice.load(
                entity.getId(),
                propertyId,
                monthlyPrice,
                depositPrice,
                entity.getEffectiveDate(),
                entity.getEndDate(),
                entity.getReason(),
                entity.getIsCurrent() != null && entity.getIsCurrent()
        );
    }

    /**
     * Domain → Entity
     */
    public PropertyPriceEntity toEntity(PropertyPrice domain) {
        if (domain == null) {
            return null;
        }

        return PropertyPriceEntity.builder()
                .id(domain.getId())
                .propertyId(domain.getPropertyId().getValue())
                .monthlyPrice(domain.getMonthlyPrice().getAmount())
                .depositPrice(domain.getDepositPrice().getAmount())
                .effectiveDate(domain.getEffectiveDate())
                .endDate(domain.getEndDate())
                .reason(domain.getReason())
                .isCurrent(domain.isCurrent())
                .build();
    }

}
