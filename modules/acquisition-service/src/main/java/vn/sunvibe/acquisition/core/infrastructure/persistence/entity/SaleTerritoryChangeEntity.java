package vn.sunvibe.acquisition.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * JPA Entity - SaleTerritoryChangeEntity
 * Mapping of the SaleTerritoryChange domain model to the database.
 * Table: sale_territory_changes
 * <p>
 * Insert-only history (acquisition-owner-lead-queue BR-10): {@code @Immutable} so Hibernate never updates a row. The
 * ward and the users are plain codes and ids without foreign keys (ADR-009).
 */
@Entity
@Immutable
@Table(name = "sale_territory_changes", indexes = {
        @Index(name = "idx_sale_territory_changes_ward_code", columnList = "ward_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleTerritoryChangeEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "ward_code", nullable = false, length = 10)
    private String wardCode;

    @Column(name = "old_user_id", length = 26)
    private String oldUserId;

    @Column(name = "new_user_id", length = 26)
    private String newUserId;

    @Column(name = "actor_user_id", nullable = false, length = 26)
    private String actorUserId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
