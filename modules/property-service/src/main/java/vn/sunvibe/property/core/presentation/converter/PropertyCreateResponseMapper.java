package vn.sunvibe.property.core.presentation.converter;

import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.domain.model.aggregate.Property;
import vn.sunvibe.property.core.presentation.dto.response.PropertyCreateResponse;

/**
 * Mapper - PropertyCreateResponseMapper (Record version)
 *
 * Converts from Domain Model (Property)
 * to Response DTO Record (PropertyCreateResponse).
 */
@Component
public class PropertyCreateResponseMapper {

    /**
     * Map Property domain model -> PropertyCreateResponse DTO
     */
    public PropertyCreateResponse toResponse(Property property, String message) {
        if (property == null) {
            throw new IllegalArgumentException("Property cannot be null");
        }

        return new PropertyCreateResponse(
                property.getId().getValue(),
                property.getPropertyCode().getValue(),
                property.getTitle(),
                property.getDescription(),

                // Location
                property.getLocation().getStreet(),
                property.getLocation().getWard(),
                property.getLocation().getDistrict(),
                property.getLocation().getCity(),
                property.getLocation().getPostalCode(),

                // Details
                property.getType().name(),
                property.getBedrooms(),
                property.getBathrooms(),
                property.getSquareMeters(),

                // Price
                property.getMonthlyPrice().getAmount(),
                property.getDepositPrice().getAmount(),
                property.getMinRentMonths(),

                // Status
                property.getStatus().toString(),
                property.getOwnerId(),

                // Audit
                property.getCreatedAt(),
                property.getCreatedBy(),

                // Statistics
                property.getImages().size(),
                property.getAppliedRules().size(),
                property.getAmenities().size(),

                // Message
                message
        );
    }

    /**
     * Map Property -> Response (with default message)
     */
    public PropertyCreateResponse toResponse(Property property) {
        return toResponse(property, "Property created successfully");
    }
}