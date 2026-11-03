package vn.sunvibe.survey.core.domain.policy.media;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.RentalScope;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.model.media.MediaPlacement;
import vn.sunvibe.survey.core.domain.model.media.MediaStatusReason;
import vn.sunvibe.survey.core.domain.model.media.RemovalKind;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_1;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_2;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identification;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identificationParts;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identificationWhole;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.observedSpace;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.OTHER_SALE;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.SALE;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.awaiting;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.cancelled;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.expired;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.failed;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.processing;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.ready;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.receiving;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.rejected;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.sha;

/**
 * The pure media rules: file size and pixel bounds (A-2, A-3), content identity (A-16), limits (A-3, A-27), removal
 * correction (4.7), placement (4.6, A-9, A-12), read budgets (A-26) and known versions (A-30).
 */
class MediaPoliciesTest {

    private static final Instant T0 = Instant.parse("2026-11-01T02:00:00Z");

    @Nested
    class MediaFileRulesTest {

        @Test
        void size_20MiBAccepted_oneMoreRefused() {
            assertTrue(MediaFileRules.sizeAccepted(20_971_520L));
            assertFalse(MediaFileRules.sizeAccepted(20_971_521L));
            assertTrue(MediaFileRules.sizeAccepted(1L));
            assertFalse(MediaFileRules.sizeAccepted(0L));
        }

        @Test
        void pixels_atTheBoundsAccepted_beyondTooManyPixels() {
            assertEquals(Optional.empty(), MediaFileRules.pixelCheck(10_000, 12_000));
            assertEquals(Optional.of(MediaStatusReason.TOO_MANY_PIXELS), MediaFileRules.pixelCheck(10_000, 12_001));
            assertEquals(Optional.empty(), MediaFileRules.pixelCheck(16_384, 7_000));
            assertEquals(Optional.of(MediaStatusReason.TOO_MANY_PIXELS), MediaFileRules.pixelCheck(16_385, 10));
            assertEquals(Optional.of(MediaStatusReason.TOO_MANY_PIXELS), MediaFileRules.pixelCheck(10, 16_385));
            assertEquals(Optional.of(MediaStatusReason.TOO_MANY_PIXELS), MediaFileRules.pixelCheck(70_000, 70_000));
        }

        @Test
        void phoneModes_50And64And108Megapixels_pass() {
            assertEquals(Optional.empty(), MediaFileRules.pixelCheck(8_160, 6_120));
            assertEquals(Optional.empty(), MediaFileRules.pixelCheck(9_248, 6_936));
            assertEquals(Optional.empty(), MediaFileRules.pixelCheck(12_000, 9_000));
        }
    }

    @Nested
    class MediaContentIdentityTest {

        @Test
        void sameHashSelectedInChecklist_isDuplicate() {
            SurveyMedia selected = processing(awaiting(EvidenceCategory.KITCHEN, MediaPlacement.space(SPACE_1, "A1"),
                    SALE, sha(7)));

            IdentityVerdict verdict = MediaContentIdentity.check(sha(7), null, List.of(selected), List.of());

            assertEquals(IdentityVerdict.Kind.DUPLICATE_CONTENT, verdict.kind());
            assertEquals(selected.getId().getValue(), verdict.matchingMediaIdOrNull());
        }

        @Test
        void sameHashOnlyRemovedOrRejectedOrFailedOrExpired_isFree() {
            SurveyMedia removed = ready(awaiting(EvidenceCategory.EXTERIOR_ACCESS, MediaPlacement.asset(), SALE,
                    sha(7)));
            removed.remove(RemovalKind.HOLDER, SALE, null, null, T0.plusSeconds(500));
            List<SurveyMedia> notSelected = List.of(removed, withSha(rejected(), 7), withSha(failed(), 7),
                    withSha(expired(), 7), withSha(cancelled(), 7));

            assertEquals(IdentityVerdict.Kind.FREE,
                    MediaContentIdentity.check(sha(7), null, notSelected, List.of()).kind());
        }

