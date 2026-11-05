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

/** JPA Entity - an owner request (property-create intake spec 5.2 file 6). The hash and the snapshot are never printed. */
@Entity
@Table(name = "inventory_owner_requests")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnerRequestEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "intake_id", nullable = false, length = 26)
    private String intakeId;

    @Column(name = "target_user_id", length = 26)
    private String targetUserId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "failed_attempts", nullable = false)
    private Integer failedAttempts;

    @Column(name = "display_snapshot", columnDefinition = "TEXT")
    private String displaySnapshot;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "declined_at")
    private Instant declinedAt;

    @Column(name = "created_by_user_id", nullable = false, length = 26)
    private String createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Override
    public String toString() {
        return "OwnerRequestEntity[" + id + "]";
    }
}
