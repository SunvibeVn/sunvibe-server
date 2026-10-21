package vn.sunvibe.property.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.domain.model.amenity.AmenityType;
import vn.sunvibe.property.core.infrastructure.persistence.entity.AmenityTypeEntity;

@Component
public class AmenityTypeEntityMapper {

    public AmenityType toDomain(AmenityTypeEntity entity) {
        if (entity == null) return null;

        return AmenityType.load(
                entity.getId(),
                entity.getAmenityName(),
                entity.getCategoryId(),
                entity.getCategoryName(),
                entity.getDescription(),
                Boolean.TRUE.equals(entity.getIsStandard()),
                entity.getLevelOfNeed(),
                entity.getEstimatedPrice(),
                entity.getIcon()
        );
    }

    public AmenityTypeEntity toEntity(AmenityType domain) {
        if (domain == null) return null;

        return AmenityTypeEntity.builder()
                .id(domain.getId())
                .amenityName(domain.getAmenityName())
                .categoryId(domain.getCategoryId())
                .categoryName(domain.getCategoryName())
                .description(domain.getDescription())
                .isStandard(domain.isStandard())
                .levelOfNeed(domain.getLevelOfNeed())
                .estimatedPrice(domain.getEstimatedPrice())
                .icon(domain.getIcon())
                .build();
    }
}