        @Test
        void selfAtCommit_isNoDuplicateOfItself() {
            SurveyMedia self = receiving();

            assertEquals(IdentityVerdict.Kind.FREE, MediaContentIdentity.check(self.getSourceSha256(),
                    self.getId().getValue(), List.of(self), List.of()).kind());
        }

        @Test
        void sameHashCommittedOnEarlierVisitOfLead_isOtherVisit() {
            SurveyMedia earlier = rejected();

            IdentityVerdict verdict = MediaContentIdentity.check(earlier.getSourceSha256(), null, List.of(),
                    List.of(earlier));

            assertEquals(IdentityVerdict.Kind.FROM_OTHER_VISIT, verdict.kind());
            assertEquals(earlier.getId().getValue(), verdict.matchingMediaIdOrNull());
        }

        @Test
        void sameHashNotCommittedOnEarlierVisit_isFree() {
            SurveyMedia earlier = expired();

            assertEquals(IdentityVerdict.Kind.FREE, MediaContentIdentity.check(earlier.getSourceSha256(), null,
                    List.of(), List.of(earlier)).kind());
        }

        @Test
        void duplicateBeforeOtherVisit_whenBoth() {
            SurveyMedia here = awaiting(EvidenceCategory.DETAIL, MediaPlacement.asset(), SALE, sha(9));
            SurveyMedia earlier = ready(awaiting(EvidenceCategory.DETAIL, MediaPlacement.asset(), SALE, sha(9)));

            assertEquals(IdentityVerdict.Kind.DUPLICATE_CONTENT,
                    MediaContentIdentity.check(sha(9), null, List.of(here), List.of(earlier)).kind());
        }

        @Test
        void otherHash_isFree() {
            assertEquals(IdentityVerdict.Kind.FREE,
                    MediaContentIdentity.check(sha(10), null, List.of(ready()), List.of(ready())).kind());
        }

        private SurveyMedia withSha(SurveyMedia media, int n) {
            return SurveyMedia.load(media.getId(), media.getChecklistId(), media.getUploadKey(), media.getMediaNo(),
                    media.getPlacement(), media.getCategory(), media.getCaption(), media.getUploadedByUserId(),
                    media.getCreatedAt(), media.getDeclaredSizeBytes(), sha(n), media.getStatus(),
                    media.getStatusReason(), media.getReceiveToken(), media.getReceiveKey(),
                    media.getReceiveStartedAt(), media.getContentReceivedAt(), media.getSourceStorageKey(),
                    media.getSourceDeletedAt(), media.getSourceFormat(), media.getReadyAt(), media.getFinishedAt(),
                    media.getRemoval(), media.getHold(), media.getContentDeleteRequestedAt(),
                    media.getContentDeletedAt(), media.getContentMissingAt(), media.getVariants(), 0L);
        }
    }

    @Nested
    class MediaLimitsTest {

        @Test
        void selection_100thAccepted_101stRefused() {
            assertEquals(Optional.empty(), MediaLimits.selectionBreach(99, 0));
            assertEquals(Optional.of("selected"), MediaLimits.selectionBreach(100, 0));
        }

        @Test
        void pending_20thAccepted_21stRefused() {
            assertEquals(Optional.empty(), MediaLimits.selectionBreach(50, 19));
            assertEquals(Optional.of("pending"), MediaLimits.selectionBreach(50, 20));
            assertEquals(Optional.of("selected"), MediaLimits.selectionBreach(100, 20));
        }

        @Test
        void hourly_300RecordsAnd3GiB() {
            assertEquals(Optional.empty(), MediaLimits.hourlyBreach(299, 0, 20_971_520L));
            assertEquals(Optional.of("records"), MediaLimits.hourlyBreach(300, 0, 1L));
            long threeGiB = 3L * 1024 * 1024 * 1024;
            assertEquals(Optional.empty(), MediaLimits.hourlyBreach(10, threeGiB - 1000, 1000));
            assertEquals(Optional.of("bytes"), MediaLimits.hourlyBreach(10, threeGiB - 1000, 1001));
        }

