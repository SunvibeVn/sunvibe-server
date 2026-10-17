package vn.sunvibe.property.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import vn.sunvibe.property.core.domain.model.ownership.OwnershipRelationType;
import vn.sunvibe.property.core.domain.model.ownership.OwnershipVerificationState;

import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA Entity - PropertyOwnershipEntity
 * Mapping of the PropertyOwnership domain model to the database.
 * Table: property_ownerships
 * <p>
 * The property and the user are plain id columns, not associations. user_id has no foreign key either:
 * auth-service owns the users (like properties.owner_id). Nothing updates an ownership yet, so no version.
 */
@Entity
@Table(name = "property_ownerships", indexes = {
        @Index(name = "idx_property_ownerships_property_id", columnList = "property_id"),
        @Index(name = "idx_property_ownerships_user_id", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyOwnershipEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "property_id", nullable = false, length = 36)
    private String propertyId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "relation_type", nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    private OwnershipRelationType relationType;

    @Column(name = "verification_state", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OwnershipVerificationState verificationState;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;  // null = no end

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
