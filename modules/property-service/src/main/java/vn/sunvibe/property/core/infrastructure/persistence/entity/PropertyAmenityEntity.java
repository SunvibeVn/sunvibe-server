package vn.sunvibe.property.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "property_amenity", indexes = {
        @Index(name = "idx_amenity_property_id", columnList = "property_id"),
        @Index(name = "idx_amenity_type_id", columnList = "amenity_type_id"),
        @Index(name = "idx_amenity_is_included", columnList = "is_included")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyAmenityEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "property_id", nullable = false, length = 36)
    private String propertyId;

    @Column(name = "amenity_type_id")
    private Integer amenityTypeId;

    // Denormalized from AmenityType master
    @Column(name = "amenity_name", nullable = false, length = 255)
    private String amenityName;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_included", nullable = false)
    private Boolean isIncluded;

    @Column(name = "level_of_need", length = 50)
    private String levelOfNeed;

    @Column(name = "custom_note", length = 500)
    private String customNote;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", insertable = false, updatable = false)
    private PropertyEntity property;

    @PrePersist
    public void onPrePersist() {
        if (this.isIncluded == null) {
            this.isIncluded = true;
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