        @Test
        void bodies_threePerUser() {
            assertFalse(MediaLimits.bodiesFull(2));
            assertTrue(MediaLimits.bodiesFull(3));
        }

        @Test
        void expensive_twentyPerHour() {
            assertFalse(MediaLimits.expensiveReached(19));
            assertTrue(MediaLimits.expensiveReached(20));
        }
    }

    @Nested
    class MediaRemovalPolicyTest {

        @Test
        void committedPhotoOfAnotherUploader_needsCorrection() {
            assertTrue(MediaRemovalPolicy.correctionRequired(processing(), OTHER_SALE));
            assertTrue(MediaRemovalPolicy.correctionRequired(ready(), OTHER_SALE));
        }

        @Test
        void ownPhotoOrNoUsableContent_needsNone() {
            assertFalse(MediaRemovalPolicy.correctionRequired(ready(), SALE));
            for (SurveyMedia media : List.of(awaiting(), receiving(), rejected(), failed(), expired())) {
                assertFalse(MediaRemovalPolicy.correctionRequired(media, OTHER_SALE), media.getStatus().name());
            }
        }

        @Test
        void history_onlyForProcessingOrReady() {
            assertTrue(MediaRemovalPolicy.writesHistory(processing()));
            assertTrue(MediaRemovalPolicy.writesHistory(ready()));
            for (SurveyMedia media : List.of(awaiting(), receiving(), rejected(), failed(), expired())) {
                assertFalse(MediaRemovalPolicy.writesHistory(media), media.getStatus().name());
            }
        }
    }

    @Nested
    class PlacementRulesTest {

        private final IdentificationContent parts = identificationParts(observedSpace(SPACE_1, "P101"),
                observedSpace(SPACE_2, "P102"));

        @Test
        void spaceCategoryOnAKnownSpace_isAllowed() {
            assertEquals(Optional.empty(), PlacementRules.violation(EvidenceCategory.KITCHEN, SPACE_2, parts));
            assertEquals(Optional.empty(), PlacementRules.violation(EvidenceCategory.DETAIL, SPACE_1, parts));
            assertEquals(Optional.empty(), PlacementRules.violation(EvidenceCategory.DETAIL, null, parts));
        }

        @Test
        void spaceNotInG1_isRefusedOnSpaceId() {
            assertEquals(Optional.of("spaceId"), PlacementRules.violation(EvidenceCategory.MAIN_SPACE,
                    "01JSPACE000000000000000099", parts));
        }

        @Test
        void wrongLevel_isRefusedOnCategory() {
            assertEquals(Optional.of("category"),
                    PlacementRules.violation(EvidenceCategory.EXTERIOR_ACCESS, SPACE_1, parts));
            assertEquals(Optional.of("category"), PlacementRules.violation(EvidenceCategory.BATHROOM, null, parts));
            assertEquals(Optional.of("category"),
                    PlacementRules.violation(EvidenceCategory.SHARED_AMENITY, SPACE_1, parts));
        }

        @Test
        void sharedCategories_onlyInParts() {
            for (EvidenceCategory shared : List.of(EvidenceCategory.SHARED_KITCHEN, EvidenceCategory.SHARED_BATHROOM,
                    EvidenceCategory.SHARED_AMENITY)) {
                assertEquals(Optional.empty(), PlacementRules.violation(shared, null, parts));
                assertEquals(Optional.of("category"),
                        PlacementRules.violation(shared, null, identificationWhole()));
                assertEquals(Optional.of("category"),
                        PlacementRules.violation(shared, null, identification(null, List.of())));
            }
        }

        @Test
        void assetCategories_withoutScope_areAllowed() {
            assertEquals(Optional.empty(), PlacementRules.violation(EvidenceCategory.EXTERIOR_ACCESS, null,
                    identification(null, List.of())));
            assertEquals(Optional.empty(), PlacementRules.violation(EvidenceCategory.DETAIL, null,
                    identification(RentalScope.WHOLE, List.of())));
        }
    }

