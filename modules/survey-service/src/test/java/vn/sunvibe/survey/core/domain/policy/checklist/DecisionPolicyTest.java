package vn.sunvibe.survey.core.domain.policy.checklist;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.exception.checklist.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.model.checklist.DecisionOutcome;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceRejection;
import vn.sunvibe.survey.core.domain.model.checklist.FailedKind;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.NeedsChangesKind;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.model.media.HoldReason;
import vn.sunvibe.survey.core.domain.model.media.MediaPlacement;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.testsupport.ChecklistFixture;
import vn.sunvibe.survey.core.testsupport.MediaFixture;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules of a decision (survey-checklist submission spec 4.5, 7.3, S-7): R1 on the body, and R2..R6, which refuse
 * PASSED only; NEEDS_CHANGES and FAILED stay allowed.
 */
class DecisionPolicyTest {

    private static final String SPACE = ChecklistFixture.SPACE_1;
    private static final IdentificationContent G1 = ChecklistFixture.identificationWhole();

    private static int hash = 5000;

    /** One ready photo per required pair of a WHOLE record with one space (code A1). */
    private static List<SurveyMedia> covering() {
        List<SurveyMedia> photos = new ArrayList<>();
        photos.add(photo(EvidenceCategory.EXTERIOR_ACCESS, MediaPlacement.asset()));
        for (EvidenceCategory category : List.of(EvidenceCategory.MAIN_SPACE, EvidenceCategory.KITCHEN,
                EvidenceCategory.BATHROOM)) {
            photos.add(photo(category, MediaPlacement.space(SPACE, "A1")));
        }
        return photos;
    }

    private static SurveyMedia photo(EvidenceCategory category, MediaPlacement placement) {
        return MediaFixture.ready(MediaFixture.awaiting(category, placement, MediaFixture.SALE,
                MediaFixture.sha(++hash)));
    }

    private static DecisionFacts facts(List<PreviewWarning> warnings, List<SurveyMedia> frozen, Set<String> rejected,
                                       boolean scheduled) {
        return new DecisionFacts(warnings, frozen, rejected, scheduled, G1, null);
    }

    private static List<String> blockers(DecisionFacts facts) {
        return DecisionPolicy.passedBlockers(facts);
    }

    // ==================== R2..R6 ====================

    @Test
    void decision_everythingFine_noBlockers() {
        assertEquals(List.of(), blockers(facts(List.of(), covering(), Set.of(), false)));
    }

    @Test
    void decision_openImportantHazard_blocksPassedOnly() {
        List<String> blockers = blockers(facts(List.of(PreviewWarning.openHazard("01JHAZARD00000000000000001")),
                covering(), Set.of(), false));

        assertEquals(List.of("hazards"), blockers);
    }

    @Test
    void decision_earlierHazardOpen_blocksPassed() {
        assertEquals(List.of("hazards"), blockers(facts(List.of(PreviewWarning.earlierHazard(
                "01JCHECK000000000000000001", 1, "01JHAZARD00000000000000001")), covering(), Set.of(), false)));
    }

    /** spec 4.5 R2 names hazard warnings: a partly entered space warns but does not block. */
    @Test
    void decision_partialAccessWarning_blocksNothing() {
        assertEquals(List.of(), blockers(facts(List.of(PreviewWarning.partialAccess(SPACE)), covering(), Set.of(),
                false)));
    }

    @Test
    void decision_rejectedPhoto_blocksPassed() {
        List<SurveyMedia> frozen = covering();
        frozen.add(photo(EvidenceCategory.DETAIL, MediaPlacement.asset()));
        String extra = frozen.get(frozen.size() - 1).getId().getValue();

        assertEquals(List.of("evidence.rejected"), blockers(facts(List.of(), frozen, Set.of(extra), false)));
    }

    @Test
    void decision_heldFrozenPhoto_blocksPassed_allowsNeedsChanges() {
        List<SurveyMedia> frozen = covering();
        SurveyMedia extra = photo(EvidenceCategory.DETAIL, MediaPlacement.asset());
        MediaFixture.held(extra, HoldReason.SECURITY_INCIDENT);
        frozen.add(extra);

        assertEquals(List.of("evidence.unavailable"), blockers(facts(List.of(), frozen, Set.of(), false)));
        assertDoesNotThrow(() -> DecisionPolicy.checkBody(DecisionOutcome.NEEDS_CHANGES,
                NeedsChangesKind.DOCUMENTS_ONLY, null, "Thiếu giấy tờ", null, List.of()));
    }

    @Test
    void decision_lostContent_blocksPassed() {
        List<SurveyMedia> frozen = covering();
        SurveyMedia extra = photo(EvidenceCategory.DETAIL, MediaPlacement.asset());
        extra.markContentMissing(MediaFixture.T0.plusSeconds(600));
        frozen.add(extra);

        assertEquals(List.of("evidence.unavailable"), blockers(facts(List.of(), frozen, Set.of(), false)));
    }

    @Test
    void decision_scheduledAppointment_blocksPassed() {
        assertEquals(List.of("appointment"), blockers(facts(List.of(), covering(), Set.of(), true)));
    }

    @Test
    void decision_pairCoveredOnlyByRejected_evidenceCoverage() {
        List<SurveyMedia> frozen = covering();
        String exterior = frozen.get(0).getId().getValue();

        List<String> blockers = blockers(facts(List.of(), frozen, Set.of(exterior), false));

        assertEquals(List.of("evidence.rejected", "evidence.coverage"), blockers);
    }

