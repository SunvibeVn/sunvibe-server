package vn.sunvibe.survey.core.domain.policy.checklist;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.checklist.AccessExtent;
import vn.sunvibe.survey.core.domain.model.checklist.ActualAccess;
import vn.sunvibe.survey.core.domain.model.checklist.AreaCheck;
import vn.sunvibe.survey.core.domain.model.checklist.AreaCheckResult;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeOverrides;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeUnit;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionSpace;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsContent;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Field;
import vn.sunvibe.survey.core.domain.model.checklist.Fit;
import vn.sunvibe.survey.core.domain.model.checklist.FitAssessment;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.Hazard;
import vn.sunvibe.survey.core.domain.model.checklist.HazardSeverity;
import vn.sunvibe.survey.core.domain.model.checklist.HazardStatus;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.PartsContent;
import vn.sunvibe.survey.core.domain.model.checklist.PartsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.TermsCommon;
import vn.sunvibe.survey.core.domain.model.checklist.TermsContent;
import vn.sunvibe.survey.core.domain.model.checklist.TermsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.YesNo;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupDocument;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.model.media.HoldReason;
import vn.sunvibe.survey.core.domain.model.media.MediaPlacement;
import vn.sunvibe.survey.core.domain.model.media.RemovalKind;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.policy.media.CoverageState;
import vn.sunvibe.survey.core.domain.policy.media.EvidenceCoverageCalculator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.core.domain.policy.checklist.MissingProblem.DIFFERENCE_NOTE_MISSING;
import static vn.sunvibe.survey.core.domain.model.media.EvidenceCategory.BATHROOM;
import static vn.sunvibe.survey.core.domain.model.media.EvidenceCategory.DETAIL;
import static vn.sunvibe.survey.core.domain.model.media.EvidenceCategory.EXTERIOR_ACCESS;
import static vn.sunvibe.survey.core.domain.model.media.EvidenceCategory.KITCHEN;
import static vn.sunvibe.survey.core.domain.model.media.EvidenceCategory.MAIN_SPACE;
import static vn.sunvibe.survey.core.domain.model.media.EvidenceCategory.SHARED_BATHROOM;
import static vn.sunvibe.survey.core.domain.model.media.EvidenceCategory.SHARED_KITCHEN;
import static vn.sunvibe.survey.core.domain.policy.checklist.MissingProblem.EVIDENCE_MISSING;
import static vn.sunvibe.survey.core.domain.policy.checklist.MissingProblem.EVIDENCE_PENDING;
import static vn.sunvibe.survey.core.domain.policy.checklist.MissingProblem.EVIDENCE_PROCESSING;
import static vn.sunvibe.survey.core.domain.policy.checklist.MissingProblem.EVIDENCE_UNAVAILABLE;
import static vn.sunvibe.survey.core.domain.policy.checklist.MissingProblem.NOT_CHECKED;
import static vn.sunvibe.survey.core.domain.policy.checklist.MissingProblem.NOT_IN_SCOPE;
import static vn.sunvibe.survey.core.domain.policy.checklist.MissingProblem.NO_SPACES;
import static vn.sunvibe.survey.core.domain.policy.checklist.MissingProblem.REQUIRED_AREA_NOT_ENTERED;
import static vn.sunvibe.survey.core.domain.policy.checklist.MissingProblem.SCOPE_MISSING;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_1;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_2;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.amenities;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.charge;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.condition;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.conditionParts;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.conditionSpace;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.dimensions;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identification;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identificationParts;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identificationWhole;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.observedSpace;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.ownerSaid;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.parts;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.partsSpace;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.safety;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.terms;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.termsCommon;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.termsSpace;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.ADMIN;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.SALE;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.awaiting;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.processing;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.ready;
import static vn.sunvibe.survey.core.testsupport.MediaFixture.sha;

/** Missing items, unconfirmed terms and warnings, computed on every read (survey-checklist spec 4.7; W16-18). */
class ReadinessCalculatorTest {