    @Nested
    class ReadBudgetArithmeticTest {

        private final Instant hour = Instant.parse("2026-11-01T02:00:00Z");

        @Test
        void hourStart_truncatesToTheHour() {
            assertEquals(hour, ReadBudgetArithmetic.hourStart(hour.plusSeconds(1799)));
        }

        @Test
        void effectiveLimit_atHourStartAndHalfPast() {
            assertEquals(600 - 400, ReadBudgetArithmetic.effectiveLimit(600, 400, hour));
            assertEquals(600 - 200, ReadBudgetArithmetic.effectiveLimit(600, 400, hour.plusSeconds(1800)));
            assertEquals(600 - 133, ReadBudgetArithmetic.effectiveLimit(600, 399, hour.plusSeconds(2400)));
            assertEquals(600, ReadBudgetArithmetic.effectiveLimit(600, 0, hour.plusSeconds(10)));
        }

        @Test
        void effectiveLimit_belowOrZero_refusesWithoutCounting() {
            long limit = ReadBudgetArithmetic.effectiveLimit(600, 1500, hour.plusSeconds(60));

            assertTrue(limit <= 0);
            assertTrue(ReadBudgetArithmetic.refusesWithoutCounting(limit));
            assertTrue(ReadBudgetArithmetic.refusesWithoutCounting(0));
            assertFalse(ReadBudgetArithmetic.refusesWithoutCounting(1));
        }
    }

    @Nested
    class KnownVersionTest {

        @Test
        void formatThenParse_roundTrips() {
            KnownVersion version = new KnownVersion(12, 3, 4, 5, 6, "0a1b2c3d");

            assertEquals("12.3.4.5.6.0a1b2c3d", version.format());
            assertEquals(Optional.of(version), KnownVersion.parse("12.3.4.5.6.0a1b2c3d"));
        }

        @Test
        void parse_malformed_isRefused() {
            for (String text : List.of("", "12.3.4.5.6", "x.3.4.5.6.0a1b2c3d", "12.x.4.5.6.0a1b2c3d",
                    "12.3.x.5.6.0a1b2c3d", "12.3.4.x.6.0a1b2c3d", "12.3.4.5.x.0a1b2c3d", "12.3.4.5.6.0a1b2c3",
                    "12.3.4.5.6.0a1b2c3dd", "12.3.4.5.6.0A1B2C3D", "12.3.4.5.6.0a1b2c3g", "-1.3.4.5.6.0a1b2c3d",
                    "12.3.4.5.6.7.0a1b2c3d", "99999999999999999999.3.4.5.6.0a1b2c3d", " 12.3.4.5.6.0a1b2c3d")) {
                assertEquals(Optional.empty(), KnownVersion.parse(text), text);
            }
            assertEquals(Optional.empty(), KnownVersion.parse(null));
        }

        @Test
        void viewDigest_changesWithEachInput() {
            String base = KnownVersion.viewDigest(null, true, false, true, false);

            assertEquals(8, base.length());
            assertTrue(base.matches("[0-9a-f]{8}"));
            assertEquals(base, KnownVersion.viewDigest(null, true, false, true, false));
            for (String other : List.of(KnownVersion.viewDigest("NEWER_VISIT", true, false, true, false),
                    KnownVersion.viewDigest(null, false, false, true, false),
                    KnownVersion.viewDigest(null, true, true, true, false),
                    KnownVersion.viewDigest(null, true, false, false, false),
                    KnownVersion.viewDigest(null, true, false, true, true))) {
                assertFalse(base.equals(other), other);
            }
            assertFalse(KnownVersion.viewDigest("NEWER_VISIT", true, false, true, false)
                    .equals(KnownVersion.viewDigest("LEAD_ON_HOLD", true, false, true, false)));
        }
    }
}
