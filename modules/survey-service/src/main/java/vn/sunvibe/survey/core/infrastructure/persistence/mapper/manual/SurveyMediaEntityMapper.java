package vn.sunvibe.survey.core.infrastructure.persistence.mapper.manual;

import vn.sunvibe.survey.core.domain.model.appointment.Basis;
import vn.sunvibe.survey.core.domain.model.media.MediaHold;
import vn.sunvibe.survey.core.domain.model.media.MediaPlacement;
import vn.sunvibe.survey.core.domain.model.media.MediaRemoval;
import vn.sunvibe.survey.core.domain.model.media.MediaRemovalCorrection;
import vn.sunvibe.survey.core.domain.model.media.MediaVariant;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyMediaEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyMediaVariantEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyMediaVariantKey;

import java.util.Comparator;
import java.util.List;

/** Maps survey photos and their variants between the aggregate and the rows (survey-media spec 5.2). */
public final class SurveyMediaEntityMapper {

    private SurveyMediaEntityMapper() {
    }

    public static SurveyMediaEntity toEntity(SurveyMedia media) {
        MediaRemoval removal = media.getRemoval();
        MediaRemovalCorrection correction = removal == null ? null : removal.correctionOrNull();
        MediaHold hold = media.getHold();
        return SurveyMediaEntity.builder()
                .id(media.getId().getValue())
                .checklistId(media.getChecklistId())
                .uploadKey(media.getUploadKey())
                .mediaNo(media.getMediaNo())
                .version(media.getVersion())
                .spaceId(media.getPlacement().spaceIdOrNull())
                .spaceCodeAtUpload(media.getPlacement().spaceCodeAtUploadOrNull())
                .category(media.getCategory())
                .caption(media.getCaption())
                .uploadedByUserId(media.getUploadedByUserId())
                .createdAt(media.getCreatedAt())
                .declaredSizeBytes(media.getDeclaredSizeBytes())
                .sourceSha256(media.getSourceSha256())
                .status(media.getStatus())
                .statusReason(media.getStatusReason())
                .receiveToken(media.getReceiveToken())
                .receiveKey(media.getReceiveKey())
                .receiveStartedAt(media.getReceiveStartedAt())
                .contentReceivedAt(media.getContentReceivedAt())
                .sourceStorageKey(media.getSourceStorageKey())
                .sourceDeletedAt(media.getSourceDeletedAt())
                .sourceFormat(media.getSourceFormat())
                .readyAt(media.getReadyAt())
                .finishedAt(media.getFinishedAt())
                .removedAt(removal == null ? null : removal.at())
                .removedByUserId(removal == null ? null : removal.byUserId())
                .removalKind(removal == null ? null : removal.kind())
                .removalCorrectionReason(correction == null ? null : correction.reason())
                .removalCorrectionBasisOn(correction == null ? null : correction.basis().confirmedOn())
                .removalCorrectionBasisText(correction == null ? null : correction.basis().text())
                .removedBySaveId(removal == null ? null : removal.bySaveIdOrNull())
                .heldAt(hold.heldAt())
                .heldByUserId(hold.heldByUserId())
                .holdReason(hold.reason())
                .holdReleasedAt(hold.releasedAt())
                .holdReleasedByUserId(hold.releasedByUserId())
                .contentDeleteRequestedAt(media.getContentDeleteRequestedAt())
                .contentDeletedAt(media.getContentDeletedAt())
                .contentMissingAt(media.getContentMissingAt())
                .build();
    }

    public static SurveyMedia toDomain(SurveyMediaEntity entity, List<MediaVariant> variants) {
        MediaRemoval removal = entity.getRemovedAt() == null ? null : new MediaRemoval(entity.getRemovedAt(),
                entity.getRemovedByUserId(), entity.getRemovalKind(),
                entity.getRemovalCorrectionReason() == null ? null : new MediaRemovalCorrection(
                        entity.getRemovalCorrectionReason(), new Basis(entity.getRemovalCorrectionBasisOn(),
                        entity.getRemovalCorrectionBasisText())),
                entity.getRemovedBySaveId());
        MediaHold hold = entity.getHeldAt() == null && entity.getHoldReleasedAt() == null ? MediaHold.NONE
                : new MediaHold(entity.getHeldAt(), entity.getHeldByUserId(), entity.getHoldReason(),
                entity.getHoldReleasedAt(), entity.getHoldReleasedByUserId());
        return SurveyMedia.load(SurveyMediaId.of(entity.getId()), entity.getChecklistId(), entity.getUploadKey(),
                entity.getMediaNo(), new MediaPlacement(entity.getSpaceId(), entity.getSpaceCodeAtUpload()),
                entity.getCategory(), entity.getCaption(), entity.getUploadedByUserId(), entity.getCreatedAt(),
                entity.getDeclaredSizeBytes(), entity.getSourceSha256(), entity.getStatus(), entity.getStatusReason(),
                entity.getReceiveToken(), entity.getReceiveKey(), entity.getReceiveStartedAt(),
                entity.getContentReceivedAt(), entity.getSourceStorageKey(), entity.getSourceDeletedAt(),
                entity.getSourceFormat(), entity.getReadyAt(), entity.getFinishedAt(), removal, hold,
                entity.getContentDeleteRequestedAt(), entity.getContentDeletedAt(), entity.getContentMissingAt(),
                variants.stream().sorted(Comparator.comparing(MediaVariant::kind)).toList(), entity.getVersion());
    }

    public static SurveyMediaVariantEntity toEntity(SurveyMediaId mediaId, MediaVariant variant) {
        return SurveyMediaVariantEntity.builder()
                .id(new SurveyMediaVariantKey(mediaId.getValue(), variant.kind()))
                .attempt(variant.attempt())
                .storageKey(variant.storageKey())
                .sha256(variant.sha256())
                .sizeBytes(variant.sizeBytes())
                .width(variant.width())
                .height(variant.height())
                .processorVersion(variant.processorVersion())
                .createdAt(variant.createdAt())
                .build();
    }

    public static MediaVariant toDomain(SurveyMediaVariantEntity entity) {
        return new MediaVariant(entity.getId().getVariant(), entity.getAttempt(), entity.getStorageKey(),
                entity.getSha256(), entity.getSizeBytes(), entity.getWidth(), entity.getHeight(),
                entity.getProcessorVersion(), entity.getCreatedAt());
    }
}
