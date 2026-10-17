package vn.sunvibe.acquisition.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * JPA Entity - OwnerIntakeIdempotencyKeyEntity
 * Table: owner_intake_idempotency_keys
 * <p>
 * Technical data of the public owner intake: which lead an Idempotency-Key produced and a hash of the request body.
 */
@Entity
@Table(name = "owner_intake_idempotency_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnerIntakeIdempotencyKeyEntity {

    @Id
    @Column(name = "idempotency_key", length = 64)
    private String key;

    // The column is CHAR(64): a fixed-width JDBC type lets Hibernate validate it without a columnDefinition
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "lead_id", nullable = false, length = 26)
    private String leadId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
