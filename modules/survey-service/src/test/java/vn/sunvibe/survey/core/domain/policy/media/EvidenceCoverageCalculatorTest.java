package vn.sunvibe.survey.core.domain.policy.media;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.checklist.AreaCheck;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Field;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.RentalScope;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.model.media.HoldReason;
import vn.sunvibe.survey.core.domain.model.media.MediaPlacement;
import vn.sunvibe.survey.core.domain.model.media.RemovalKind;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.core.domain.model.media.EvidenceCategory.BATHROOM;
import static vn.sunvibe.survey.core.domain.model.media.EvidenceCategory.DETAIL;
import static vn.sunvibe.survey.core.domain.model.media.EvidenceCategory.EXTERIOR_ACCESS;
import static vn.sunvibe.survey.core.domain.model.media.EvidenceCategory.KITCHEN;
import static vn.sunvibe.survey.core.domain.model.media.EvidenceCategory.MAIN_SPACE;
import static vn.sunvibe.survey.core.domain.model.media.EvidenceCategory.SHARED_AMENITY;
import static vn.sunvibe.survey.core.domain.model.media.EvidenceCategory.SHARED_BATHROOM;
import static vn.sunvibe.survey.core.domain.model.media.EvidenceCategory.SHARED_KITCHEN;
import static vn.sunvibe.survey.core.domain.policy.media.CoverageState.COVERED;
import static vn.sunvibe.survey.core.domain.policy.media.CoverageState.MISSING;
import static vn.sunvibe.survey.core.domain.policy.media.CoverageState.NOT_REQUIRED;
import static vn.sunvibe.survey.core.domain.policy.media.CoverageState.OPTIONAL;
import static vn.sunvibe.survey.core.domain.policy.media.CoverageState.PROCESSING;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_1;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_2;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.condition;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.conditionParts;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identification;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identificationParts;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identificationWhole;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.inspected;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.observedSpace;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.ADMIN;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.SALE;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.awaiting;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.processing;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.ready;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.sha;

/** Evidence coverage of a checklist (survey-media spec 4.6, A-12, A-23; SM-10). */
class EvidenceCoverageCalculatorTest {

    private static final Instant LATER = Instant.parse("2026-11-01T03:00:00Z");
    private static int hash = 1000;

    private static final IdentificationContent PARTS_TWO_SPACES = identificationParts(
            observedSpace(SPACE_1, "P101"), observedSpace(SPACE_2, "P102"));

    @Test
    void wholeWithKitchenNa_exactPairsAndMissing() {
        ConditionContent g4 = new ConditionContent(null, null, List.of(withKitchen(SPACE_1,
                Field.notApplicable("Không có bếp"))));

        EvidenceCoverage coverage = EvidenceCoverageCalculator.of(identificationWhole(), g4, List.of());

        assertEquals(List.of(
                pair(null, EXTERIOR_ACCESS, true, null, MISSING),
                pair(null, DETAIL, false, null, OPTIONAL),
                pair(SPACE_1, MAIN_SPACE, true, null, MISSING),
                pair(SPACE_1, KITCHEN, false, "Không có bếp", NOT_REQUIRED),
                pair(SPACE_1, BATHROOM, true, null, MISSING),
                pair(SPACE_1, DETAIL, false, null, OPTIONAL)), shape(coverage.pairs()));
        assertEquals(List.of(key(null, EXTERIOR_ACCESS), key(SPACE_1, MAIN_SPACE), key(SPACE_1, BATHROOM)),
                keys(coverage.missing()));
    }

    @Test
    void partsTwoSpacesWithSharedWetAreas_pairsInTableOrder() {
        EvidenceCoverage coverage = EvidenceCoverageCalculator.of(PARTS_TWO_SPACES,
                conditionParts(SPACE_1, SPACE_2), List.of());

        assertEquals(List.of(
                pair(null, EXTERIOR_ACCESS, true, null, MISSING),
                pair(null, SHARED_KITCHEN, true, null, MISSING),
                pair(null, SHARED_BATHROOM, false, "Không có phòng tắm chung", NOT_REQUIRED),
                pair(null, SHARED_AMENITY, false, null, OPTIONAL),
                pair(null, DETAIL, false, null, OPTIONAL),
                pair(SPACE_1, MAIN_SPACE, true, null, MISSING),
                pair(SPACE_1, KITCHEN, true, null, MISSING),
                pair(SPACE_1, BATHROOM, true, null, MISSING),
                pair(SPACE_1, DETAIL, false, null, OPTIONAL),
                pair(SPACE_2, MAIN_SPACE, true, null, MISSING),
                pair(SPACE_2, KITCHEN, true, null, MISSING),
                pair(SPACE_2, BATHROOM, true, null, MISSING),
                pair(SPACE_2, DETAIL, false, null, OPTIONAL)), shape(coverage.pairs()));
    }

