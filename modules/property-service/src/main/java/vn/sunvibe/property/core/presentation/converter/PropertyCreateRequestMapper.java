package vn.sunvibe.property.core.presentation.converter;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.application.command.PropertyCreateCommand;
import vn.sunvibe.property.core.domain.model.enums.PropertyType;
import vn.sunvibe.property.core.presentation.dto.request.PropertyCreateRequest;

import java.util.List;

/**
 * Mapper - PropertyCreateRequestMapper (Record version)
 *
 * Converts from API DTO Record (PropertyCreateRequest)
 * to Application Command Record (PropertyCreateCommand).
 */
@Component
public class PropertyCreateRequestMapper {

    /**
     * Map PropertyCreateRequest -> PropertyCreateCommand
     */
    public PropertyCreateCommand toCommand(PropertyCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("PropertyCreateRequest cannot be null");
        }

        // Get user ID from security context
        String userId = getCurrentUserId();

        // Convert property type string -> enum
        PropertyType propertyType = parsePropertyType(request.propertyType());

        // Map images using stream
        List<PropertyCreateCommand.ImageData> imageDataList = request.images().stream()
                .map(this::mapImage)
                .toList();

        // Build command using record constructor
        return new PropertyCreateCommand(
                request.propertyCode(),
                request.title(),
                request.description(),

                // Location
                request.street(),
                request.ward(),
                request.district(),
                request.city(),
                request.postalCode(),
                request.latitude(),
                request.longitude(),

                // Property details
                propertyType,
                request.bedrooms(),
                request.bathrooms(),
                request.squareMeters(),

                // Price
                request.monthlyPrice(),
                request.depositPrice(),
                request.minRentMonths(),

                // Ownership & audit
                request.ownerId(),
                userId,

                // Additional data
                request.ruleCategories(),
                request.amenityTypes(),
                imageDataList
        );
    }

    /**
     * Map image request -> command image data
     */
    private PropertyCreateCommand.ImageData mapImage(PropertyCreateRequest.PropertyImageRequest imageRequest) {
        return new PropertyCreateCommand.ImageData(
                imageRequest.imageUrl(),
                imageRequest.imageKey(),
                imageRequest.caption(),
                imageRequest.isPrimary(),
                imageRequest.displayOrder()
        );
    }

    /**
     * Parse property type string to enum
     */
    private PropertyType parsePropertyType(String typeStr) {
        if (typeStr == null || typeStr.isBlank()) {
            throw new IllegalArgumentException("PropertyType cannot be null");
        }

        try {
            return PropertyType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid PropertyType: " + typeStr +
                            ". Valid values: STUDIO, ONE_BEDROOM, TWO_BEDROOM, THREE_BEDROOM, FOUR_BEDROOM, DUPLEX, PENTHOUSE"
            );
        }
    }

    /**
     * Get userId from security context.
     */
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        throw new IllegalArgumentException("Cannot retrieve userId from security context");
    }
}