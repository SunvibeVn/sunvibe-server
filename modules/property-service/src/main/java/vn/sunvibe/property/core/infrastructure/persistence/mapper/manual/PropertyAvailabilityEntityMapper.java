package vn.sunvibe.property.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.domain.model.availability.PropertyAvailability;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;
import vn.sunvibe.property.core.infrastructure.persistence.entity.PropertyAvailabilityEntity;

/**
 * Mapper - PropertyAvailabilityEntityMapper
 * Converts between PropertyAvailability (Domain) and PropertyAvailabilityEntity (Entity).
 */
@Component
public class PropertyAvailabilityEntityMapper {

    /**
     * Entity → Domain
     */
    public PropertyAvailability toDomain(PropertyAvailabilityEntity entity) {
        if (entity == null) {
            return null;
        }

        PropertyId propertyId = PropertyId.of(entity.getPropertyId());

        PropertyAvailability availability = new PropertyAvailability(
                propertyId,
                entity.getAvailableFromDate(),
                entity.getReservedUntilDate(),
                entity.getBookingId(),
                entity.getStatus()
        );

        // Set private field via reflection
        setPrivateField(availability, "id", entity.getId());

        return availability;
    }

    /**
     * Domain → Entity
     */
    public PropertyAvailabilityEntity toEntity(PropertyAvailability domain) {
        if (domain == null) {
            return null;
        }

        return PropertyAvailabilityEntity.builder()
                .id(domain.getId())
                .propertyId(domain.getPropertyId().getValue())
                .availableFromDate(domain.getAvailableFromDate())
                .reservedUntilDate(domain.getReservedUntilDate())
                .bookingId(domain.getBookingId())
                .status(domain.getStatus())
                .build();
    }

    private void setPrivateField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Cannot set field: " + fieldName, e);
        }
    }
}