    @Test
    void orderOfMissing_assetFirstThenSpaceOrderCategoriesInTableOrder() {
        SurveyMedia exterior = ready(photo(EXTERIOR_ACCESS, MediaPlacement.asset()));
        SurveyMedia mainSpace2 = processing(photo(MAIN_SPACE, MediaPlacement.space(SPACE_2, "P102")));

        EvidenceCoverage coverage = EvidenceCoverageCalculator.of(PARTS_TWO_SPACES,
                conditionParts(SPACE_1, SPACE_2), List.of(exterior, mainSpace2));

        assertEquals(List.of(key(null, SHARED_KITCHEN), key(SPACE_1, MAIN_SPACE), key(SPACE_1, KITCHEN),
                key(SPACE_1, BATHROOM), key(SPACE_2, MAIN_SPACE), key(SPACE_2, KITCHEN), key(SPACE_2, BATHROOM)),
                keys(coverage.missing()));
        assertEquals(PROCESSING, find(coverage, SPACE_2, MAIN_SPACE).state());
        assertEquals(COVERED, find(coverage, null, EXTERIOR_ACCESS).state());
    }

    @Test
    void counts_readyUsableAndProcessingNotHeld() {
        SurveyMedia a = ready(photo(KITCHEN, MediaPlacement.space(SPACE_1, "P101")));
        SurveyMedia b = ready(photo(KITCHEN, MediaPlacement.space(SPACE_1, "P101")));
        SurveyMedia c = processing(photo(KITCHEN, MediaPlacement.space(SPACE_1, "P101")));
        SurveyMedia otherSpace = ready(photo(KITCHEN, MediaPlacement.space(SPACE_2, "P102")));

        CoveragePair pair = find(EvidenceCoverageCalculator.of(PARTS_TWO_SPACES, conditionParts(SPACE_1, SPACE_2),
                List.of(a, b, c, otherSpace)), SPACE_1, KITCHEN);

        assertEquals(2, pair.readyCount());
        assertEquals(1, pair.processingCount());
        assertEquals(COVERED, pair.state());
    }

    @Test
    void unknownG4_keepsRequired() {
        ConditionContent unknown = new ConditionContent(Field.unknown(), Field.unknown(), List.of(
                withKitchen(SPACE_1, Field.unknown()), withKitchen(SPACE_2, Field.unknown())));

        EvidenceCoverage coverage = EvidenceCoverageCalculator.of(PARTS_TWO_SPACES, unknown, List.of());

        assertTrue(find(coverage, SPACE_1, KITCHEN).required());
        assertTrue(find(coverage, null, SHARED_KITCHEN).required());
        assertTrue(find(coverage, null, SHARED_BATHROOM).required());
        assertTrue(EvidenceCoverageCalculator.of(PARTS_TWO_SPACES, null, List.of())
                .pairs().stream().filter(p -> p.category() == KITCHEN).allMatch(CoveragePair::required));
    }

    @Test
    void noScope_onlyExteriorAccess() {
        EvidenceCoverage coverage = EvidenceCoverageCalculator.of(identification(null,
                List.of(observedSpace(SPACE_1, "P101"))), condition(SPACE_1), List.of());

        assertEquals(List.of(pair(null, EXTERIOR_ACCESS, true, null, MISSING), pair(null, DETAIL, false, null,
                OPTIONAL)), shape(coverage.pairs()));
    }

    @Test
    void partsWithoutSpaces_onlyExteriorAccessIsRequired() {
        EvidenceCoverage coverage = EvidenceCoverageCalculator.of(identification(RentalScope.PARTS, List.of()),
                conditionParts(), List.of());

        assertEquals(List.of(key(null, EXTERIOR_ACCESS)), keys(coverage.missing()));
        assertFalse(find(coverage, null, SHARED_KITCHEN).required());
        assertNull(find(coverage, null, SHARED_KITCHEN).notRequiredReasonOrNull());
        assertEquals(NOT_REQUIRED, find(coverage, null, SHARED_KITCHEN).state());
    }

    @Test
    void heldOnlyPhoto_isMissing() {
        SurveyMedia held = ready(photo(EXTERIOR_ACCESS, MediaPlacement.asset()));
        held.hold(ADMIN, HoldReason.SECURITY_INCIDENT, LATER);

        CoveragePair pair = find(EvidenceCoverageCalculator.of(identificationWhole(), condition(SPACE_1),
                List.of(held)), null, EXTERIOR_ACCESS);

        assertEquals(MISSING, pair.state());
        assertEquals(0, pair.readyCount());
    }

