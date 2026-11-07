package vn.sunvibe.survey.core.infrastructure.persistence.entity.media;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.model.media.HoldReason;
import vn.sunvibe.survey.core.domain.model.media.MediaStatus;
import vn.sunvibe.survey.core.domain.model.media.MediaStatusReason;
import vn.sunvibe.survey.core.domain.model.media.RemovalKind;
import vn.sunvibe.survey.core.domain.model.media.SourceFormat;

import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA Entity - SurveyMediaEntity (survey-media spec 5.2): one survey photo; the version guards every change, and the
 * conditional updates bump it too. Partial and expression indexes live only in Flyway. Captions, hashes and keys are
 * private: no {@code toString} prints them.
 */
@Entity
@Table(name = "survey_media")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyMediaEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "checklist_id", nullable = false, length = 26)
    private String checklistId;

    @Column(name = "upload_key", nullable = false, length = 26)
    private String uploadKey;

    @Column(name = "media_no", nullable = false)
    private Integer mediaNo;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "space_id", length = 26)
    private String spaceId;

    @Column(name = "space_code_at_upload", length = 20)
    private String spaceCodeAtUpload;

    @Column(name = "category", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private EvidenceCategory category;

    @Column(name = "caption", length = 200)
    private String caption;

    @Column(name = "uploaded_by_user_id", nullable = false, length = 26)
    private String uploadedByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "declared_size_bytes", nullable = false)
    private Long declaredSizeBytes;

    @Column(name = "source_sha256", nullable = false, length = 64)
    private String sourceSha256;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private MediaStatus status;

    @Column(name = "status_reason", length = 30)
    @Enumerated(EnumType.STRING)
    private MediaStatusReason statusReason;

    @Column(name = "receive_token", length = 26)
    private String receiveToken;

    @Column(name = "receive_key", length = 120)
    private String receiveKey;

    @Column(name = "receive_started_at")
    private Instant receiveStartedAt;

    @Column(name = "content_received_at")
    private Instant contentReceivedAt;

    @Column(name = "source_storage_key", length = 120)
    private String sourceStorageKey;

    @Column(name = "source_deleted_at")
    private Instant sourceDeletedAt;

    @Column(name = "source_format", length = 10)
    @Enumerated(EnumType.STRING)
    private SourceFormat sourceFormat;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removed_by_user_id", length = 26)
    private String removedByUserId;

    @Column(name = "removal_kind", length = 10)
    @Enumerated(EnumType.STRING)
    private RemovalKind removalKind;

    @Column(name = "removal_correction_reason", length = 500)
    private String removalCorrectionReason;

    @Column(name = "removal_correction_basis_on")
    private LocalDate removalCorrectionBasisOn;

    @Column(name = "removal_correction_basis_text", length = 500)
    private String removalCorrectionBasisText;

    @Column(name = "removed_by_save_id")
    private Long removedBySaveId;

    @Column(name = "held_at")
    private Instant heldAt;

    @Column(name = "held_by_user_id", length = 26)
    private String heldByUserId;

    @Column(name = "hold_reason", length = 30)
    @Enumerated(EnumType.STRING)
    private HoldReason holdReason;

    @Column(name = "hold_released_at")
    private Instant holdReleasedAt;

    @Column(name = "hold_released_by_user_id", length = 26)
    private String holdReleasedByUserId;

    @Column(name = "content_delete_requested_at")
    private Instant contentDeleteRequestedAt;

    @Column(name = "content_deleted_at")
    private Instant contentDeletedAt;

    @Column(name = "content_missing_at")
    private Instant contentMissingAt;

    @Override
    public String toString() {
        return "SurveyMediaEntity[" + id + "]";
    }
}