    private static final String H1 = "01JHAZARD00000000000000001";
    private static final IdentificationContent WHOLE = identificationWhole();
    private static final Instant LATER = Instant.parse("2026-11-01T03:00:00Z");
    private static int hash = 2000;

    @Test
    void newDraft_listsEveryNeededFieldScopeSpacesAndEvidence() {
        Readiness readiness = ReadinessCalculator.of(initialGroups(), LocationDifference.NONE, List.of());

        assertEquals(List.of(
                item(ChecklistGroup.IDENTIFICATION, null, "observedAddress", NOT_CHECKED),
                item(ChecklistGroup.IDENTIFICATION, null, "propertyType", NOT_CHECKED),
                item(ChecklistGroup.IDENTIFICATION, null, "fit", NOT_CHECKED),
                item(ChecklistGroup.IDENTIFICATION, null, "rentalScope", SCOPE_MISSING),
                item(ChecklistGroup.IDENTIFICATION, null, "spaces", NO_SPACES),
                item(ChecklistGroup.PARTS, null, "accessRoute", NOT_CHECKED),
                item(ChecklistGroup.PARTS, null, "sharedAreas", NOT_CHECKED),
                item(ChecklistGroup.SAFETY, null, "egress", NOT_CHECKED),
                item(ChecklistGroup.SAFETY, null, "accessRestrictions", NOT_CHECKED),
                item(ChecklistGroup.SAFETY, null, "hazards", NOT_CHECKED),
                item(ChecklistGroup.EVIDENCE, null, null, EVIDENCE_PENDING)), readiness.missing());
        assertFalse(readiness.submissionAvailable());
        assertEquals("EVIDENCE_NOT_AVAILABLE", readiness.submissionUnavailableReason());
        assertEquals(11, readiness.missingCount());
    }

    @Test
    void completeDraft_onlyEvidencePending() {
        Readiness readiness = ReadinessCalculator.of(complete(), LocationDifference.NONE, List.of());

        assertEquals(List.of(item(ChecklistGroup.EVIDENCE, null, null, EVIDENCE_PENDING)), readiness.missing());
        assertEquals(List.of(), readiness.unconfirmedTerms());
        assertEquals(List.of(), readiness.warnings());
    }

    @Test
    void spaceWithoutEntries_listsEveryNeededFieldOfThatSpace() {
        Map<ChecklistGroup, GroupDocument> groups = complete();
        groups.put(ChecklistGroup.DIMENSIONS, document(new DimensionsContent(List.of())));

        assertEquals(List.of("area", "bedrooms", "privateBathrooms", "sharedBathroomUse"),
                ReadinessCalculator.of(groups, LocationDifference.NONE, List.of()).missing().stream()
                        .filter(item -> item.group() == ChecklistGroup.DIMENSIONS)
                        .map(MissingItem::fieldOrNull).toList());
    }

    /** US-4 AC-3: out of scope is kept; the type may stay unknown, still listed. */
    @Test
    void outOfScope_isNotInScopeAndTheUnknownTypeStaysListed() {
        Map<ChecklistGroup, GroupDocument> groups = complete();
        groups.put(ChecklistGroup.IDENTIFICATION, document(new IdentificationContent(WHOLE.observedAddress(), null,
                Field.unknown(), Field.value(new FitAssessment(Fit.OUT_OF_SCOPE, "Văn phòng")), WHOLE.rentalScope(),
                WHOLE.spaces(), null, null)));

        assertEquals(List.of(item(ChecklistGroup.IDENTIFICATION, null, "propertyType", NOT_CHECKED),
                        item(ChecklistGroup.IDENTIFICATION, null, "fit", NOT_IN_SCOPE),
                        item(ChecklistGroup.EVIDENCE, null, null, EVIDENCE_PENDING)),
                ReadinessCalculator.of(groups, LocationDifference.NONE, List.of()).missing());
    }

