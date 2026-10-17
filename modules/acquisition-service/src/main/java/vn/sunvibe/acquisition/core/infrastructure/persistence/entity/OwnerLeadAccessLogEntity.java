package vn.sunvibe.acquisition.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * JPA Entity - OwnerLeadAccessLogEntity
 * Mapping of the OwnerLeadAccessLog domain model to the database.
 * Table: owner_lead_access_logs
 * <p>
 * Insert-only log (acquisition-owner-lead-queue BR-7): {@code @Immutable} so Hibernate never updates a row. The user
 * has no foreign key because auth-service owns the users.
 */
@Entity
@Immutable
@Table(name = "owner_lead_access_logs", indexes = {
        @Index(name = "idx_owner_lead_access_logs_lead_id_accessed_at", columnList = "lead_id, accessed_at"),
        @Index(name = "idx_owner_lead_access_logs_user_id", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnerLeadAccessLogEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "lead_id", nullable = false, length = 26)
    private String leadId;

    @Column(name = "user_id", nullable = false, length = 26)
    private String userId;

    @Column(name = "accessed_at", nullable = false)
    private Instant accessedAt;
}
