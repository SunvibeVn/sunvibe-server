package vn.sunvibe.property.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import vn.sunvibe.property.core.domain.model.unit.UnitAvailabilityState;
import vn.sunvibe.property.core.domain.model.unit.UnitType;

import java.time.Instant;

/**
 * JPA Entity - UnitEntity
 * Mapping of the Unit domain model to the database.
 * Table: units
 * <p>
 * A unit refers to its property by the plain property_id column, not by an association
 * (aggregates refer to each other by id), so the property must be saved first.
 */
@Entity
@Table(name = "units",
        uniqueConstraints = @UniqueConstraint(name = "uk_units_property_id_unit_code",
                columnNames = {"property_id", "unit_code"}),
        indexes = @Index(name = "idx_units_property_id", columnList = "property_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnitEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "property_id", nullable = false, length = 36)
    private String propertyId;

    @Column(name = "unit_code", nullable = false, length = 50)
    private String unitCode;

    @Column(name = "unit_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private UnitType unitType;

    @Column(name = "bedrooms", nullable = false)
    private Integer bedrooms;

    @Column(name = "bathrooms", nullable = false)
    private Integer bathrooms;

    @Column(name = "area_sqm", nullable = false)
    private Double areaSqm;

    @Column(name = "availability_state", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private UnitAvailabilityState availabilityState;

    // ==================== Audit Fields ====================
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ==================== Optimistic Locking ====================
    // Hibernate writes 0 on insert and adds 1 on every update; a stale update then fails (property-units BR-14).
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