    @Test
    void addressDifferentFromTheLead_withoutNote_isMissingItsNote() {
        assertTrue(ReadinessCalculator.of(complete(), new LocationDifference(true, false), List.of()).missing()
                .contains(item(ChecklistGroup.IDENTIFICATION, null, "differenceNote", DIFFERENCE_NOTE_MISSING)));
        assertFalse(ReadinessCalculator.of(complete(), new LocationDifference(false, true), List.of()).missing()
                .contains(item(ChecklistGroup.IDENTIFICATION, null, "differenceNote", DIFFERENCE_NOTE_MISSING)));
    }

    /** W16-10, W16-42: a kitchen not or only partly entered stays missing whatever note or owner word it has. */
    @Test
    void notEnteredKitchenWithANote_isARequiredAreaNotEntered() {
        Map<ChecklistGroup, GroupDocument> groups = complete();
        ConditionSpace s = conditionSpace(SPACE_1);
        groups.put(ChecklistGroup.CONDITION, document(new ConditionContent(null, null, List.of(new ConditionSpace(
                SPACE_1, s.occupancy(), s.expectedVacantFrom(), s.availableFrom(), s.mainSpace(), Field.value(
                new AreaCheck(AreaCheckResult.NOT_ENTERED, null, "Chủ nói bếp mới sửa", "Chỉ nhìn từ cửa")),
                s.bathroom())))));

        assertEquals(List.of(item(ChecklistGroup.CONDITION, SPACE_1, "kitchen", REQUIRED_AREA_NOT_ENTERED),
                        item(ChecklistGroup.EVIDENCE, null, null, EVIDENCE_PENDING)),
                ReadinessCalculator.of(groups, LocationDifference.NONE, List.of()).missing());
    }

    /** W16-42: owner-stated area and counts complete G3 while a NOT_ENTERED main space stays missing. */
    @Test
    void ownerStatedDimensions_completeG3ButNotTheMainSpaceCheck() {
        Map<ChecklistGroup, GroupDocument> groups = complete();
        LocalDate said = LocalDate.parse("2026-10-15");
        groups.put(ChecklistGroup.DIMENSIONS, document(new DimensionsContent(List.of(new DimensionsSpace(SPACE_1,
                ownerSaid(new BigDecimal("60.0"), said), ownerSaid(2, said), ownerSaid(1, said),
                ownerSaid(YesNo.NO, said), null)))));
        ConditionSpace s = conditionSpace(SPACE_1);
        groups.put(ChecklistGroup.CONDITION, document(new ConditionContent(null, null, List.of(new ConditionSpace(
                SPACE_1, Field.unknown(), s.expectedVacantFrom(), s.availableFrom(), Field.value(new AreaCheck(
                AreaCheckResult.NOT_ENTERED, null, null, "Khách đang ở")), s.kitchen(), s.bathroom())))));

        List<MissingItem> missing = ReadinessCalculator.of(groups, LocationDifference.NONE, List.of()).missing();

        assertEquals(List.of(item(ChecklistGroup.CONDITION, SPACE_1, "occupancy", NOT_CHECKED),
                item(ChecklistGroup.CONDITION, SPACE_1, "mainSpace", REQUIRED_AREA_NOT_ENTERED),
                item(ChecklistGroup.EVIDENCE, null, null, EVIDENCE_PENDING)), missing);
    }

    @Test
    void partsScope_needsTheSharedWetAreas() {
        Map<ChecklistGroup, GroupDocument> groups = completeParts();
        groups.put(ChecklistGroup.CONDITION, document(condition(SPACE_1, SPACE_2)));

        assertEquals(List.of(item(ChecklistGroup.CONDITION, null, "sharedKitchen", NOT_CHECKED),
                        item(ChecklistGroup.CONDITION, null, "sharedBathroom", NOT_CHECKED),
                        item(ChecklistGroup.EVIDENCE, null, null, EVIDENCE_PENDING)),
                ReadinessCalculator.of(groups, LocationDifference.NONE, List.of()).missing());
        assertEquals(List.of(item(ChecklistGroup.EVIDENCE, null, null, EVIDENCE_PENDING)),
                ReadinessCalculator.of(completeParts(), LocationDifference.NONE, List.of()).missing());
    }

