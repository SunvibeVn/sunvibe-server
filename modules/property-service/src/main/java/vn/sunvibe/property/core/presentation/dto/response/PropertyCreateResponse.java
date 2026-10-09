package vn.sunvibe.property.core.presentation.dto.response;


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
        String description,

        // Location
        String street,
        String ward,
        String district,
        String city,
        String postalCode,

        // Details
        String propertyType,
        Integer bedrooms,
        Integer bathrooms,
        Double squareMeters,

        // Price
        BigDecimal monthlyPrice,
        BigDecimal depositPrice,
        Integer minRentMonths,

        // Status
        String status,
        String ownerId,

        // Audit
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
