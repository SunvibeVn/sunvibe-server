package vn.sunvibe.property.core.infrastructure.persistence.entity.intake;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/** JPA Entity - which unit a surveyed space became (property-create intake spec 5.2 file 8). */
@Entity
@Table(name = "inventory_unit_mappings")
@IdClass(IntakeUnitMappingEntity.Key.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class IntakeUnitMappingEntity {

    @Id
    @Column(name = "intake_id", length = 26)
    private String intakeId;

    @Id
    @Column(name = "space_id", length = 26)
    private String spaceId;

    @Column(name = "space_code", nullable = false, length = 50)
    private String spaceCode;

    @Column(name = "unit_id", nullable = false, length = 26)
    private String unitId;

    @Column(name = "canonical", nullable = false)
    private Boolean canonical;

    /** The primary key: one row per space of an intake. */
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String intakeId;
        private String spaceId;
    }
}
