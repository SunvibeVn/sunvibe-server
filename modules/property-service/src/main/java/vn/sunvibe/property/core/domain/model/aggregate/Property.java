package vn.sunvibe.property.core.domain.model.aggregate;


import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.sunvibe.common.time.AppClock;
import vn.sunvibe.property.core.application.exception.PropertyValidationException;
import vn.sunvibe.property.core.domain.model.amenity.PropertyAmenityApply;
import vn.sunvibe.property.core.domain.model.availability.PropertyAvailability;
import vn.sunvibe.property.core.domain.model.document.PropertyDocument;
import vn.sunvibe.property.core.domain.model.enums.PropertyStatus;
import vn.sunvibe.property.core.domain.model.enums.PropertyType;
import vn.sunvibe.property.core.domain.model.image.PropertyImage;
import vn.sunvibe.property.core.domain.model.price.PropertyPrice;
import vn.sunvibe.property.core.domain.model.rule.PropertyRuleApply;
import vn.sunvibe.property.core.domain.model.vo.Location;
import vn.sunvibe.property.core.domain.model.vo.Money;
import vn.sunvibe.property.core.domain.model.vo.PropertyCode;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/**
 * Domain Model - Property (Aggregate Root)
 * <p>
 * - appliedRules: List<PropertyRuleApply> (N:M via RuleCategory)
 * - amenities: List<PropertyAmenityApply> (N:M via AmenityType)
 * - documents: List<PropertyDocument> (1:M, ref to DocumentType master)
 * - images, prices, availability: unchanged (1:M direct)
 */

@Data
@NoArgsConstructor
public class Property {

    private PropertyId id;
    private PropertyCode propertyCode;
    private String title;                    // Property title
    private String description;              // Detailed description
    private Location location;               // Property location
    private PropertyType type;               // Type: STUDIO, ONE_BEDROOM, etc
    private Integer bedrooms;                // Number of bedrooms
    private Integer bathrooms;               // Number of bathrooms
    private Double squareMeters;             // Area (m²)
    private PropertyStatus status;           // AVAILABLE, RENTED, MAINTENANCE, INACTIVE
    private String ownerId;                  // Owner ID (from owner-service)

    private Money monthlyPrice;              // Monthly rent
    private Money depositPrice;              // Security deposit
    private Integer minRentMonths;           // Minimum rental months

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    // ==================== REFACTORED Relationships ====================

    // M:N via RuleCategory (master)
    private List<PropertyRuleApply> appliedRules = new ArrayList<>();

    // M:N via AmenityType (master)
    private List<PropertyAmenityApply> amenities = new ArrayList<>();

    // 1:M direct (unchanged)
    private List<PropertyImage> images = new ArrayList<>();
    private List<PropertyDocument> documents = new ArrayList<>();
    private List<PropertyPrice> priceHistories = new ArrayList<>();
    private PropertyAvailability availability;

    // ==================== Constructor ====================

