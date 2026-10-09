package vn.sunvibe.property.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.domain.model.amenity.AmenityType;
import vn.sunvibe.property.core.infrastructure.persistence.entity.AmenityTypeEntity;

@Component
public class AmenityTypeEntityMapper {

    public AmenityType toDomain(AmenityTypeEntity entity) {
        if (entity == null) return null;

        // AmenityType uses itself as its category (parent reference).
        // Reconstruct via setters to bypass constructor validation on the reconstitution path.
        AmenityType category = new AmenityType();
        category.setId(entity.getCategoryId());
        category.setAmenityName(entity.getCategoryName());

        AmenityType domain = new AmenityType();
        domain.setId(entity.getId());
        domain.setAmenityName(entity.getAmenityName());
        domain.setAmenityType(category);
        domain.setDescription(entity.getDescription());
        domain.setStandard(Boolean.TRUE.equals(entity.getIsStandard()));
        domain.setLevelOfNeed(entity.getLevelOfNeed());
        domain.setEstimatedPrice(entity.getEstimatedPrice());
        domain.setIcon(entity.getIcon());
        return domain;
    }

    public AmenityTypeEntity toEntity(AmenityType domain) {
        if (domain == null) return null;

        AmenityType category = domain.getAmenityType();
        return AmenityTypeEntity.builder()
                .id(domain.getId())
                .amenityName(domain.getAmenityName())
                .categoryId(category != null ? category.getId() : null)
                .categoryName(category != null ? category.getAmenityName() : null)
                .description(domain.getDescription())
                .isStandard(domain.isStandard())
                .levelOfNeed(domain.getLevelOfNeed())
                .estimatedPrice(domain.getEstimatedPrice())
                .icon(domain.getIcon())
                .build();
    }
}