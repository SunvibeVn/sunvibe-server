package vn.sunvibe.property.core.infrastructure.persistence.mapper.manual;


import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.domain.model.image.PropertyImage;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;
import vn.sunvibe.property.core.infrastructure.persistence.entity.PropertyImageEntity;

/**
 * Mapper - PropertyImageEntityMapper
 * Converts between PropertyImage (Domain) and PropertyImageEntity (Entity).
 */
@Component
public class PropertyImageEntityMapper {

    /**
     * Entity → Domain
     */
    public PropertyImage toDomain(PropertyImageEntity entity) {
        if (entity == null) {
            return null;
        }

        PropertyId propertyId = PropertyId.of(entity.getPropertyId());

        return PropertyImage.load(
                entity.getId(),
                propertyId,
                entity.getImageUrl(),
                entity.getImageKey(),
                entity.getCaption(),
                entity.getIsPrimary() != null && entity.getIsPrimary(),
                entity.getDisplayOrder(),
                entity.getUploadedAt(),
                entity.getUploadedBy()
        );
    }

    /**
     * Domain → Entity
     */
    public PropertyImageEntity toEntity(PropertyImage domain) {
        if (domain == null) {
            return null;
        }

        return PropertyImageEntity.builder()
                .id(domain.getId())
                .propertyId(domain.getPropertyId().getValue())
                .imageUrl(domain.getImageUrl())
                .imageKey(domain.getImageKey())
                .caption(domain.getCaption())
                .isPrimary(domain.isPrimary())
                .displayOrder(domain.getDisplayOrder())
                .uploadedAt(domain.getUploadedAt())
                .uploadedBy(domain.getUploadedBy())
                .build();
    }
}