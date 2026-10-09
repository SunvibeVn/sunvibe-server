package vn.sunvibe.property.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "amenity_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AmenityTypeEntity {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "amenity_name", nullable = false, length = 100)
    private String amenityName;

    // Self-referential FK — stores the category AmenityType id
    @Column(name = "category_id")
    private Integer categoryId;

    // Denormalized category name to avoid extra join on read
    @Column(name = "category_name", length = 100)
    private String categoryName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_standard", nullable = false)
    private Boolean isStandard;

    @Column(name = "level_of_need", nullable = false, length = 50)
    private String levelOfNeed;

    @Column(name = "estimated_price", precision = 15, scale = 2)
    private BigDecimal estimatedPrice;

    @Column(name = "icon", length = 100)
    private String icon;
}