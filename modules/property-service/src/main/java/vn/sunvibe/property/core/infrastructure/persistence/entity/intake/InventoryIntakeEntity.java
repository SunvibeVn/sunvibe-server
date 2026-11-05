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
import vn.sunvibe.property.core.domain.model.enums.AssetType;
import vn.sunvibe.property.core.domain.model.enums.RentalLayout;
import vn.sunvibe.property.core.domain.model.intake.IntakeStatus;
import vn.sunvibe.property.core.domain.model.intake.TargetMode;

import java.time.Instant;

/**
 * JPA Entity - the row of an intake (property-create intake spec 5.2 file 5). {@code version} is a plain column: commands
 * compare it under the row lock and raise it themselves (A-13), so a write that must not show keeps it. It holds selected
 * places and availability notes, so it prints only its id.
 */
@Entity
@Table(name = "inventory_intakes")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryIntakeEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "intake_no", nullable = false)
    private Integer intakeNo;

    @Column(name = "lead_id", nullable = false, length = 26)
    private String leadId;

    @Column(name = "intent_cycle", nullable = false)
    private Integer intentCycle;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IntakeStatus status;

    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "source_checklist_id", nullable = false, length = 26)
    private String sourceChecklistId;

    @Column(name = "source_sha256", nullable = false, length = 64)
    private String sourceSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "rental_scope", nullable = false, length = 10)
    private RentalLayout rentalScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 20)
    private AssetType assetType;

    @Column(name = "ward_code", nullable = false, length = 10)
    private String wardCode;

    @Column(name = "address_norm", nullable = false, length = 255)
    private String addressNorm;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "selection", nullable = false, columnDefinition = "TEXT")
    private String selection;

    @Column(name = "canonical_space_id", length = 26)
    private String canonicalSpaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_mode", length = 10)
    private TargetMode targetMode;

    @Column(name = "target_property_id", length = 36)
    private String targetPropertyId;

    @Column(name = "target_mappings", columnDefinition = "TEXT")
    private String targetMappings;

    @Column(name = "availability", columnDefinition = "TEXT")
    private String availability;

    @Column(name = "used_versions", columnDefinition = "TEXT")
    private String usedVersions;

    @Column(name = "result_property_id", length = 36)
    private String resultPropertyId;

    @Column(name = "proof_id", length = 26)
    private String proofId;

    @Column(name = "preview_hash", length = 64)
    private String previewHash;

    @Column(name = "completed_by_user_id", length = 26)
    private String completedByUserId;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public String toString() {
        return "InventoryIntakeEntity[" + id + "]";
    }
}
