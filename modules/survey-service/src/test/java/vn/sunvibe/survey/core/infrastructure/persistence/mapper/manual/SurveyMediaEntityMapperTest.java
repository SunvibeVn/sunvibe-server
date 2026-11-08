package vn.sunvibe.survey.core.infrastructure.persistence.mapper.manual;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import vn.sunvibe.survey.core.domain.model.appointment.Basis;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.model.media.HoldReason;
import vn.sunvibe.survey.core.domain.model.media.MediaHold;
import vn.sunvibe.survey.core.domain.model.media.MediaPlacement;
import vn.sunvibe.survey.core.domain.model.media.MediaRemoval;
import vn.sunvibe.survey.core.domain.model.media.MediaRemovalCorrection;
import vn.sunvibe.survey.core.domain.model.media.MediaStatus;
import vn.sunvibe.survey.core.domain.model.media.MediaStatusReason;
import vn.sunvibe.survey.core.domain.model.media.MediaVariant;
import vn.sunvibe.survey.core.domain.model.media.RemovalKind;
import vn.sunvibe.survey.core.domain.model.media.SourceFormat;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.model.media.VariantKind;

/**
 * A stored survey photo keeps every value in its own field through its rows and back (cleanup spec A-5, SV-169): each
 * parameter of {@link SurveyMedia#load} gets a value no other parameter has, so two arguments swapped in the mapper or
 * in {@code load} fail here. A removal names a correction or a save, never both, so the cascade removal makes a second
 * trip.
 */
class SurveyMediaEntityMapperTest {

    private static final String ID = "01JC0000000000000000000001";

    @Test
    void load_everyFieldDistinct_survivesTheMapperRoundTrip() {
        MediaRemoval corrected = new MediaRemoval(at(7), "remover-1", RemovalKind.HOLDER,
                new MediaRemovalCorrection("reason-1", new Basis(LocalDate.of(2026, 11, 5), "basis-1")), null);

        SurveyMedia back = roundTrip(photo(corrected));

        assertEquals(ID, back.getId().getValue());
        assertEquals("checklist-1", back.getChecklistId());
        assertEquals("upload-1", back.getUploadKey());
        assertEquals(3, back.getMediaNo());
        assertEquals(MediaPlacement.space("space-1", "A1"), back.getPlacement());
        assertEquals(EvidenceCategory.KITCHEN, back.getCategory());
        assertEquals("caption-1", back.getCaption());
        assertEquals("uploader-1", back.getUploadedByUserId());
        assertEquals(at(1), back.getCreatedAt());
        assertEquals(4000L, back.getDeclaredSizeBytes());
        assertEquals("sha-1", back.getSourceSha256());
        assertEquals(MediaStatus.READY, back.getStatus());
        assertEquals(MediaStatusReason.MOTION_PHOTO, back.getStatusReason());
        assertEquals("rt-1", back.getReceiveToken());
        assertEquals("rk-1", back.getReceiveKey());
        assertEquals(at(2), back.getReceiveStartedAt());
        assertEquals(at(3), back.getContentReceivedAt());
        assertEquals("src-1", back.getSourceStorageKey());
        assertEquals(at(4), back.getSourceDeletedAt());
        assertEquals(SourceFormat.WEBP, back.getSourceFormat());
        assertEquals(at(5), back.getReadyAt());
        assertEquals(at(6), back.getFinishedAt());
        assertEquals(new MediaRemoval(at(7), "remover-1", RemovalKind.HOLDER,
                new MediaRemovalCorrection("reason-1", new Basis(LocalDate.of(2026, 11, 5), "basis-1")), null),
                back.getRemoval());
        assertEquals(new MediaHold(at(8), "holder-1", HoldReason.CREDENTIAL_EXPOSURE, at(9), "releaser-1"),
                back.getHold());
        assertEquals(at(10), back.getContentDeleteRequestedAt());
        assertEquals(at(11), back.getContentDeletedAt());
        assertEquals(at(12), back.getContentMissingAt());
        assertEquals(List.of(new MediaVariant(VariantKind.PREVIEW, 2, "var-1", "vsha-1", 3000L, 800, 600, "proc-1",
                at(13))), back.getVariants());
        assertEquals(5L, back.getVersion());

        MediaRemoval cascade = new MediaRemoval(at(14), "remover-2", RemovalKind.CASCADE, null, 6L);
        assertEquals(new MediaRemoval(at(14), "remover-2", RemovalKind.CASCADE, null, 6L),
                roundTrip(photo(cascade)).getRemoval());
    }

    private static SurveyMedia photo(MediaRemoval removal) {
        return SurveyMedia.load(SurveyMediaId.of(ID), "checklist-1", "upload-1", 3,
                MediaPlacement.space("space-1", "A1"), EvidenceCategory.KITCHEN, "caption-1", "uploader-1", at(1),
                4000L, "sha-1", MediaStatus.READY, MediaStatusReason.MOTION_PHOTO, "rt-1", "rk-1", at(2), at(3),
                "src-1", at(4), SourceFormat.WEBP, at(5), at(6), removal,
                new MediaHold(at(8), "holder-1", HoldReason.CREDENTIAL_EXPOSURE, at(9), "releaser-1"), at(10), at(11),
                at(12), List.of(new MediaVariant(VariantKind.PREVIEW, 2, "var-1", "vsha-1", 3000L, 800, 600,
                        "proc-1", at(13))), 5L);
    }

    private static SurveyMedia roundTrip(SurveyMedia media) {
        List<MediaVariant> variants = media.getVariants().stream()
                .map(variant -> SurveyMediaEntityMapper.toDomain(SurveyMediaEntityMapper.toEntity(media.getId(),
                        variant)))
                .toList();
        return SurveyMediaEntityMapper.toDomain(SurveyMediaEntityMapper.toEntity(media), variants);
    }

    private static Instant at(int minute) {
        return Instant.parse("2026-11-01T01:00:00Z").plusSeconds(60L * minute);
    }
}