    public Property(PropertyId id, PropertyCode propertyCode, String title, String description,
                    Location location, PropertyType type, Integer bedrooms, Integer bathrooms,
                    Double squareMeters, Money monthlyPrice, Money depositPrice,
                    Integer minRentMonths, String ownerId, String createdBy) {

        if (id == null) throw new PropertyValidationException(FieldName.PROPERTY_ID, "PropertyId is required.");
        if (propertyCode == null)
            throw new PropertyValidationException(FieldName.PROPERTY_CODE, "PropertyCode is required.");
        if (title == null || title.isBlank())
            throw new PropertyValidationException(FieldName.TITLE, "Title is required.");
        if (location == null) throw new PropertyValidationException(FieldName.LOCATION, "Location is required.");
        if (type == null) throw new PropertyValidationException(FieldName.TYPE, "PropertyType is required.");
        if (bedrooms == null || bedrooms <= 0)
            throw new PropertyValidationException(FieldName.BEDROOMS, "Bedrooms must > 0");
        if (bathrooms == null || bathrooms <= 0)
            throw new PropertyValidationException(FieldName.BATHROOMS, "Bathrooms must > 0");
        if (squareMeters == null || squareMeters <= 0)
            throw new PropertyValidationException(FieldName.SQUARE_METERS, "SquareMeters must > 0");
        if (monthlyPrice == null || !monthlyPrice.isPositive())
            throw new PropertyValidationException(FieldName.MONTHLY_PRICE, "MonthlyPrice is invalid.");
        if (depositPrice == null || !depositPrice.isPositive())
            throw new PropertyValidationException(FieldName.DEPOSIT_PRICE, "DepositPrice is invalid.");
        if (minRentMonths == null || minRentMonths < 1)
            throw new PropertyValidationException(FieldName.MIN_RENT_MONTHS, "MinRentMonths must >= 1");
        if (ownerId == null || ownerId.isBlank())
            throw new PropertyValidationException(FieldName.OWNER_ID, "OwnerId is required.");
        if (createdBy == null || createdBy.isBlank())
            throw new PropertyValidationException(FieldName.CREATED_BY, "CreatedBy is required.");

        this.id = id;
        this.propertyCode = propertyCode;
        this.title = title;
        this.description = description;
        this.location = location;
        this.type = type;
        this.bedrooms = bedrooms;
        this.bathrooms = bathrooms;
        this.squareMeters = squareMeters;
        this.monthlyPrice = monthlyPrice;
        this.depositPrice = depositPrice;
        this.minRentMonths = minRentMonths;
        this.ownerId = ownerId;
        this.status = PropertyStatus.AVAILABLE;
        this.createdAt = AppClock.currentLocalTime();
        this.updatedAt = AppClock.currentLocalTime();
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
        this.availability = PropertyAvailability.createAvailable(id);
    }

    // ==================== Factory Methods ====================

    public static Property create(PropertyCode code, String title, String description,
                                  Location location, PropertyType type, Integer bedrooms,
                                  Integer bathrooms, Double squareMeters, Money monthlyPrice,
                                  Money depositPrice, Integer minRentMonths, String ownerId,
                                  String createdBy) {
        PropertyId id = PropertyId.generate();
        return new Property(id, code, title, description, location, type, bedrooms,
                bathrooms, squareMeters, monthlyPrice, depositPrice,
                minRentMonths, ownerId, createdBy);
    }

    /**
     * Load from database (reconstruct).
     */
    public static Property load(PropertyId id, PropertyCode code, String title,
                                String description, Location location, PropertyType type,
                                Integer bedrooms, Integer bathrooms, Double squareMeters,
                                Money monthlyPrice, Money depositPrice, Integer minRentMonths,
                                String ownerId, PropertyStatus status, LocalDateTime createdAt,
                                LocalDateTime updatedAt, String createdBy, String updatedBy) {
        Property property = new Property(id, code, title, description, location, type,
                bedrooms, bathrooms, squareMeters, monthlyPrice,
                depositPrice, minRentMonths, ownerId, createdBy);
        property.status = status;
        property.createdAt = createdAt;
        property.updatedAt = updatedAt;
        property.updatedBy = updatedBy;
        return property;
    }

    // ==================== Business Methods ====================

    /**
     * Update basic property information.
     */
    public void updateBasicInfo(String title, String description, Integer bedrooms,
                                Integer bathrooms, Double squareMeters, String updatedBy) {
        if (title != null && !title.isBlank()) this.title = title;
        if (description != null && !description.isBlank()) this.description = description;
        if (bedrooms != null && bedrooms > 0) this.bedrooms = bedrooms;
        if (bathrooms != null && bathrooms > 0) this.bathrooms = bathrooms;
        if (squareMeters != null && squareMeters > 0) this.squareMeters = squareMeters;

        this.updatedAt = AppClock.currentLocalTime();
        this.updatedBy = updatedBy;
    }

    /**
     * Update the rental price.
     */
    public void updatePrice(Money monthlyPrice, Money depositPrice, String updatedBy) {
        if (monthlyPrice != null && monthlyPrice.isPositive()) {
            this.monthlyPrice = monthlyPrice;
        }
        if (depositPrice != null && depositPrice.isPositive()) {
            this.depositPrice = depositPrice;
        }
        this.updatedAt = AppClock.currentLocalTime();
        this.updatedBy = updatedBy;
    }

