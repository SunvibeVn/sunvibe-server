package vn.sunvibe.property.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rule_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleCategoryEntity {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "default_severity", nullable = false, length = 50)
    private String defaultSeverity;

    @Column(name = "is_negotiable", nullable = false)
    private Boolean isNegotiable;

    @Column(name = "icon", length = 100)
    private String icon;
}