    /** US-2 AC-2, W16-11: unknown terms are listed; a common charge is one item unless every space overrides it. */
    @Test
    void unconfirmedTerms_perSpaceCommonAndAvailableFrom() {
        Map<ChecklistGroup, GroupDocument> groups = completeParts();
        TermsCommon c = termsCommon();
        groups.put(ChecklistGroup.TERMS, document(new TermsContent(new TermsCommon(SourcedField.unknown(),
                SourcedField.unknown(), c.internet(), c.parking(), c.serviceFee(), c.otherFees(), SourcedField.unknown(),
                c.pets(), c.handoverMaintenance()), List.of(
                termsSpace(SPACE_1, new ChargeOverrides(charge(4_000, ChargeUnit.PER_KWH), null, null, null, null), null),
                new TermsSpace(SPACE_2, SourcedField.unknown(),
                        termsSpace(SPACE_2, null, null).deposit(), termsSpace(SPACE_2, null, null).paymentPeriodMonths(),
                        termsSpace(SPACE_2, null, null).minimumTermMonths(),
                        new ChargeOverrides(charge(4_500, ChargeUnit.PER_KWH), null, null, null, null), null)))));
        ConditionContent parts = conditionParts(SPACE_1, SPACE_2);
        ConditionSpace s2 = parts.spaces().get(1);
        groups.put(ChecklistGroup.CONDITION, document(new ConditionContent(parts.sharedKitchen(), parts.sharedBathroom(),
                List.of(parts.spaces().get(0), new ConditionSpace(SPACE_2, s2.occupancy(), s2.expectedVacantFrom(),
                        SourcedField.unknown(), s2.mainSpace(), s2.kitchen(), s2.bathroom())))));

        assertEquals(List.of(new UnconfirmedTerm(ChecklistGroup.CONDITION, SPACE_2, "availableFrom"),
                        new UnconfirmedTerm(ChecklistGroup.TERMS, null, "water"),
                        new UnconfirmedTerm(ChecklistGroup.TERMS, null, "houseRules"),
                        new UnconfirmedTerm(ChecklistGroup.TERMS, SPACE_2, "monthlyRent")),
                ReadinessCalculator.of(groups, LocationDifference.NONE, List.of()).unconfirmedTerms());
    }

    @Test
    void warnings_openImportantHazardsPartialAccessAndEarlierHazards() {
        Map<ChecklistGroup, GroupDocument> groups = complete();
        groups.put(ChecklistGroup.SAFETY, document(safety(
                hazard(H1, HazardSeverity.UNCLASSIFIED, HazardStatus.OWNER_REPORTED_FIXED),
                hazard("01JHAZARD00000000000000002", HazardSeverity.IMPORTANT, HazardStatus.OBSERVED_RESOLVED),
                hazard("01JHAZARD00000000000000003", HazardSeverity.MINOR, HazardStatus.OPEN))));
        PartsSpace s = partsSpace(SPACE_1);
        groups.put(ChecklistGroup.PARTS, document(new PartsContent(parts().accessRoute(), parts().sharedAreas(),
                parts().totalFloors(), List.of(new PartsSpace(SPACE_1, s.floor(), s.privateAreas(),
                Field.value(new ActualAccess(AccessExtent.ENTERED_PART, "Phòng ngủ khóa")))))));
        Hazard earlier = hazard("01JHAZARD00000000000000009", HazardSeverity.IMPORTANT, HazardStatus.OPEN);
        List<EarlierHazard> chain = List.of(new EarlierHazard("01JCHECK000000000000000001", 1, earlier, List.of()),
                new EarlierHazard("01JCHECK000000000000000001", 1, hazard("01JHAZARD00000000000000008",
                        HazardSeverity.IMPORTANT, HazardStatus.OPEN), List.of(new AddressedBy(
                        "01JCHECK000000000000000002", 2, H1, AddressKind.CARRIED))));

        assertEquals(List.of(PreviewWarning.openHazard(H1), PreviewWarning.earlierHazard(
                        "01JCHECK000000000000000001", 1, earlier.hazardId()), PreviewWarning.partialAccess(SPACE_1)),
                ReadinessCalculator.of(groups, LocationDifference.NONE, chain).warnings());
    }