    @Test
    void decision_pairAlsoCoveredByAnAcceptedPhoto_noCoverageBlocker() {
        List<SurveyMedia> frozen = covering();
        frozen.add(photo(EvidenceCategory.EXTERIOR_ACCESS, MediaPlacement.asset()));
        String rejected = frozen.get(0).getId().getValue();

        assertEquals(List.of("evidence.rejected"), blockers(facts(List.of(), frozen, Set.of(rejected), false)));
    }

    @Test
    void decision_everyBlocker_inTheOrderOfSpec45() {
        List<SurveyMedia> frozen = covering();
        MediaFixture.held(frozen.get(1), HoldReason.SECURITY_INCIDENT);

        assertEquals(List.of("hazards", "evidence.rejected", "evidence.unavailable", "appointment",
                "evidence.coverage"), blockers(facts(List.of(PreviewWarning.openHazard("01JHAZARD00000000000000001")),
                frozen, Set.of(frozen.get(0).getId().getValue()), true)));
    }

    // ==================== R1 ====================

    private static List<String> bodyPaths(DecisionOutcome outcome, NeedsChangesKind needsChanges, FailedKind failed,
                                          String reason, String limitations) {
        ChecklistValidationException refused = assertThrows(ChecklistValidationException.class,
                () -> DecisionPolicy.checkBody(outcome, needsChanges, failed, reason, limitations, List.of()));
        return refused.violations().stream().map(Violation::path).toList();
    }

    @Test
    void body_passedWithOnlyLimitations_accepted() {
        assertDoesNotThrow(() -> DecisionPolicy.checkBody(DecisionOutcome.PASSED, null, null, null, "Chưa xem sổ",
                List.of()));
    }

    @Test
    void body_noOutcome_refused() {
        assertEquals(List.of("outcome"), bodyPaths(null, null, null, null, null));
    }

    @Test
    void body_passedWithAKind_refused() {
        assertEquals(List.of("needsChangesKind"), bodyPaths(DecisionOutcome.PASSED, NeedsChangesKind.DOCUMENTS_ONLY,
                null, null, null));
        assertEquals(List.of("failedKind"), bodyPaths(DecisionOutcome.PASSED, null, FailedKind.TEMPORARY, null, null));
    }

    @Test
    void body_needsChangesWithoutItsKindOrReason_refused() {
        assertEquals(List.of("needsChangesKind"), bodyPaths(DecisionOutcome.NEEDS_CHANGES, null, null, "Lý do", null));
        assertEquals(List.of("reason"), bodyPaths(DecisionOutcome.NEEDS_CHANGES, NeedsChangesKind.DOCUMENTS_ONLY, null,
                null, null));
    }

    @Test
    void body_failedWithoutItsKindOrReason_refused() {
        assertEquals(List.of("failedKind"), bodyPaths(DecisionOutcome.FAILED, null, null, "Lý do", null));
        assertEquals(List.of("reason"), bodyPaths(DecisionOutcome.FAILED, null, FailedKind.NOT_ACCEPTED, null, null));
    }

    @Test
    void body_otherOutcomesKind_refused() {
        assertEquals(List.of("failedKind"), bodyPaths(DecisionOutcome.NEEDS_CHANGES, NeedsChangesKind.DOCUMENTS_ONLY,
                FailedKind.TEMPORARY, "Lý do", null));
        assertEquals(List.of("needsChangesKind"), bodyPaths(DecisionOutcome.FAILED, NeedsChangesKind.DOCUMENTS_ONLY,
                FailedKind.TEMPORARY, "Lý do", null));
    }

    @Test
    void body_limitationsOnlyWithPassed() {
        assertEquals(List.of("limitations"), bodyPaths(DecisionOutcome.NEEDS_CHANGES,
                NeedsChangesKind.DOCUMENTS_ONLY, null, "Lý do", "Giới hạn"));
        assertEquals(List.of("limitations"), bodyPaths(DecisionOutcome.FAILED, null, FailedKind.TEMPORARY, "Lý do",
                "Giới hạn"));
    }

    @Test
    void body_samePhotoMarkedTwice_refused() {
        String media = "01JMED1A000000000000000001";

        ChecklistValidationException refused = assertThrows(ChecklistValidationException.class,
                () -> DecisionPolicy.checkBody(DecisionOutcome.PASSED, null, null, null, null, List.of(
                        new EvidenceRejection(media, "Mờ"), new EvidenceRejection(media, "Tối"))));

        assertEquals("rejectedEvidence.1.mediaId", refused.violations().get(0).path());
    }

    @Test
    void rejections_onlyAmongFrozenIds() {
        String frozen = "01JMED1A000000000000000001";
        String other = "01JMED1A000000000000000002";

        assertDoesNotThrow(() -> DecisionPolicy.checkRejections(List.of(new EvidenceRejection(frozen, "Mờ")),
                Set.of(frozen)));
        ChecklistValidationException refused = assertThrows(ChecklistValidationException.class,
                () -> DecisionPolicy.checkRejections(List.of(new EvidenceRejection(frozen, "Mờ"),
                        new EvidenceRejection(other, "Tối")), Set.of(frozen)));
        assertEquals("rejectedEvidence.1.mediaId", refused.violations().get(0).path());
        assertTrue(refused.violations().get(0).message().contains("frozen"));
    }
}
