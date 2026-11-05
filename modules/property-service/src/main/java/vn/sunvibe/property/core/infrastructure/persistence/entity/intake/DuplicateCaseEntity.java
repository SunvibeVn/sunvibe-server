package vn.sunvibe.property.core.infrastructure.persistence.entity.intake;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.sunvibe.property.core.domain.model.intake.CaseOutcome;
import vn.sunvibe.property.core.domain.model.intake.CaseSignal;
import vn.sunvibe.property.core.domain.model.intake.CaseStatus;

import java.time.Instant;

/** JPA Entity - a duplicate case (property-create intake spec 5.2 file 9). The basis is never printed. */
@Entity
@Table(name = "inventory_duplicate_cases")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DuplicateCaseEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "intake_id", nullable = false, length = 26)
    private String intakeId;

    @Column(name = "candidate_property_id", length = 36)
    private String candidatePropertyId;

    @Column(name = "candidate_intake_id", length = 26)
    private String candidateIntakeId;

    @Column(name = "pair_key", nullable = false, length = 120)
    private String pairKey;

    @Column(name = "scope_hash", nullable = false, length = 64)
    private String scopeHash;

    @Column(name = "candidate_scope_hash", length = 64)
    private String candidateScopeHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal", nullable = false, length = 20)
    private CaseSignal signal;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private CaseStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 20)
    private CaseOutcome outcome;

    @Column(name = "proceeding_intake_id", length = 26)
    private String proceedingIntakeId;

    @Column(name = "basis", length = 2000)
    private String basis;

    @Column(name = "decided_by_user_id", length = 26)
    private String decidedByUserId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Override
    public String toString() {
        return "DuplicateCaseEntity[" + id + "]";
    }
}