    // ==================== photos available (survey-media spec 4.6, A-1; SM-10) ====================

    @Test
    void photosAvailable_wholeKitchenNa_exactMissing() {
        Map<ChecklistGroup, GroupDocument> groups = complete();
        ConditionSpace s = conditionSpace(SPACE_1);
        groups.put(ChecklistGroup.CONDITION, document(new ConditionContent(null, null, List.of(new ConditionSpace(
                SPACE_1, s.occupancy(), s.expectedVacantFrom(), s.availableFrom(), s.mainSpace(),
                Field.notApplicable("Không có bếp"), s.bathroom())))));
        SurveyMedia exterior = ready(photo(EXTERIOR_ACCESS, MediaPlacement.asset()));

        Readiness readiness = withPhotos(groups, exterior);

        assertEquals(List.of(evidence(SPACE_1, MAIN_SPACE, EVIDENCE_MISSING),
                evidence(SPACE_1, BATHROOM, EVIDENCE_MISSING)), readiness.missing());
        assertEquals(2, readiness.missingCount());
        assertEquals("SUBMISSION_NOT_AVAILABLE", readiness.submissionUnavailableReason());
    }

    @Test
    void partsTwoSpacesSharedWetAreas_exactMissingAndCoverage() {
        Map<ChecklistGroup, GroupDocument> groups = completeParts();
        List<SurveyMedia> photos = List.of(ready(photo(EXTERIOR_ACCESS, MediaPlacement.asset())),
                processing(photo(MAIN_SPACE, MediaPlacement.space(SPACE_2, "P102"))),
                ready(photo(KITCHEN, MediaPlacement.space(SPACE_1, "P101"))));

        Readiness readiness = ReadinessCalculator.of(groups, LocationDifference.NONE, List.of(), photos);

        assertEquals(List.of(evidence(null, SHARED_KITCHEN, EVIDENCE_MISSING),
                evidence(SPACE_1, MAIN_SPACE, EVIDENCE_MISSING), evidence(SPACE_1, BATHROOM, EVIDENCE_MISSING),
                evidence(SPACE_2, MAIN_SPACE, EVIDENCE_PROCESSING), evidence(SPACE_2, KITCHEN, EVIDENCE_MISSING),
                evidence(SPACE_2, BATHROOM, EVIDENCE_MISSING)), readiness.missing());
        assertEquals(EvidenceCoverageCalculator.of((IdentificationContent) groups.get(ChecklistGroup.IDENTIFICATION)
                        .content(), (ConditionContent) groups.get(ChecklistGroup.CONDITION).content(), photos)
                        .missing().stream().map(pair -> evidence(pair.spaceIdOrNull(), pair.category(),
                                pair.state() == CoverageState.PROCESSING ? EVIDENCE_PROCESSING : EVIDENCE_MISSING))
                        .toList(), readiness.missing(), "the same pairs as the list's coverage[]");
    }

    @Test
    void unknownG4_keepsRequired() {
        Map<ChecklistGroup, GroupDocument> groups = completeParts();
        groups.put(ChecklistGroup.CONDITION, document(condition(SPACE_1, SPACE_2)));

        List<MissingItem> missing = withPhotos(groups).missing();

        assertEquals(List.of(item(ChecklistGroup.CONDITION, null, "sharedKitchen", NOT_CHECKED),
                item(ChecklistGroup.CONDITION, null, "sharedBathroom", NOT_CHECKED),
                evidence(null, EXTERIOR_ACCESS, EVIDENCE_MISSING), evidence(null, SHARED_KITCHEN, EVIDENCE_MISSING),
                evidence(null, SHARED_BATHROOM, EVIDENCE_MISSING), evidence(SPACE_1, MAIN_SPACE, EVIDENCE_MISSING),
                evidence(SPACE_1, KITCHEN, EVIDENCE_MISSING), evidence(SPACE_1, BATHROOM, EVIDENCE_MISSING),
                evidence(SPACE_2, MAIN_SPACE, EVIDENCE_MISSING), evidence(SPACE_2, KITCHEN, EVIDENCE_MISSING),
                evidence(SPACE_2, BATHROOM, EVIDENCE_MISSING)), missing);
    }

