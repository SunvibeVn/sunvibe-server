package vn.sunvibe.property.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO - PropertyCreateRequest (Record)
 * <p>
 * Immutable data carrier for the create property request.
 * Records automatically generate: constructor, getters, equals, hashCode, toString.
 */
public record PropertyCreateRequest(
        @NotBlank(message = "PropertyCode cannot be null")
        String propertyCode,

        @NotBlank(message = "Title cannot be null")
        String title,

        String description,

        // ==================== Location ====================
        @NotBlank(message = "Street cannot be null")
        String street,

        @NotBlank(message = "Ward cannot be null")
        String ward,

        @NotBlank(message = "District cannot be null")
        String district,

        String city,  // Default: Da Nang

        String postalCode,

        Double latitude,
        Double longitude,

        // ==================== Property Details ====================
        @NotBlank(message = "PropertyType cannot be null")
        String propertyType,

        @NotNull(message = "Bedrooms cannot be null")
        @Positive(message = "Bedrooms must be > 0")
        Integer bedrooms,

        @NotNull(message = "Bathrooms cannot be null")
        @Positive(message = "Bathrooms must be > 0")
        Integer bathrooms,

        @NotNull(message = "SquareMeters cannot be null")
        @Positive(message = "SquareMeters must be > 0")
        Double squareMeters,

        // ==================== Price ====================
        @NotNull(message = "MonthlyPrice cannot be null")
        @DecimalMin(value = "0.01", message = "MonthlyPrice must be > 0")
        BigDecimal monthlyPrice,

        @NotNull(message = "DepositPrice cannot be null")
        @DecimalMin(value = "0.01", message = "DepositPrice must be > 0")
        BigDecimal depositPrice,

        @NotNull(message = "MinRentMonths cannot be null")
        @Min(value = 1, message = "MinRentMonths must be >= 1")
        Integer minRentMonths,

        // ==================== Ownership ====================
        @Schema(description = "The owner's user id (required). The owner must be able to sign in and must not hold a "
                + "staff or admin role; the owner gets the OWNER role if missing. The caller is never the owner by default.")
        @NotBlank(message = "OwnerId is required.")
        String ownerId,

        // ==================== Additional Data ====================
        List<Integer> ruleCategories,    // IDs of rule categories
        List<Integer> amenityTypes,      // IDs of amenity types
        List<PropertyImageRequest> images
) {

    /**
     * Compact constructor - validation logic
     */
    public PropertyCreateRequest {
        // City defaults to "Da Nang" if not provided
        if (city == null || city.isBlank()) {
            city = "Đà Nẵng";
        }

        // Empty list instead of null
        if (ruleCategories == null) {
            ruleCategories = List.of();
        }
        if (amenityTypes == null) {
            amenityTypes = List.of();
        }
        if (images == null) {
            images = List.of();
        }
    }

    /**
     * Convenience constructor with default city
     */
    public PropertyCreateRequest(String propertyCode, String title, String description,
                                 String street, String ward, String district,
                                 String postalCode, Double latitude, Double longitude,
                                 String propertyType, Integer bedrooms, Integer bathrooms,
                                 Double squareMeters, BigDecimal monthlyPrice,
                                 BigDecimal depositPrice, Integer minRentMonths,
                                 String ownerId, List<Integer> ruleCategories,
                                 List<Integer> amenityTypes, List<PropertyImageRequest> images) {
        this(propertyCode, title, description, street, ward, district, "Đà Nẵng",
                postalCode, latitude, longitude, propertyType, bedrooms, bathrooms,
                squareMeters, monthlyPrice, depositPrice, minRentMonths, ownerId,
                ruleCategories, amenityTypes, images);
    }

    /**
     * Record DTO cho image request
     */
    public record PropertyImageRequest(
            @NotBlank(message = "ImageUrl cannot be null")
            String imageUrl,

            String imageKey,

            String caption,

            boolean isPrimary,

            Integer displayOrder
    ) {
        /**
         * Compact constructor
         */
        public PropertyImageRequest {
            if (displayOrder == null) {
                displayOrder = 0;
            }
        }
    }
}