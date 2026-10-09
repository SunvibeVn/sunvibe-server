package vn.sunvibe.property.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "property_rules", indexes = {
        @Index(name = "idx_rule_property_id", columnList = "property_id"),
        @Index(name = "idx_rule_category_id", columnList = "rule_category_id"),
        @Index(name = "idx_rule_severity", columnList = "severity"),
        @Index(name = "idx_rule_is_mandatory", columnList = "is_mandatory")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyRuleEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "property_id", nullable = false, length = 36)
    private String propertyId;

    @Column(name = "rule_category_id")
    private Integer ruleCategoryId;

    // Denormalized from RuleCategory master
    @Column(name = "rule_name", nullable = false, length = 255)
    private String ruleName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "severity", nullable = false, length = 50)
    private String severity;

    @Column(name = "is_mandatory", nullable = false)
    private Boolean isMandatory;

    @Column(name = "custom_note", length = 500)
    private String customNote;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", insertable = false, updatable = false)
    private PropertyEntity property;

    @PrePersist
    public void onPrePersist() {
        if (this.isMandatory == null) {
            this.isMandatory = true;
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}