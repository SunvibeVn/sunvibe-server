package vn.sunvibe.acquisition.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA Entity - SaleTerritoryEntity
 * Table: sale_territories
 * <p>
 * Who looks after which ward: the ward code is the primary key because a ward has at most one person. The ward and
 * the users are plain codes and ids without foreign keys (ADR-009).
 */
@Entity
@Table(name = "sale_territories", indexes = {
        @Index(name = "idx_sale_territories_user_id", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleTerritoryEntity {

    @Id
    @Column(name = "ward_code", length = 10)
    private String wardCode;

    @Column(name = "user_id", nullable = false, length = 26)
    private String userId;

    @Column(name = "assigned_by", nullable = false, length = 26)
    private String assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;
}
