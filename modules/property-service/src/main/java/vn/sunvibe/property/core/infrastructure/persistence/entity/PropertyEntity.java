package vn.sunvibe.property.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import vn.sunvibe.property.core.domain.model.enums.PropertyType;
import vn.sunvibe.common.time.AppClock;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Entity - PropertyEntity
 * Mapping of the Property domain model to the database.
 * Table: properties
 */
@Entity
@Table(name = "properties", indexes = {
        @Index(name = "idx_property_code", columnList = "property_code", unique = true),
        @Index(name = "idx_owner_id", columnList = "owner_id"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_district", columnList = "district"),
        @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "property_code", nullable = false, length = 50, unique = true)
    private String propertyCode;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // ==================== Location Fields ====================
    @Column(name = "street", nullable = false, length = 255)
    private String street;

    @Column(name = "ward", nullable = false, length = 100)
    private String ward;

    @Column(name = "district", nullable = false, length = 100)
    private String district;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    // ==================== Property Details ====================
    @Column(name = "property_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private PropertyType type;

    @Column(name = "bedrooms", nullable = false)
    private Integer bedrooms;

    @Column(name = "bathrooms", nullable = false)
    private Integer bathrooms;

    @Column(name = "square_meters", nullable = false)
    private Double squareMeters;

    // ==================== Price Information ====================
    @Column(name = "monthly_price", nullable = false, precision = 15, scale = 0)
    private BigDecimal monthlyPrice;

    @Column(name = "deposit_price", nullable = false, precision = 15, scale = 0)
    private BigDecimal depositPrice;

    @Column(name = "min_rent_months", nullable = false)
    private Integer minRentMonths;

    // ==================== Status & Ownership ====================
    @Column(name = "status", nullable = false, length = 50)
    private String status;  // AVAILABLE, RENTED, MAINTENANCE, INACTIVE

    @Column(name = "owner_id", nullable = false, length = 36)
    private String ownerId;

    // ==================== Audit Fields ====================
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", nullable = false, length = 36)
    private String createdBy;

    @Column(name = "updated_by", nullable = false, length = 36)
    private String updatedBy;

    // ==================== Relationships ====================
    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PropertyImageEntity> images = new ArrayList<>();

    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PropertyAmenityEntity> amenities = new ArrayList<>();

    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PropertyRuleEntity> rules = new ArrayList<>();

    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PropertyDocumentEntity> documents = new ArrayList<>();

    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PropertyPriceEntity> priceHistory = new ArrayList<>();

    @OneToOne(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private PropertyAvailabilityEntity availability;

    // ==================== Lifecycle Callbacks ====================
    @PrePersist
    public void onPrePersist() {
        if (this.createdAt == null) {
            this.createdAt = AppClock.currentLocalTime();
        }
        if (this.updatedAt == null) {
            this.updatedAt = AppClock.currentLocalTime();
        }
    }

    @PreUpdate
    public void onPreUpdate() {
        this.updatedAt = AppClock.currentLocalTime();
    }

    // ==================== Helper Methods ====================

    public String getFullAddress() {
        return String.format("%s, %s, %s, %s", street, ward, district, city);
    }

    public void addImage(PropertyImageEntity image) {
        images.add(image);
        image.setProperty(this);
    }

    public void removeImage(PropertyImageEntity image) {
        images.remove(image);
        image.setProperty(null);
    }

    public void addAmenity(PropertyAmenityEntity amenity) {
        amenities.add(amenity);
        amenity.setProperty(this);
    }

    public void removeAmenity(PropertyAmenityEntity amenity) {
        amenities.remove(amenity);
        amenity.setProperty(null);
    }

    public void addRule(PropertyRuleEntity rule) {
        rules.add(rule);
        rule.setProperty(this);
    }

    public void removeRule(PropertyRuleEntity rule) {
        rules.remove(rule);
        rule.setProperty(null);
    }

    public void addDocument(PropertyDocumentEntity document) {
        documents.add(document);
        document.setProperty(this);
    }

    public void removeDocument(PropertyDocumentEntity document) {
        documents.remove(document);
        document.setProperty(null);
    }

    public void addPriceRecord(PropertyPriceEntity price) {
        priceHistory.add(price);
        price.setProperty(this);
    }

    @Override
    public String toString() {
        return "PropertyEntity{" +
                "id='" + id + '\'' +
                ", propertyCode='" + propertyCode + '\'' +
                ", title='" + title + '\'' +
                ", bedrooms=" + bedrooms +
                ", status='" + status + '\'' +
                '}';
    }
}
