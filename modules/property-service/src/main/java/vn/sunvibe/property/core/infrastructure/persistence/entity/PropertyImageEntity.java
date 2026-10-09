package vn.sunvibe.property.core.infrastructure.persistence.entity;


import jakarta.persistence.*;
import lombok.*;
import vn.sunvibe.common.time.AppClock;

import java.time.LocalDateTime;

/**
 * JPA Entity - PropertyImageEntity
 * Table: property_images
 */
@Entity
@Table(name = "property_images", indexes = {
        @Index(name = "idx_property_id_02", columnList = "property_id"),
        @Index(name = "idx_is_primary", columnList = "is_primary"),
        @Index(name = "idx_display_order", columnList = "display_order")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyImageEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "property_id", nullable = false, length = 36)
    private String propertyId;

    @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "image_key", length = 500)
    private String imageKey;  // S3 key or file path

    @Column(name = "caption", length = 255)
    private String caption;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "uploaded_by", nullable = false, length = 36)
    private String uploadedBy;

    // ==================== Relationships ====================
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false, insertable = false, updatable = false)
    private PropertyEntity property;

    // ==================== Lifecycle Callbacks ====================
    @PrePersist
    public void onPrePersist() {
        if (this.uploadedAt == null) {
            this.uploadedAt = AppClock.currentLocalTime();
        }
        if (this.isPrimary == null) {
            this.isPrimary = false;
        }
        if (this.displayOrder == null) {
            this.displayOrder = 0;
        }
    }

    @Override
    public String toString() {
        return "PropertyImageEntity{" +
                "id='" + id + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", isPrimary=" + isPrimary +
                '}';
    }
}