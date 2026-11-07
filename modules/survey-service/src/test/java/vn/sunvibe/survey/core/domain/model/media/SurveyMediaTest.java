package vn.sunvibe.survey.core.domain.model.media;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.exception.media.IllegalMediaTransitionException;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.ADMIN;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.CHECKLIST;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.OTHER_SALE;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.SALE;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.T0;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.TOKEN;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.awaiting;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.cancelled;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.correction;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.expired;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.failed;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.held;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.processing;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.ready;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.receiving;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.rejected;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.variants;

/** The status machine, marks and derived states of a survey photo (survey-media spec 4.2, A-23). */
class SurveyMediaTest {

    private static final Instant LATER = T0.plusSeconds(600);

    // ==================== allowed moves (spec 4.2) ====================

    @Test
    void create_isAwaitingContentWithoutMarks() {
        SurveyMedia media = awaiting();

        assertEquals(MediaStatus.AWAITING_CONTENT, media.getStatus());
        assertNull(media.getStatusReason());
        assertFalse(media.isRemoved());
        assertFalse(media.isHeld());
        assertTrue(media.isSelected());
        assertTrue(media.isPending());
        assertEquals(T0, media.getCreatedAt());
        assertEquals(SALE, media.getUploadedByUserId());
    }

    @Test
    void claim_awaitingContent_becomesReceiving() {
        SurveyMedia media = awaiting();

        media.claim(TOKEN, "quarantine/m/1", T0.plusSeconds(60));

        assertEquals(MediaStatus.RECEIVING, media.getStatus());
        assertEquals(TOKEN, media.getReceiveToken());
        assertEquals("quarantine/m/1", media.getReceiveKey());
        assertEquals(T0.plusSeconds(60), media.getReceiveStartedAt());
    }

    @Test
    void claim_after60Minutes_isRefused() {
        SurveyMedia media = awaiting();

        assertThrows(IllegalMediaTransitionException.class,
                () -> media.claim(TOKEN, "quarantine/m/1", T0.plus(SurveyMedia.CLAIM_WINDOW)));
        assertEquals(MediaStatus.AWAITING_CONTENT, media.getStatus());
    }

    @Test
    void claim_removedAwaiting_isRefused() {
        SurveyMedia media = awaiting();
        media.remove(RemovalKind.HOLDER, SALE, null, null, T0.plusSeconds(10));

        assertThrows(IllegalMediaTransitionException.class,
                () -> media.claim(TOKEN, "quarantine/m/1", T0.plusSeconds(60)));
    }

    @Test
    void releaseClaim_receiving_backToAwaiting() {
        SurveyMedia media = receiving();

        media.releaseClaim();

        assertEquals(MediaStatus.AWAITING_CONTENT, media.getStatus());
        assertNull(media.getReceiveToken());
        assertNull(media.getReceiveKey());
        assertNull(media.getReceiveStartedAt());
    }

    @Test
    void commit_receiving_becomesProcessing() {
        SurveyMedia media = receiving();

        media.commit("quarantine/m/1", 1000L, T0.plusSeconds(90));

        assertEquals(MediaStatus.PROCESSING, media.getStatus());
        assertEquals("quarantine/m/1", media.getSourceStorageKey());
        assertEquals(T0.plusSeconds(90), media.getContentReceivedAt());
        assertNull(media.getReceiveToken());
        assertTrue(media.isPending());
    }

    @Test
    void commit_sizeOtherThanDeclared_isRefused() {
        SurveyMedia media = receiving();

        assertThrows(IllegalMediaTransitionException.class,
                () -> media.commit("quarantine/m/1", 999L, T0.plusSeconds(90)));
    }

    @Test
    void commit_removedReceiving_isRefused() {
        SurveyMedia media = receiving();
        media.remove(RemovalKind.HOLDER, SALE, null, null, T0.plusSeconds(70));

        assertThrows(IllegalMediaTransitionException.class,
                () -> media.commit("quarantine/m/1", 1000L, T0.plusSeconds(90)));
    }

