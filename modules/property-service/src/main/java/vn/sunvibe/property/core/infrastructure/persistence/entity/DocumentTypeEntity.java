package vn.sunvibe.property.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "document_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentTypeEntity {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "type_name", nullable = false, length = 100)
    private String typeName;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "requires_renewal", nullable = false)
    private Boolean requiresRenewal;

    @Column(name = "renewal_frequency_days")
    private Integer renewalFrequencyDays;

    @Column(name = "is_archived", nullable = false)
    private Boolean isArchived;

    @Column(name = "icon", length = 100)
    private String icon;
}