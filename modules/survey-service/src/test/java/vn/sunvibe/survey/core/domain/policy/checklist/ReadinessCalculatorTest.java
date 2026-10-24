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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.core.domain.policy.checklist.MissingProblem.DIFFERENCE_NOTE_MISSING;
import static vn.sunvibe.survey.core.domain.policy.checklist.MissingProblem.EVIDENCE_PENDING;
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

/** Missing items, unconfirmed terms and warnings, computed on every read (survey-checklist spec 4.7; W16-18). */
class ReadinessCalculatorTest {

    private static final String H1 = "01JHAZARD00000000000000001";
    private static final IdentificationContent WHOLE = identificationWhole();

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

        assertEquals(List.of(ChecklistWarning.openHazard(H1), ChecklistWarning.earlierHazard(
                        "01JCHECK000000000000000001", 1, earlier.hazardId()), ChecklistWarning.partialAccess(SPACE_1)),
                ReadinessCalculator.of(groups, LocationDifference.NONE, chain).warnings());
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

    private static GroupDocument document(GroupContent content) {
        return new GroupDocument(content.group(), content, Map.of(), Map.of());
    }
}
