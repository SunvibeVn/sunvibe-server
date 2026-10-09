package vn.sunvibe.property.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.domain.model.amenity.AmenityType;
import vn.sunvibe.property.core.domain.model.amenity.PropertyAmenityApply;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;
import vn.sunvibe.property.core.infrastructure.persistence.entity.PropertyAmenityEntity;

@Component
public class PropertyAmenityEntityMapper {

    public PropertyAmenityApply toDomain(PropertyAmenityEntity entity) {
        if (entity == null) return null;

        // Reconstitution path: use no-args + setters to bypass constructor validation.
        // The constructor enforces invariants for new objects only.
        AmenityType categoryStub = new AmenityType();
        categoryStub.setAmenityName(entity.getCategory());

        AmenityType amenityType = new AmenityType();
        amenityType.setId(entity.getAmenityTypeId());
        amenityType.setAmenityName(entity.getAmenityName());
        amenityType.setAmenityType(categoryStub);
        amenityType.setDescription(entity.getDescription());
        amenityType.setLevelOfNeed(entity.getLevelOfNeed());

        PropertyAmenityApply domain = new PropertyAmenityApply();
        domain.setId(entity.getId());
        domain.setPropertyId(PropertyId.of(entity.getPropertyId()));
        domain.setAmenityTypeId(entity.getAmenityTypeId());
        domain.setAmenityType(amenityType);
        domain.setIncluded(Boolean.TRUE.equals(entity.getIsIncluded()));
        domain.setLevelOfNeed(entity.getLevelOfNeed());
        domain.setCustomNote(entity.getCustomNote());
        domain.setCreatedAt(entity.getCreatedAt());
        return domain;
    }

    public PropertyAmenityEntity toEntity(PropertyAmenityApply domain) {
        if (domain == null) return null;

        AmenityType amenityType = domain.getAmenityType();
        AmenityType category = amenityType != null ? amenityType.getAmenityType() : null;

        return PropertyAmenityEntity.builder()
                .id(domain.getId())
                .propertyId(domain.getPropertyId().getValue())
                .amenityTypeId(domain.getAmenityTypeId())
                .amenityName(amenityType != null ? amenityType.getAmenityName() : null)
                .category(category != null ? category.getAmenityName() : null)
                .description(amenityType != null ? amenityType.getDescription() : null)
                .isIncluded(domain.isIncluded())
                .levelOfNeed(domain.getLevelOfNeed())
                .customNote(domain.getCustomNote())
                .createdAt(domain.getCreatedAt())
                .build();
    }
}