    @Test
    void removedOnlyPhoto_missing() {
        SurveyMedia removed = ready(photo(EXTERIOR_ACCESS, MediaPlacement.asset()));
        removed.remove(RemovalKind.HOLDER, SALE, null, null, LATER);

        assertEquals(List.of(evidence(null, EXTERIOR_ACCESS, EVIDENCE_MISSING)),
                withPhotos(complete(), withSpaceOne(removed)).missing());
    }

    @Test
    void processingOnly_processing() {
        SurveyMedia exterior = processing(photo(EXTERIOR_ACCESS, MediaPlacement.asset()));

        assertEquals(List.of(evidence(null, EXTERIOR_ACCESS, EVIDENCE_PROCESSING)),
                withPhotos(complete(), withSpaceOne(exterior)).missing());
    }

    @Test
    void heldOnly_missing() {
        SurveyMedia held = ready(photo(EXTERIOR_ACCESS, MediaPlacement.asset()));
        held.hold(ADMIN, HoldReason.SECURITY_INCIDENT, LATER);

        assertEquals(List.of(evidence(null, EXTERIOR_ACCESS, EVIDENCE_MISSING),
                evidence(null, EXTERIOR_ACCESS, EVIDENCE_UNAVAILABLE)),
                withPhotos(complete(), withSpaceOne(held)).missing());
    }

    @Test
    void heldProcessingOnly_neverProcessing() {
        SurveyMedia held = processing(photo(EXTERIOR_ACCESS, MediaPlacement.asset()));
        held.hold(ADMIN, HoldReason.CREDENTIAL_EXPOSURE, LATER);

        assertEquals(List.of(evidence(null, EXTERIOR_ACCESS, EVIDENCE_MISSING),
                evidence(null, EXTERIOR_ACCESS, EVIDENCE_UNAVAILABLE)),
                withPhotos(complete(), withSpaceOne(held)).missing());
    }

    @Test
    void partsToNull_dropsSharedPairs() {
        Map<ChecklistGroup, GroupDocument> groups = completeParts();
        SurveyMedia sharedKitchen = ready(photo(SHARED_KITCHEN, MediaPlacement.asset()));
        assertTrue(withPhotos(groups, sharedKitchen).missing().stream()
                .noneMatch(item -> SHARED_KITCHEN.name().equals(item.fieldOrNull())), "covered while PARTS");

        groups.put(ChecklistGroup.IDENTIFICATION, document(identification(null, List.of(
                observedSpace(SPACE_1, "P101"), observedSpace(SPACE_2, "P102")))));

        List<MissingItem> evidence = withPhotos(groups, sharedKitchen).missing().stream()
                .filter(item -> item.group() == ChecklistGroup.EVIDENCE).toList();
        assertEquals(List.of(evidence(null, EXTERIOR_ACCESS, EVIDENCE_MISSING)), evidence);
    }

    @Test
    void notEnteredKitchenWithPhoto_keepsRequiredAreaNotEntered() {
        Map<ChecklistGroup, GroupDocument> groups = complete();
        ConditionSpace s = conditionSpace(SPACE_1);
        groups.put(ChecklistGroup.CONDITION, document(new ConditionContent(null, null, List.of(new ConditionSpace(
                SPACE_1, s.occupancy(), s.expectedVacantFrom(), s.availableFrom(), s.mainSpace(), Field.value(
                new AreaCheck(AreaCheckResult.NOT_ENTERED, null, null, "Chỉ nhìn từ cửa")), s.bathroom())))));

        assertEquals(List.of(item(ChecklistGroup.CONDITION, SPACE_1, "kitchen", REQUIRED_AREA_NOT_ENTERED)),
                withPhotos(groups, withSpaceOne(ready(photo(EXTERIOR_ACCESS, MediaPlacement.asset())))).missing());
    }

