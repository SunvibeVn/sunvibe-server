package vn.sunvibe.property.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.domain.model.amenity.PropertyAmenityApply;
import vn.sunvibe.property.core.domain.model.vo.AmenityTypeRef;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;
import vn.sunvibe.property.core.infrastructure.persistence.entity.PropertyAmenityEntity;

@Component
public class PropertyAmenityEntityMapper {

    public PropertyAmenityApply toDomain(PropertyAmenityEntity entity) {
        if (entity == null) return null;

        // The amenity row keeps the type's names; they are rebuilt as a reference, not as master data
        AmenityTypeRef amenityType = AmenityTypeRef.of(entity.getAmenityTypeId(), entity.getAmenityName(),
                entity.getCategory(), entity.getDescription());
        return PropertyAmenityApply.load(
                entity.getId(),
                PropertyId.of(entity.getPropertyId()),
                amenityType,
                Boolean.TRUE.equals(entity.getIsIncluded()),
                entity.getLevelOfNeed(),
                entity.getCustomNote(),
                entity.getCreatedAt()
        );
    }

    public PropertyAmenityEntity toEntity(PropertyAmenityApply domain) {
        if (domain == null) return null;

        AmenityTypeRef amenityType = domain.getAmenityType();

        return PropertyAmenityEntity.builder()
                .id(domain.getId())
                .propertyId(domain.getPropertyId().getValue())
                .amenityTypeId(domain.getAmenityTypeId())
                .amenityName(amenityType.getName())
                .category(amenityType.getCategoryName())
                .description(amenityType.getDescription())
                .isIncluded(domain.isIncluded())
                .levelOfNeed(domain.getLevelOfNeed())
                .customNote(domain.getCustomNote())
                .createdAt(domain.getCreatedAt())
                .build();
    }
}