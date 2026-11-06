package vn.sunvibe.property.core.presentation.dto.response;


import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO - PropertyCreateResponse (Record)
 * <p>
 * Immutable response returned to the client after successfully creating a property.
 */
public record PropertyCreateResponse(
        String id,                  // PropertyId
        String propertyCode,
        String title,
        @Schema(nullable = true) String description,

        // Location
        String street,
        String ward,
        String district,
        String city,
        @Schema(nullable = true) String postalCode,

        // Details
        @Schema(allowableValues = {"STUDIO", "ONE_BEDROOM", "TWO_BEDROOM", "THREE_BEDROOM", "FOUR_BEDROOM", "DUPLEX",
                "PENTHOUSE"})
        String propertyType,
        Integer bedrooms,
        Integer bathrooms,
        Double squareMeters,

        // Price
        BigDecimal monthlyPrice,
        BigDecimal depositPrice,
        Integer minRentMonths,

        // Status
        @Schema(allowableValues = {"AVAILABLE", "UNAVAILABLE", "RENTED", "SOLD", "UNDER_MAINTENANCE",
                "PENDING_APPROVAL", "DRAFT"})
        String status,
        String ownerId,

        // Audit
        @Schema(types = {"string"}, format = "local-date-time",
                description = "The server's local time, with no offset", example = "2026-11-05T19:59:18.1712426")
        LocalDateTime createdAt,
        String createdBy,

        // Statistics
        Integer imageCount,
        Integer ruleCount,
        Integer amenityCount,

        // Message
        String message
) {
    /**
     * Static factory method with default message.
     */
    public static PropertyCreateResponse success(
            String id, String propertyCode, String title, String description,
            String street, String ward, String district, String city, String postalCode,
            String propertyType, Integer bedrooms, Integer bathrooms, Double squareMeters,
            BigDecimal monthlyPrice, BigDecimal depositPrice, Integer minRentMonths,
            String status, String ownerId, LocalDateTime createdAt, String createdBy,
            Integer imageCount, Integer ruleCount, Integer amenityCount) {

        return new PropertyCreateResponse(
                id, propertyCode, title, description,
                street, ward, district, city, postalCode,
                propertyType, bedrooms, bathrooms, squareMeters,
                monthlyPrice, depositPrice, minRentMonths,
                status, ownerId, createdAt, createdBy,
                imageCount, ruleCount, amenityCount,
                "Property created successfully"
        );
    }
}