    @Test
    void photosNotAvailable_keepsEvidencePending() {
        Readiness readiness = ReadinessCalculator.of(complete(), LocationDifference.NONE, List.of(), null);

        assertEquals(List.of(item(ChecklistGroup.EVIDENCE, null, null, EVIDENCE_PENDING)), readiness.missing());
        assertEquals("EVIDENCE_NOT_AVAILABLE", readiness.submissionUnavailableReason());
    }

    @Test
    void submissionAvailable_falseInEveryCase() {
        Readiness none = ReadinessCalculator.of(complete(), LocationDifference.NONE, List.of(), null);
        Readiness noPhotos = withPhotos(complete());
        Readiness covered = withPhotos(complete(), withSpaceOne(ready(photo(EXTERIOR_ACCESS,
                MediaPlacement.asset()))));

        assertEquals(List.of(), covered.missing(), "every required pair covered");
        for (Readiness readiness : List.of(none, noPhotos, covered)) {
            assertFalse(readiness.submissionAvailable());
        }
        assertEquals("SUBMISSION_NOT_AVAILABLE", covered.submissionUnavailableReason());
        assertEquals("SUBMISSION_NOT_AVAILABLE", noPhotos.submissionUnavailableReason());
    }

    // ==================== the gates (checklist submission spec 4.4, S-2) ====================

    @Test
    void readiness_allGatesPass_submissionAvailable() {
        Readiness readiness = open(complete(), false, withSpaceOne(ready(photo(EXTERIOR_ACCESS,
                MediaPlacement.asset()))));

        assertTrue(readiness.submissionAvailable());
        assertEquals(null, readiness.submissionUnavailableReason());
    }

    @Test
    void readiness_scheduledAppointment_namesTheFirstFailingGate() {
        Readiness readiness = open(complete(), true, withSpaceOne(ready(photo(EXTERIOR_ACCESS,
                MediaPlacement.asset()))));

        assertFalse(readiness.submissionAvailable());
        assertEquals("APPOINTMENT_SCHEDULED", readiness.submissionUnavailableReason());
    }

    @Test
    void readiness_missingItems_namesTheFirstFailingGate() {
        assertEquals("MISSING_ITEMS", open(complete(), false).submissionUnavailableReason());
    }

    /** A held extra photo: every pair is covered, so the only item is the photo's and the gate is S7. */
    @Test
    void readiness_heldPhoto_oneItemAndPhotoUnavailable() {
        SurveyMedia held = ready(photo(DETAIL, MediaPlacement.asset()));
        held.hold(ADMIN, HoldReason.SECURITY_INCIDENT, LATER);
        SurveyMedia[] covering = withSpaceOne(ready(photo(EXTERIOR_ACCESS, MediaPlacement.asset())));
        SurveyMedia[] photos = java.util.Arrays.copyOf(covering, covering.length + 1);
        photos[covering.length] = held;

        Readiness readiness = open(complete(), false, photos);

        assertEquals(List.of(evidence(null, DETAIL, EVIDENCE_UNAVAILABLE)), readiness.missing());
        assertEquals("PHOTO_UNAVAILABLE", readiness.submissionUnavailableReason());
    }

    @Test
    void readiness_lostContent_itemEvidenceUnavailable() {
        SurveyMedia lost = ready(photo(DETAIL, MediaPlacement.asset()));
        lost.markContentMissing(LATER);

        assertTrue(open(complete(), false, lost).missing().contains(evidence(null, DETAIL, EVIDENCE_UNAVAILABLE)));
    }

    /** Nothing changes for a removed photo: it is no longer selected. */
    @Test
    void readiness_removedHeldPhoto_noUnavailableItem() {
        SurveyMedia photo = ready(photo(DETAIL, MediaPlacement.asset()));
        photo.hold(ADMIN, HoldReason.SECURITY_INCIDENT, LATER);
        photo.remove(vn.sunvibe.survey.core.domain.model.media.RemovalKind.HOLDER, SALE, null, null, LATER);

        assertFalse(open(complete(), false, photo).missing().contains(evidence(null, DETAIL, EVIDENCE_UNAVAILABLE)));
    }