    @Test
    void heldProcessingOnly_isMissingNotProcessing() {
        SurveyMedia held = processing(photo(EXTERIOR_ACCESS, MediaPlacement.asset()));
        held.hold(ADMIN, HoldReason.SECURITY_INCIDENT, LATER);

        CoveragePair pair = find(EvidenceCoverageCalculator.of(identificationWhole(), condition(SPACE_1),
                List.of(held)), null, EXTERIOR_ACCESS);

        assertEquals(MISSING, pair.state());
        assertEquals(0, pair.processingCount());
    }

    @Test
    void removedOnlyPhoto_isMissing_uploadingDoesNotCount() {
        SurveyMedia removed = ready(photo(EXTERIOR_ACCESS, MediaPlacement.asset()));
        removed.remove(RemovalKind.HOLDER, SALE, null, null, LATER);
        SurveyMedia uploading = photo(EXTERIOR_ACCESS, MediaPlacement.asset());

        CoveragePair pair = find(EvidenceCoverageCalculator.of(identificationWhole(), condition(SPACE_1),
                List.of(removed, uploading)), null, EXTERIOR_ACCESS);

        assertEquals(MISSING, pair.state());
    }

    @Test
    void contentMissingPhoto_isNotUsable() {
        SurveyMedia missing = ready(photo(EXTERIOR_ACCESS, MediaPlacement.asset()));
        missing.markContentMissing(LATER);

        assertEquals(MISSING, find(EvidenceCoverageCalculator.of(identificationWhole(), condition(SPACE_1),
                List.of(missing)), null, EXTERIOR_ACCESS).state());
    }

    @Test
    void partsToNullScope_dropsSharedPairs() {
        List<SurveyMedia> photos = List.of(ready(photo(SHARED_KITCHEN, MediaPlacement.asset())));

        EvidenceCoverage parts = EvidenceCoverageCalculator.of(PARTS_TWO_SPACES, conditionParts(SPACE_1, SPACE_2),
                photos);
        EvidenceCoverage none = EvidenceCoverageCalculator.of(identification(null, List.of()), null, photos);

        assertEquals(COVERED, find(parts, null, SHARED_KITCHEN).state());
        assertTrue(none.pairs().stream().noneMatch(p -> p.category().name().startsWith("SHARED_")));
    }

    @Test
    void optionalCategories_neverSatisfyARequiredOne() {
        SurveyMedia detail = ready(photo(DETAIL, MediaPlacement.space(SPACE_1, "A1")));

        EvidenceCoverage coverage = EvidenceCoverageCalculator.of(identificationWhole(), condition(SPACE_1),
                List.of(detail));

        assertEquals(MISSING, find(coverage, SPACE_1, MAIN_SPACE).state());
        assertEquals(OPTIONAL, find(coverage, SPACE_1, DETAIL).state());
        assertEquals(1, find(coverage, SPACE_1, DETAIL).readyCount());
    }

    private static ConditionSpace withKitchen(String spaceId, Field<AreaCheck> kitchen) {
        ConditionSpace base = condition(spaceId).spaces().get(0);
        return new ConditionSpace(spaceId, base.occupancy(), base.expectedVacantFrom(), base.availableFrom(),
                Field.value(inspected()), kitchen, base.bathroom());
    }

    private static SurveyMedia photo(EvidenceCategory category, MediaPlacement placement) {
        return awaiting(category, placement, SALE, sha(++hash));
    }

    private static CoveragePair find(EvidenceCoverage coverage, String spaceId, EvidenceCategory category) {
        return coverage.pairs().stream()
                .filter(p -> java.util.Objects.equals(p.spaceIdOrNull(), spaceId) && p.category() == category)
                .findFirst().orElseThrow(() -> new AssertionError("no pair " + spaceId + " " + category));
    }

    private static String key(String spaceId, EvidenceCategory category) {
        return (spaceId == null ? "asset" : spaceId) + "/" + category;
    }

    private static List<String> keys(List<CoveragePair> pairs) {
        return pairs.stream().map(p -> key(p.spaceIdOrNull(), p.category())).toList();
    }

    private static String pair(String spaceId, EvidenceCategory category, boolean required, String reason,
                               CoverageState state) {
        return key(spaceId, category) + " required=" + required + " reason=" + reason + " " + state;
    }

    private static List<String> shape(List<CoveragePair> pairs) {
        List<String> shapes = new ArrayList<>();
        for (CoveragePair p : pairs) {
            shapes.add(pair(p.spaceIdOrNull(), p.category(), p.required(), p.notRequiredReasonOrNull(), p.state()));
        }
        return shapes;
    }
}
