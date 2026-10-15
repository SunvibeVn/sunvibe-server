package vn.sunvibe.property.core.application.command;

import vn.sunvibe.property.core.domain.model.enums.PropertyType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Command - PropertyCreateCommand (Record)
 * <p>
 * CQRS Command object - Immutable
 * Represents the command to create a new property.
 * Created from PropertyCreateRequest by the Controller.
 * Handled by PropertyCreateUseCase.
 */
public record PropertyCreateCommand(
        // ==================== Basic Information ====================
        String propertyCode,
        String title,
        String description,

        // ==================== Location ====================
        String street,
        String ward,
        String district,
        String city,
        String postalCode,
        Double latitude,
        Double longitude,

        // ==================== Property Details ====================
        PropertyType propertyType,
        Integer bedrooms,
        Integer bathrooms,
        Double squareMeters,

        // ==================== Price ====================
        BigDecimal monthlyPrice,
        BigDecimal depositPrice,
        Integer minRentMonths,

        // ==================== Ownership & Audit ====================
        // The owner named in the request (W6: required for every caller, never the caller by default)
        String ownerId,
        // The authenticated caller: user id (actor of the OWNER grant) and username (created_by)
        String actorUserId,
        String createdBy,

        // ==================== Additional Data ====================
        List<Integer> ruleCategories,
        List<Integer> amenityTypes,
        List<ImageData> images
) {

    /**
     * Compact constructor - validation and defaults.
     */
    public PropertyCreateCommand {
        // Default city
        if (city == null || city.isBlank()) {
            city = "Đà Nẵng";
        }

        // Use empty lists instead of null
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
     * Validation method
     */
    public boolean isValid() {
        return propertyCode != null && !propertyCode.isBlank() &&
                title != null && !title.isBlank() &&
                propertyType != null &&
                bedrooms != null && bedrooms > 0 &&
                bathrooms != null && bathrooms > 0 &&
                squareMeters != null && squareMeters > 0 &&
                monthlyPrice != null && monthlyPrice.compareTo(BigDecimal.ZERO) > 0 &&
                depositPrice != null && depositPrice.compareTo(BigDecimal.ZERO) > 0 &&
                minRentMonths != null && minRentMonths >= 1 &&
                actorUserId != null && !actorUserId.isBlank() &&
                createdBy != null && !createdBy.isBlank() &&
                street != null && !street.isBlank() &&
                ward != null && !ward.isBlank() &&
                district != null && !district.isBlank();
    }

    /**
     * Record DTO cho image data
     */
    public record ImageData(
            String imageUrl,
            String imageKey,
            String caption,
            boolean isPrimary,
            Integer displayOrder
    ) {
        public ImageData {
            if (displayOrder == null) {
                displayOrder = 0;
            }
        }
    }
}