    @Test
    void ready_processingNotRemoved_becomesReady() {
        SurveyMedia media = processing();

        media.ready(variants(1), SourceFormat.HEIF, LATER);

        assertEquals(MediaStatus.READY, media.getStatus());
        assertEquals(LATER, media.getReadyAt());
        assertEquals(LATER, media.getFinishedAt());
        assertEquals(SourceFormat.HEIF, media.getSourceFormat());
        assertEquals(3, media.getVariants().size());
        assertTrue(media.isServable());
        assertFalse(media.isPending());
        assertTrue(media.isSelected());
    }

    @Test
    void ready_processingRemoved_isRefused() {
        SurveyMedia media = processing();
        media.remove(RemovalKind.HOLDER, SALE, null, null, T0.plusSeconds(100));

        assertThrows(IllegalMediaTransitionException.class,
                () -> media.ready(variants(1), SourceFormat.JPEG, LATER));
        assertEquals(MediaStatus.PROCESSING, media.getStatus());
    }

    @Test
    void ready_withoutExactlyTheThreeVariants_isRefused() {
        SurveyMedia media = processing();

        assertThrows(IllegalArgumentException.class,
                () -> media.ready(variants(1).subList(0, 2), SourceFormat.JPEG, LATER));
        assertThrows(IllegalArgumentException.class, () -> media.ready(List.of(variants(1).get(0),
                variants(1).get(0), variants(1).get(2)), SourceFormat.JPEG, LATER));
    }

    @Test
    void reject_processing_becomesRejectedWithItsReason() {
        SurveyMedia media = processing();

        media.reject(MediaStatusReason.MALWARE_DETECTED, LATER);

        assertEquals(MediaStatus.REJECTED, media.getStatus());
        assertEquals(MediaStatusReason.MALWARE_DETECTED, media.getStatusReason());
        assertEquals(LATER, media.getFinishedAt());
        assertFalse(media.isSelected());
    }

