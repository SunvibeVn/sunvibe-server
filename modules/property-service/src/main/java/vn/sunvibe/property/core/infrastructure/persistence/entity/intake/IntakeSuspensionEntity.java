package vn.sunvibe.property.core.infrastructure.persistence.entity.intake;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** JPA Entity - a manager suspension (property-create intake spec 5.2 file 10). The reasons are never printed. */
@Entity
@Table(name = "inventory_suspensions")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntakeSuspensionEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "lead_id", nullable = false, length = 26)
    private String leadId;

    @Column(name = "intake_id", length = 26)
    private String intakeId;

    @Column(name = "unit_id", length = 26)
    private String unitId;

    @Column(name = "reason", nullable = false, length = 2000)
    private String reason;

    @Column(name = "created_by_user_id", nullable = false, length = 26)
    private String createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "release_reason", length = 2000)
    private String releaseReason;

    @Column(name = "released_by_user_id", length = 26)
    private String releasedByUserId;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Override
    public String toString() {
        return "IntakeSuspensionEntity[" + id + "]";
    }
}
