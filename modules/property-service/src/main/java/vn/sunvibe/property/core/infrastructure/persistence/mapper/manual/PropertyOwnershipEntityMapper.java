package vn.sunvibe.property.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.domain.model.ownership.PropertyOwnership;
import vn.sunvibe.property.core.domain.model.ownership.PropertyOwnershipId;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;
import vn.sunvibe.property.core.infrastructure.persistence.entity.PropertyOwnershipEntity;

/**
 * Mapper to convert between the PropertyOwnership Domain Model and PropertyOwnershipEntity.
 * Domain → Entity: When saving to database.
 * Entity → Domain: When retrieving from database (rebuilt with PropertyOwnership.load).
 */
@Component
public class PropertyOwnershipEntityMapper {

    /**
     * Convert Entity → Domain.
     */
    public PropertyOwnership toDomain(PropertyOwnershipEntity entity) {
        if (entity == null) return null;

        return PropertyOwnership.load(
                PropertyOwnershipId.of(entity.getId()),
                PropertyId.of(entity.getPropertyId()),
                entity.getUserId(),
                entity.getRelationType(),
                entity.getVerificationState(),
                entity.getValidFrom(),
                entity.getValidTo(),
                entity.getCreatedAt()
        );
    }

    /**
     * Convert Domain → Entity.
     */
    public PropertyOwnershipEntity toEntity(PropertyOwnership domain) {
        if (domain == null) return null;

        return PropertyOwnershipEntity.builder()
                .id(domain.getId().getValue())
                .propertyId(domain.getPropertyId().getValue())
                .userId(domain.getUserId())
                .relationType(domain.getRelationType())
                .verificationState(domain.getVerificationState())
                .validFrom(domain.getValidFrom())
                .validTo(domain.getValidTo())
                .createdAt(domain.getCreatedAt())
                .build();
    }
}