    @Test
    void reject_withAReasonThatIsNoRejection_isRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> processing().reject(MediaStatusReason.PROCESSING_UNAVAILABLE, LATER));
        assertThrows(IllegalArgumentException.class, () -> processing().reject(MediaStatusReason.REMOVED, LATER));
    }

    @Test
    void fail_processing_becomesFailedProcessingUnavailable() {
        SurveyMedia media = processing();

        media.fail(LATER);

        assertEquals(MediaStatus.FAILED, media.getStatus());
        assertEquals(MediaStatusReason.PROCESSING_UNAVAILABLE, media.getStatusReason());
        assertEquals(LATER, media.getFinishedAt());
    }

    @Test
    void cancel_removedProcessing_becomesCancelledRemoved() {
        SurveyMedia media = processing();
        media.remove(RemovalKind.HOLDER, SALE, null, null, T0.plusSeconds(100));

        media.cancel(LATER);

        assertEquals(MediaStatus.CANCELLED, media.getStatus());
        assertEquals(MediaStatusReason.REMOVED, media.getStatusReason());
        assertEquals(LATER, media.getFinishedAt());
    }

    @Test
    void cancel_processingNotRemoved_isRefused() {
        assertThrows(IllegalMediaTransitionException.class, () -> processing().cancel(LATER));
    }

    @Test
    void outcomes_ofRemovedProcessing_areRefusedButCancel() {
        SurveyMedia media = processing();
        media.remove(RemovalKind.HOLDER, SALE, null, null, T0.plusSeconds(100));

        assertThrows(IllegalMediaTransitionException.class,
                () -> media.reject(MediaStatusReason.UNREADABLE, LATER));
        assertThrows(IllegalMediaTransitionException.class, () -> media.fail(LATER));
    }

    @Test
    void expire_awaitingOrReceiving_becomesUploadExpired() {
        for (SurveyMedia media : List.of(awaiting(), receiving())) {
            media.expire(LATER);

            assertEquals(MediaStatus.UPLOAD_EXPIRED, media.getStatus());
            assertEquals(MediaStatusReason.CONTENT_NOT_RECEIVED, media.getStatusReason());
            assertEquals(LATER, media.getFinishedAt());
            assertNull(media.getReceiveToken());
            assertFalse(media.isSelected());
        }
    }

    @Test
    void expire_removedAwaiting_stillExpires() {
        SurveyMedia media = awaiting();
        media.remove(RemovalKind.HOLDER, SALE, null, null, T0.plusSeconds(10));

        media.expire(LATER);

        assertEquals(MediaStatus.UPLOAD_EXPIRED, media.getStatus());
        assertTrue(media.isRemoved());
    }

    @Test
    void markSourceDeleted_afterOutcome_setsTheTime() {
        SurveyMedia media = ready();

        media.markSourceDeleted(LATER);

        assertEquals(LATER, media.getSourceDeletedAt());
        assertThrows(IllegalMediaTransitionException.class, () -> media.markSourceDeleted(LATER));
        assertThrows(IllegalMediaTransitionException.class, () -> awaiting().markSourceDeleted(LATER));
    }

    // ==================== refused moves: every final status refuses every move ====================

    @Test
    void finalStatuses_refuseEveryStatusMove() {
        List<Supplier<SurveyMedia>> finals = List.of(() -> ready(), () -> rejected(), () -> failed(),
                () -> cancelled(), () -> expired());
        List<Consumer<SurveyMedia>> moves = List.of(
                m -> m.claim(TOKEN, "quarantine/m/2", LATER),
                SurveyMedia::releaseClaim,
                m -> m.commit("quarantine/m/2", 1000L, LATER),
                m -> m.ready(variants(2), SourceFormat.JPEG, LATER),
                m -> m.reject(MediaStatusReason.UNREADABLE, LATER),
                m -> m.fail(LATER),
                m -> m.cancel(LATER),
                m -> m.expire(LATER));
        for (Supplier<SurveyMedia> photo : finals) {
            for (Consumer<SurveyMedia> move : moves) {
                SurveyMedia media = photo.get();
                MediaStatus before = media.getStatus();
                assertThrows(IllegalMediaTransitionException.class, () -> move.accept(media), before.name());
                assertEquals(before, media.getStatus());
            }
        }
    }

    @Test
    void nonFinalStatuses_refuseMovesTheyDoNotAllow() {
        assertThrows(IllegalMediaTransitionException.class, () -> awaiting().releaseClaim());
        assertThrows(IllegalMediaTransitionException.class, () -> awaiting().commit("q", 1000L, LATER));
        assertThrows(IllegalMediaTransitionException.class, () -> awaiting().ready(variants(1), SourceFormat.JPEG,
                LATER));
        assertThrows(IllegalMediaTransitionException.class, () -> receiving().claim(TOKEN, "q", LATER));
        assertThrows(IllegalMediaTransitionException.class, () -> receiving().fail(LATER));
        assertThrows(IllegalMediaTransitionException.class, () -> processing().claim(TOKEN, "q", LATER));
        assertThrows(IllegalMediaTransitionException.class, () -> processing().expire(LATER));
        assertThrows(IllegalMediaTransitionException.class, () -> processing().releaseClaim());
    }

    // ==================== removal (spec 4.7, A-9) ====================

    @Test
    void remove_readyByHolder_requestsDeletionAndStopsServing() {
        SurveyMedia media = ready();

        media.remove(RemovalKind.HOLDER, OTHER_SALE, correction(), null, LATER);

        assertTrue(media.isRemoved());
        assertEquals(LATER, media.getRemoval().at());
        assertEquals(OTHER_SALE, media.getRemoval().byUserId());
        assertEquals(RemovalKind.HOLDER, media.getRemoval().kind());
        assertNotNull(media.getRemoval().correctionOrNull());
        assertEquals(LATER, media.getContentDeleteRequestedAt());
        assertFalse(media.isServable());
        assertFalse(media.isSelected());
        assertEquals(MediaStatus.READY, media.getStatus());
    }

    @Test
    void remove_cascade_carriesTheSaveAndNoCorrection() {
        SurveyMedia media = ready();

        media.remove(RemovalKind.CASCADE, SALE, null, 42L, LATER);

        assertEquals(42L, media.getRemoval().bySaveIdOrNull());
        assertThrows(IllegalArgumentException.class,
                () -> ready().remove(RemovalKind.CASCADE, SALE, correction(), 42L, LATER));
        assertThrows(IllegalArgumentException.class, () -> ready().remove(RemovalKind.CASCADE, SALE, null, null,
                LATER));
        assertThrows(IllegalArgumentException.class, () -> ready().remove(RemovalKind.HOLDER, SALE, null, 42L,
                LATER));
    }

    @Test
    void remove_alreadyRemoved_isRefused() {
        SurveyMedia media = ready();
        media.remove(RemovalKind.HOLDER, SALE, null, null, LATER);

        assertThrows(IllegalMediaTransitionException.class,
                () -> media.remove(RemovalKind.HOLDER, SALE, null, null, LATER));
    }

    @Test
    void remove_notAcceptedPhotos_tidiesWithoutDeletionRequest() {
        for (SurveyMedia media : List.of(rejected(), failed(), expired(), awaiting(), receiving())) {
            media.remove(RemovalKind.HOLDER, SALE, null, null, LATER);

            assertTrue(media.isRemoved());
            assertNull(media.getContentDeleteRequestedAt());
        }
    }

    @Test
    void remove_heldReady_marksRemovedWithoutDeletionRequest() {
        SurveyMedia media = held(ready(), HoldReason.SECURITY_INCIDENT);

        media.remove(RemovalKind.HOLDER, SALE, null, null, LATER);

        assertTrue(media.isRemoved());
        assertTrue(media.isHeld());
        assertNull(media.getContentDeleteRequestedAt());
    }

    // ==================== hold and release (spec A-23) ====================

    @Test
    void hold_readyOrProcessing_holdsWithActorAndReason() {
        for (SurveyMedia media : List.of(ready(), processing())) {
            assertTrue(media.hold(ADMIN, HoldReason.SECURITY_INCIDENT, LATER));

            assertTrue(media.isHeld());
            assertEquals(ADMIN, media.getHold().heldByUserId());
            assertEquals(HoldReason.SECURITY_INCIDENT, media.getHold().reason());
            assertEquals(LATER, media.getHold().heldAt());
            assertFalse(media.isServable());
            assertTrue(media.isSelected());
        }
    }

    @Test
    void hold_awaitingContent_isRefused() {
        assertThrows(IllegalMediaTransitionException.class,
                () -> awaiting().hold(ADMIN, HoldReason.SECURITY_INCIDENT, LATER));
        assertThrows(IllegalMediaTransitionException.class,
                () -> receiving().hold(ADMIN, HoldReason.SECURITY_INCIDENT, LATER));
        assertThrows(IllegalMediaTransitionException.class,
                () -> rejected().hold(ADMIN, HoldReason.SECURITY_INCIDENT, LATER));
    }

    @Test
    void hold_removed_isRefused() {
        SurveyMedia media = ready();
        media.remove(RemovalKind.HOLDER, SALE, null, null, LATER);

        assertThrows(IllegalMediaTransitionException.class,
                () -> media.hold(ADMIN, HoldReason.SECURITY_INCIDENT, LATER));
    }

    @Test
    void hold_alreadyHeld_isNoOpKeepingFirstReason() {
        SurveyMedia media = held(ready(), HoldReason.CREDENTIAL_EXPOSURE);
        MediaHold first = media.getHold();

        assertFalse(media.hold(OTHER_SALE, HoldReason.SECURITY_INCIDENT, LATER));

        assertEquals(first, media.getHold());
    }

    @Test
    void release_notHeld_isNoOp() {
        SurveyMedia media = ready();

        assertFalse(media.release(ADMIN, LATER));

        assertFalse(media.isHeld());
        assertNull(media.getHold().releasedAt());
    }

    @Test
    void release_heldReady_servableAgainKeepingTheHoldColumns() {
        SurveyMedia media = held(ready(), HoldReason.SECURITY_INCIDENT);

        assertTrue(media.release(ADMIN, LATER));

        assertFalse(media.isHeld());
        assertTrue(media.isServable());
        assertEquals(LATER, media.getHold().releasedAt());
        assertEquals(ADMIN, media.getHold().releasedByUserId());
        assertEquals(HoldReason.SECURITY_INCIDENT, media.getHold().reason());
        assertNull(media.getContentDeleteRequestedAt());
    }

    @Test
    void release_removedHeldReady_requestsDeletion() {
        SurveyMedia media = held(ready(), HoldReason.SECURITY_INCIDENT);
        media.remove(RemovalKind.HOLDER, SALE, null, null, T0.plusSeconds(400));

        media.release(ADMIN, LATER);

        assertEquals(LATER, media.getContentDeleteRequestedAt());
        assertFalse(media.isServable());
        assertFalse(media.isServableToAdmin());
    }

    @Test
    void release_removedHeldProcessingThenCancelled_requestsNothing() {
        SurveyMedia media = held(processing(), HoldReason.SECURITY_INCIDENT);
        media.remove(RemovalKind.HOLDER, SALE, null, null, T0.plusSeconds(400));
        media.cancel(T0.plusSeconds(450));

        media.release(ADMIN, LATER);

        assertNull(media.getContentDeleteRequestedAt());
    }

    @Test
    void rehold_ofCredentialExposure_keepsThatReason() {
        SurveyMedia media = held(ready(), HoldReason.CREDENTIAL_EXPOSURE);
        media.release(ADMIN, T0.plusSeconds(400));

        assertTrue(media.hold(ADMIN, HoldReason.SECURITY_INCIDENT, LATER));

        assertEquals(HoldReason.CREDENTIAL_EXPOSURE, media.getHold().reason());
        assertNull(media.getHold().releasedAt());
        assertEquals(LATER, media.getHold().heldAt());
    }

    @Test
    void heldProcessing_mayEndReadyOrRejectedOrFailed_stillHeld() {
        SurveyMedia ready = held(processing(), HoldReason.SECURITY_INCIDENT);
        ready.ready(variants(1), SourceFormat.JPEG, LATER);
        SurveyMedia rejected = held(processing(), HoldReason.SECURITY_INCIDENT);
        rejected.reject(MediaStatusReason.ANIMATED, LATER);
        SurveyMedia failed = held(processing(), HoldReason.SECURITY_INCIDENT);
        failed.fail(LATER);

        for (SurveyMedia media : List.of(ready, rejected, failed)) {
            assertTrue(media.isHeld(), media.getStatus().name());
        }
        assertFalse(ready.isServable());
        assertTrue(ready.isServableToAdmin());
    }

    // ==================== content deletion and restore marks ====================

    @Test
    void markContentDeleted_afterRequest_setsTheTime() {
        SurveyMedia media = ready();
        media.remove(RemovalKind.HOLDER, SALE, null, null, T0.plusSeconds(400));

        media.markContentDeleted(LATER);

        assertEquals(LATER, media.getContentDeletedAt());
        assertThrows(IllegalMediaTransitionException.class, () -> media.markContentDeleted(LATER));
    }

    @Test
    void markContentDeleted_withoutRequest_isRefused() {
        assertThrows(IllegalMediaTransitionException.class, () -> ready().markContentDeleted(LATER));
        SurveyMedia heldRemoved = held(ready(), HoldReason.SECURITY_INCIDENT);
        heldRemoved.remove(RemovalKind.HOLDER, SALE, null, null, T0.plusSeconds(400));
        assertThrows(IllegalMediaTransitionException.class, () -> heldRemoved.markContentDeleted(LATER));
    }

    @Test
    void requestContentDeletion_onlyForRemovedReadyNotHeldWithoutRequest() {
        assertThrows(IllegalMediaTransitionException.class, () -> ready().requestContentDeletion(LATER));
        SurveyMedia removed = ready();
        removed.remove(RemovalKind.HOLDER, SALE, null, null, T0.plusSeconds(400));
        assertThrows(IllegalMediaTransitionException.class, () -> removed.requestContentDeletion(LATER));
    }

    @Test
    void markContentMissing_readyOnly_notServableAnyMore() {
        SurveyMedia media = ready();

        media.markContentMissing(LATER);

        assertEquals(LATER, media.getContentMissingAt());
        assertFalse(media.isServable());
        assertFalse(media.isServableToAdmin());
        assertThrows(IllegalMediaTransitionException.class, () -> processing().markContentMissing(LATER));
        assertThrows(IllegalMediaTransitionException.class, () -> media.markContentMissing(LATER));
    }

    // ==================== derived states ====================

    @Test
    void servable_heldOrRemovedOrDeletedOrMissing_isFalse() {
        assertTrue(ready().isServable());
        assertFalse(held(ready(), HoldReason.SECURITY_INCIDENT).isServable());
        SurveyMedia removed = ready();
        removed.remove(RemovalKind.HOLDER, SALE, null, null, LATER);
        assertFalse(removed.isServable());
        SurveyMedia deleted = ready();
        deleted.remove(RemovalKind.HOLDER, SALE, null, null, LATER);
        deleted.markContentDeleted(LATER.plusSeconds(600));
        assertFalse(deleted.isServable());
        SurveyMedia missing = ready();
        missing.markContentMissing(LATER);
        assertFalse(missing.isServable());
        for (SurveyMedia notReady : List.of(awaiting(), receiving(), processing(), rejected(), failed(),
                cancelled(), expired())) {
            assertFalse(notReady.isServable(), notReady.getStatus().name());
        }
    }

    @Test
    void servableToAdmin_heldReadyPresent_isTrue() {
        assertTrue(held(ready(), HoldReason.SECURITY_INCIDENT).isServableToAdmin());
        assertTrue(ready().isServableToAdmin());
        assertFalse(held(processing(), HoldReason.SECURITY_INCIDENT).isServableToAdmin());
    }

    @Test
    void servableToAdmin_removedHeldContentPresent_isTrue() {
        SurveyMedia media = held(ready(), HoldReason.SECURITY_INCIDENT);
        media.remove(RemovalKind.HOLDER, SALE, null, null, LATER);

        assertTrue(media.isServableToAdmin());
        assertFalse(media.isServable());
        SurveyMedia removedNotHeld = ready();
        removedNotHeld.remove(RemovalKind.HOLDER, SALE, null, null, LATER);
        assertFalse(removedNotHeld.isServableToAdmin());
    }

    @Test
    void selectedAndPending_followTheStatusAndRemoval() {
        assertTrue(processing().isPending());
        assertTrue(receiving().isPending());
        assertFalse(ready().isPending());
        SurveyMedia removed = awaiting();
        removed.remove(RemovalKind.HOLDER, SALE, null, null, LATER);
        assertFalse(removed.isSelected());
        assertFalse(removed.isPending());
        assertTrue(held(ready(), HoldReason.SECURITY_INCIDENT).isSelected());
    }

    // ==================== invariants (spec 4.2) ====================

    @Test
    void invariantI1_placementCategoryUploaderAndHashNeverChange() {
        SurveyMedia media = awaiting();
        MediaPlacement placement = media.getPlacement();
        EvidenceCategory category = media.getCategory();
        String hash = media.getSourceSha256();

        media.claim(TOKEN, "q", T0.plusSeconds(60));
        media.commit("q", 1000L, T0.plusSeconds(70));
        media.hold(ADMIN, HoldReason.SECURITY_INCIDENT, T0.plusSeconds(80));
        media.ready(variants(1), SourceFormat.PNG, T0.plusSeconds(90));
        media.release(ADMIN, T0.plusSeconds(95));
        media.remove(RemovalKind.HOLDER, OTHER_SALE, correction(), null, LATER);

        assertEquals(placement, media.getPlacement());
        assertEquals(category, media.getCategory());
        assertEquals(hash, media.getSourceSha256());
        assertEquals(SALE, media.getUploadedByUserId());
        assertEquals(CHECKLIST, media.getChecklistId());
    }

    @Test
    void invariantI2_onlyReadyIsServable_neverTheSource() {
        for (SurveyMedia notReady : List.of(processing(), rejected(), failed(), cancelled())) {
            assertNotNull(notReady.getSourceStorageKey());
            assertFalse(notReady.isServable());
            assertFalse(notReady.isServableToAdmin());
        }
    }

    @Test
    void invariantI3_readyNeedsOneVariantOfEachKind() {
        SurveyMedia media = ready();

        assertEquals(List.of(VariantKind.FULL, VariantKind.PREVIEW, VariantKind.THUMBNAIL),
                media.getVariants().stream().map(MediaVariant::kind).toList());
    }

    @Test
    void invariantI4_contentGoesOnlyThroughARequestedDeletion() {
        assertThrows(IllegalMediaTransitionException.class, () -> ready().markContentDeleted(LATER));
    }

    @Test
    void invariantI5_movesCarryNoLeadOrChecklistChange() {
        SurveyMedia media = ready();
        String checklist = media.getChecklistId();

        media.remove(RemovalKind.HOLDER, SALE, null, null, LATER);

        assertEquals(checklist, media.getChecklistId());
    }

    @Test
    void invariantI6_servabilityIsComputedFromTheCurrentState() {
        SurveyMedia media = ready();
        assertTrue(media.isServable());

        media.hold(ADMIN, HoldReason.SECURITY_INCIDENT, LATER);

        assertFalse(media.isServable());
    }

    @Test
    void invariantI8_readyOnlyWhenNotRemovedAtTheOutcome() {
        SurveyMedia media = processing();
        media.remove(RemovalKind.CASCADE, SALE, null, 7L, LATER);

        assertThrows(IllegalMediaTransitionException.class,
                () -> media.ready(variants(1), SourceFormat.JPEG, LATER));
        media.cancel(LATER);
        assertEquals(MediaStatus.CANCELLED, media.getStatus());
    }

    @Test
    void invariantI9_fromRemovalNoReadButAnAdminOfAHeldPhoto() {
        SurveyMedia media = ready();
        media.remove(RemovalKind.HOLDER, SALE, null, null, LATER);

        assertFalse(media.isServable());
        assertFalse(media.isServableToAdmin());
    }

    @Test
    void invariantI10_holdChangesNoStatusPlacementCategoryOrHash() {
        SurveyMedia media = ready();
        MediaStatus status = media.getStatus();

        media.hold(ADMIN, HoldReason.SECURITY_INCIDENT, LATER);

        assertEquals(status, media.getStatus());
        assertNull(media.getContentDeleteRequestedAt());
        assertFalse(media.isRemoved());
        assertEquals(EvidenceCategory.EXTERIOR_ACCESS, media.getCategory());
    }

    @Test
    void toString_holdsIdsOnly() {
        SurveyMedia media = ready();

        String text = media.toString();

        assertTrue(text.contains(media.getId().getValue()));
        assertFalse(text.contains(media.getSourceSha256()));
        assertFalse(text.contains("quarantine"));
    }
}
