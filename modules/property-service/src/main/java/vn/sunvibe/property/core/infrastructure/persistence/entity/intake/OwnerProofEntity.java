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
import vn.sunvibe.property.core.domain.model.intake.OwnerCheckState;

import java.time.Instant;

/** JPA Entity - an owner proof (property-create intake spec 5.2 file 7). The phrase hash and the basis are never printed. */
@Entity
@Table(name = "inventory_owner_proofs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnerProofEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "intake_id", nullable = false, length = 26)
    private String intakeId;

    @Column(name = "request_id", nullable = false, length = 26)
    private String requestId;

    @Column(name = "lead_id", nullable = false, length = 26)
    private String leadId;

    @Column(name = "intent_cycle", nullable = false)
    private Integer intentCycle;

    @Column(name = "checklist_id", nullable = false, length = 26)
    private String checklistId;

    @Column(name = "user_id", nullable = false, length = 26)
    private String userId;

    @Column(name = "scope_hash", nullable = false, length = 64)
    private String scopeHash;

    @Column(name = "statement_version", nullable = false, length = 40)
    private String statementVersion;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_check", nullable = false, length = 10)
    private OwnerCheckState ownerCheck;

    @Column(name = "sign_in_losses", nullable = false)
    private Integer signInLosses;

    @Column(name = "phrase_hash", length = 64)
    private String phraseHash;

    @Column(name = "phrase_expires_at")
    private Instant phraseExpiresAt;

    @Column(name = "phrase_failed_attempts", nullable = false)
    private Integer phraseFailedAttempts;

    @Column(name = "owner_check_by_user_id", length = 26)
    private String ownerCheckByUserId;

    @Column(name = "owner_check_basis", length = 2000)
    private String ownerCheckBasis;

    @Column(name = "owner_check_at")
    private Instant ownerCheckAt;

    @Override
    public String toString() {
        return "OwnerProofEntity[" + id + "]";
    }
}