    /**
     * Change the property status.
     */
    public void changeStatus(PropertyStatus newStatus, String updatedBy) {
        if (!isValidStatus(newStatus.toString())) {
            throw new PropertyValidationException(FieldName.STATUS, "Status " + newStatus + " is invalid.");
        }
        this.status = newStatus;
        this.updatedAt = AppClock.currentLocalTime();
        this.updatedBy = updatedBy;
    }

    // ==================== REFACTORED: Rule Management ====================

    /**
     * Add a rule (M:N via PropertyRuleApply).
     */
    public void applyRule(PropertyRuleApply ruleApply) {
        if (ruleApply == null) {
            throw new PropertyValidationException(FieldName.APPLIED_RULES, "RuleApply is required.");
        }
        if (!ruleApply.getPropertyId().equals(this.id)) {
            throw new PropertyValidationException(FieldName.APPLIED_RULES, "RuleApply must belong to this property");
        }
        appliedRules.add(ruleApply);
    }

    /**
     * Remove a rule.
     */
    public void removeRule(PropertyRuleApply ruleApply) {
        appliedRules.remove(ruleApply);
    }

    /**
     * Get a rule by ID (of RuleCategory).
     */
    public PropertyRuleApply getRuleApply(Integer ruleCategoryId) {
        return appliedRules.stream()
                .filter(r -> r.getRuleCategoryId().equals(ruleCategoryId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Check whether the property has this rule.
     */
    public boolean hasRule(Integer ruleCategoryId) {
        return appliedRules.stream()
                .anyMatch(r -> r.getRuleCategoryId().equals(ruleCategoryId));
    }

    // ==================== REFACTORED: Amenity Management ====================

    /**
     * Add an amenity (M:N via PropertyAmenityApply).
     */
    public void applyAmenity(PropertyAmenityApply amenityApply) {
        if (amenityApply == null) {
            throw new PropertyValidationException(FieldName.AMENITIES, "AmenityApply is required.");
        }
        if (!amenityApply.getPropertyId().equals(this.id)) {
            throw new PropertyValidationException(FieldName.AMENITIES, "AmenityApply must belong to this property");
        }
        amenities.add(amenityApply);
    }

    /**
     * Remove an amenity.
     */
    public void removeAmenity(PropertyAmenityApply amenityApply) {
        amenities.remove(amenityApply);
    }

    /**
     * Get an amenity by ID (of AmenityType).
     */
    public PropertyAmenityApply getAmenityApply(Integer amenityTypeId) {
        return amenities.stream()
                .filter(a -> a.getAmenityTypeId().equals(amenityTypeId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Check whether the property has this amenity.
     */
    public boolean hasAmenity(Integer amenityTypeId) {
        return amenities.stream()
                .anyMatch(a -> a.getAmenityTypeId().equals(amenityTypeId));
    }

    /**
     * Count the number of included amenities.
     */
    public long getIncludedAmenityCount() {
        return amenities.stream()
                .filter(PropertyAmenityApply::isIncluded)
                .count();
    }

    // ==================== Document Management ====================

    /**
     * Add a document.
     */
    public void addDocument(PropertyDocument document) {
        if (document == null) {
            throw new PropertyValidationException(FieldName.DOCUMENTS, "Document is required.");
        }
        documents.add(document);
    }

    /**
     * Remove a document.
     */
    public void removeDocument(PropertyDocument document) {
        documents.remove(document);
    }

    /**
     * Get documents by document type.
     */
    public List<PropertyDocument> getDocumentsByType(Integer documentTypeId) {
        return documents.stream()
                .filter(d -> d.getDocumentTypeId().equals(documentTypeId))
                .toList();
    }

    /**
     * Get documents expiring soon.
     */
    public List<PropertyDocument> getExpiringDocuments() {
        return documents.stream()
                .filter(PropertyDocument::isExpiringSoon)
                .toList();
    }

    /**
     * Get expired documents.
     */
    public List<PropertyDocument> getExpiredDocuments() {
        return documents.stream()
                .filter(PropertyDocument::isExpired)
                .toList();
    }

    // ==================== Image Management ====================

    /**
     * Add an image.
     */
    public void addImage(PropertyImage image) {
        if (image == null) throw new PropertyValidationException(FieldName.IMAGES, "Image is required.");
        images.add(image);
    }

    /**
     * Get the primary image.
     */
    public PropertyImage getPrimaryImage() {
        return images.stream()
                .filter(PropertyImage::isPrimary)
                .findFirst()
                .orElse(images.isEmpty() ? null : images.getFirst());
    }

    // ==================== Price Management ====================

    /**
     * Add a price record.
     */
    public void addPriceRecord(PropertyPrice price) {
        if (price == null) throw new PropertyValidationException(FieldName.PRICE_HISTORIES, "Price is required.");
        priceHistories.add(price);
    }

    /**
     * Get the current price.
     */
    public PropertyPrice getCurrentPrice() {
        return priceHistories.stream()
                .filter(PropertyPrice::isCurrentPrice)
                .findFirst()
                .orElse(null);
    }

    // ==================== Availability Management ====================

    /**
     * Check whether the property is available.
     */
    public boolean isAvailable() {
        return status == PropertyStatus.AVAILABLE && availability != null && availability.isAvailable();
    }

    /**
     * Check availability for a time period.
     */
    public boolean isAvailableForPeriod(java.time.LocalDate checkIn, java.time.LocalDate checkOut) {
        return status == PropertyStatus.AVAILABLE &&
                availability != null &&
                availability.isAvailableForPeriod(checkIn, checkOut);
    }

    // ==================== Helper Methods ====================

    private boolean isValidStatus(String status) {
        if (status == null) {
            return false;
        }
        try {
            PropertyStatus.valueOf(status);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Property property = (Property) o;
        return Objects.equals(id, property.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Property{" +
                "id=" + id +
                ", code=" + propertyCode +
                ", title='" + title + '\'' +
                ", type=" + type +
                ", bedrooms=" + bedrooms +
                ", status='" + status + '\'' +
                ", rules=" + appliedRules.size() +
                ", amenities=" + amenities.size() +
                '}';
    }

    @Getter
    public enum FieldName {

        /*────────── PROPERTY ──────────*/

        PROPERTY_ID("id", "Property ID"),
        PROPERTY_CODE("propertyCode", "Property Code"),
        TITLE("title", "Title"),
        DESCRIPTION("description", "Description"),
        LOCATION("location", "Location"),
        TYPE("type", "Property Type"),
        BEDROOMS("bedrooms", "Bedrooms"),
        BATHROOMS("bathrooms", "Bathrooms"),
        SQUARE_METERS("squareMeters", "Square Meters"),
        STATUS("status", "Status"),
        OWNER_ID("ownerId", "Owner ID"),
        MONTHLY_PRICE("monthlyPrice", "Monthly Price"),
        DEPOSIT_PRICE("depositPrice", "Deposit Price"),
        MIN_RENT_MONTHS("minRentMonths", "Minimum Rent Months"),
        CREATED_AT("createdAt", "Created At"),
        UPDATED_AT("updatedAt", "Updated At"),
        CREATED_BY("createdBy", "Created By"),
        UPDATED_BY("updatedBy", "Updated By"),

        /*────────── RELATIONSHIPS ──────────*/

        APPLIED_RULES("appliedRules", "Applied Rules"),
        AMENITIES("amenities", "Amenities"),
        IMAGES("images", "Images"),
        DOCUMENTS("documents", "Documents"),
        PRICE_HISTORIES("priceHistories", "Price Histories"),
        AVAILABILITY("availability", "Availability");

        // Getters
        private final String fieldName;      // Database/JSON field name
        private final String displayName;    // User-friendly name

        FieldName(String fieldName, String displayName) {
            this.fieldName = fieldName;
            this.displayName = displayName;
        }

    }
}