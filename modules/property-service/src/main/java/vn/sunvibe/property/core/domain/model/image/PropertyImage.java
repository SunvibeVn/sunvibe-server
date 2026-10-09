package vn.sunvibe.property.core.domain.model.image;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.sunvibe.common.time.AppClock;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain Model - PropertyImage
 * Represents an image of a property.
 */
@Getter
@Setter
@NoArgsConstructor
public class PropertyImage {

    private String id;
    private PropertyId propertyId;
    private String imageUrl;           // URL or path to image
    private String imageKey;           // Key to identify the image (S3 key, etc)
    private String caption;            // Image caption
    private boolean isPrimary;         // Is this the primary image?
    private Integer displayOrder;      // Display order
    private LocalDateTime uploadedAt;
    private String uploadedBy;

    // ==================== Constructor ====================

    public PropertyImage(PropertyId propertyId, String imageUrl, String imageKey,
                         String caption, boolean isPrimary, Integer displayOrder,
                         String uploadedBy) {
        if (propertyId == null) throw new IllegalArgumentException("PropertyId cannot be null");
        if (imageUrl == null || imageUrl.isBlank()) throw new IllegalArgumentException("ImageUrl cannot be null");
        if (uploadedBy == null || uploadedBy.isBlank()) throw new IllegalArgumentException("UploadedBy cannot be null");

        this.id = UUID.randomUUID().toString();
        this.propertyId = propertyId;
        this.imageUrl = imageUrl;
        this.imageKey = imageKey;
        this.caption = caption;
        this.isPrimary = isPrimary;
        this.displayOrder = displayOrder != null ? displayOrder : 0;
        this.uploadedAt = AppClock.currentLocalTime();
        this.uploadedBy = uploadedBy;
    }

    // ==================== Factory Methods ====================

    public static PropertyImage create(PropertyId propertyId, String imageUrl,
                                       String imageKey, String caption,
                                       Integer displayOrder, String uploadedBy) {
        return new PropertyImage(propertyId, imageUrl, imageKey, caption, false, displayOrder, uploadedBy);
    }

    public static PropertyImage createPrimary(PropertyId propertyId, String imageUrl,
                                              String imageKey, String caption,
                                              String uploadedBy) {
        return new PropertyImage(propertyId, imageUrl, imageKey, caption, true, 0, uploadedBy);
    }

    // ==================== Business Methods ====================

    public void setAsPrimary() {
        this.isPrimary = true;
    }

    public void setAsSecondary() {
        this.isPrimary = false;
    }

    public void updateCaption(String caption) {
        this.caption = caption;
    }

    public void updateOrder(Integer order) {
        if (order != null && order >= 0) {
            this.displayOrder = order;
        }
    }

    // ==================== Getters ====================

    public String getId() {
        return id;
    }

    public PropertyId getPropertyId() {
        return propertyId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getImageKey() {
        return imageKey;
    }

    public String getCaption() {
        return caption;
    }

    public boolean isPrimary() {
        return isPrimary;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PropertyImage that = (PropertyImage) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "PropertyImage{" +
                "id='" + id + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", isPrimary=" + isPrimary +
                '}';
    }
}