    /** Both switches on, the given appointment state and these photos. */
    private static Readiness open(Map<ChecklistGroup, GroupDocument> groups, boolean scheduled,
                                  SurveyMedia... photos) {
        return ReadinessCalculator.of(groups, LocationDifference.NONE, List.of(), List.of(photos),
                new SubmissionContext(true, true, scheduled));
    }

    // ==================== helpers ====================

    private static MissingItem item(ChecklistGroup group, String spaceId, String field, MissingProblem problem) {
        return new MissingItem(group, spaceId, field, problem);
    }

    private static Hazard hazard(String id, HazardSeverity severity, HazardStatus status) {
        boolean reported = status == HazardStatus.OWNER_REPORTED_FIXED;
        return new Hazard(id, null, null, "Dây điện hở", severity, status, status == HazardStatus.OPEN ? null : "Ghi chú",
                reported ? LocalDate.parse("2026-10-21") : null, reported ? "01JSA1E0000000000000000001" : null, null);
    }

    private static Map<ChecklistGroup, GroupDocument> initialGroups() {
        Map<ChecklistGroup, GroupDocument> groups = new LinkedHashMap<>();
        for (ChecklistGroup group : ChecklistGroup.stored()) {
            groups.put(group, GroupDocument.initial(group));
        }
        return groups;
    }

    private static Map<ChecklistGroup, GroupDocument> complete() {
        Map<ChecklistGroup, GroupDocument> groups = new LinkedHashMap<>();
        for (GroupContent content : List.of(WHOLE, parts(SPACE_1), dimensions(SPACE_1), condition(SPACE_1), safety(),
                amenities(SPACE_1), terms(SPACE_1))) {
            groups.put(content.group(), document(content));
        }
        return groups;
    }

    private static Map<ChecklistGroup, GroupDocument> completeParts() {
        Map<ChecklistGroup, GroupDocument> groups = new LinkedHashMap<>();
        TermsContent terms = new TermsContent(termsCommon(), List.of(termsSpace(SPACE_1, ChargeOverrides.none(), null),
                termsSpace(SPACE_2, ChargeOverrides.none(), null)));
        for (GroupContent content : List.of(identificationParts(observedSpace(SPACE_1, "P101"),
                        observedSpace(SPACE_2, "P102")), parts(SPACE_1, SPACE_2), dimensions(SPACE_1, SPACE_2),
                conditionParts(SPACE_1, SPACE_2), safety(), amenities(SPACE_1, SPACE_2), terms)) {
            groups.put(content.group(), document(content));
        }
        return groups;
    }

    private static Readiness withPhotos(Map<ChecklistGroup, GroupDocument> groups, SurveyMedia... photos) {
        return ReadinessCalculator.of(groups, LocationDifference.NONE, List.of(), List.of(photos));
    }

    /** The given asset photo and a usable main space, kitchen and bathroom photo of SPACE_1 (WHOLE, code A1). */
    private static SurveyMedia[] withSpaceOne(SurveyMedia exterior) {
        return new SurveyMedia[]{exterior, ready(photo(MAIN_SPACE, MediaPlacement.space(SPACE_1, "A1"))),
                ready(photo(KITCHEN, MediaPlacement.space(SPACE_1, "A1"))),
                ready(photo(BATHROOM, MediaPlacement.space(SPACE_1, "A1")))};
    }

    private static SurveyMedia photo(EvidenceCategory category, MediaPlacement placement) {
        return awaiting(category, placement, SALE, sha(++hash));
    }

    private static MissingItem evidence(String spaceId, EvidenceCategory category, MissingProblem problem) {
        return item(ChecklistGroup.EVIDENCE, spaceId, category.name(), problem);
    }

    private static GroupDocument document(GroupContent content) {
        return new GroupDocument(content.group(), content, Map.of(), Map.of());
    }
}
