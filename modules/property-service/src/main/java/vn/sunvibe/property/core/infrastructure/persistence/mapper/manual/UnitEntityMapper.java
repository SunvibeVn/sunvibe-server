package vn.sunvibe.property.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.domain.model.unit.Unit;
import vn.sunvibe.property.core.domain.model.unit.UnitId;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;
import vn.sunvibe.property.core.infrastructure.persistence.entity.UnitEntity;

/**
 * Mapper to convert between the Unit Domain Model and UnitEntity.
 * Domain → Entity: When saving to database.
 * Entity → Domain: When retrieving from database (rebuilt with Unit.load).
 */
@Component
public class UnitEntityMapper {

    /**
     * Convert Entity → Domain.
     */
    public Unit toDomain(UnitEntity entity) {
        if (entity == null) return null;

        return Unit.load(
                UnitId.of(entity.getId()),
                PropertyId.of(entity.getPropertyId()),
                entity.getUnitCode(),
                entity.getUnitType(),
                entity.getBedrooms(),
                entity.getBathrooms(),
                entity.getAreaSqm(),
                entity.getAvailabilityState(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    /**
     * Convert Domain → Entity.
     * A new unit has no version (null), so Spring Data inserts it and Hibernate writes version 0.
     */
    public UnitEntity toEntity(Unit domain) {
        if (domain == null) return null;

        return UnitEntity.builder()
                .id(domain.getId().getValue())
                .propertyId(domain.getPropertyId().getValue())
                .unitCode(domain.getUnitCode())
                .unitType(domain.getUnitType())
                .bedrooms(domain.getBedrooms())
                .bathrooms(domain.getBathrooms())
                .areaSqm(domain.getAreaSqm())
                .availabilityState(domain.getAvailabilityState())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .version(domain.getVersion())
                .build();
    }
}
