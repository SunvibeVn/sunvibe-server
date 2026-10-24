package vn.sunvibe.survey.core.domain.policy.checklist;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesContent;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeOverrides;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeUnit;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionSpace;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsContent;
import vn.sunvibe.survey.core.domain.model.checklist.Field;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.Hazard;
import vn.sunvibe.survey.core.domain.model.checklist.HazardSeverity;
import vn.sunvibe.survey.core.domain.model.checklist.HazardStatus;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.OtherItem;
import vn.sunvibe.survey.core.domain.model.checklist.PartsContent;
import vn.sunvibe.survey.core.domain.model.checklist.RentMoney;
import vn.sunvibe.survey.core.domain.model.checklist.SafetyContent;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.TermsContent;
import vn.sunvibe.survey.core.domain.model.checklist.TermsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.YesNo;
import vn.sunvibe.survey.core.domain.model.checklist.unit.ChangeKind;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupDocument;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitChange;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitDiff;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.parts;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.safety;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.terms;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.termsSpace;

/** What a G1 save changes in the other groups (survey-checklist spec A-12; W16-9, W16-37). */
class GroupCascadeTest {

    private static final String H1 = "01JHAZARD00000000000000001";
    private static final String H2 = "01JHAZARD00000000000000002";
    private static final String ITEM_1 = "01JTEM000000000000000000A1";
    private static final String ITEM_2 = "01JTEM000000000000000000A2";
    private static final IdentificationContent TWO = identificationParts(observedSpace(SPACE_1, "P101"),
            observedSpace(SPACE_2, "P102"));
    private static final IdentificationContent ONE = identificationParts(observedSpace(SPACE_1, "P101"));

    /** W16-9: removing P102 deletes its entries in G2, G3, G4, G6 (its other items too) and G8; hazards detach. */
    @Test
    void removal_deletesEntriesInFiveGroupsAndDetachesHazards() {
        CascadeResult result = GroupCascade.of(TWO, ONE, partsDocuments());

        assertEquals(List.of(ChecklistGroup.PARTS, ChecklistGroup.DIMENSIONS, ChecklistGroup.CONDITION,
                ChecklistGroup.SAFETY, ChecklistGroup.AMENITIES, ChecklistGroup.TERMS),
                List.copyOf(result.changed().keySet()));
        assertEquals(List.of(SPACE_1), ((PartsContent) result.changed().get(ChecklistGroup.PARTS)).spaces().stream()
                .map(space -> space.spaceId()).toList());
        assertEquals(1, ((DimensionsContent) result.changed().get(ChecklistGroup.DIMENSIONS)).spaces().size());
        assertEquals(1, ((ConditionContent) result.changed().get(ChecklistGroup.CONDITION)).spaces().size());
        assertEquals(1, ((TermsContent) result.changed().get(ChecklistGroup.TERMS)).spaces().size());
        AmenitiesContent amenities = (AmenitiesContent) result.changed().get(ChecklistGroup.AMENITIES);
        assertEquals(1, amenities.spaces().size());
        assertEquals(List.of(ITEM_1), amenities.otherItems().stream().map(OtherItem::itemId).toList());
        List<Hazard> hazards = ((SafetyContent) result.changed().get(ChecklistGroup.SAFETY)).hazards().value();
        assertEquals(SPACE_1, hazards.get(0).spaceId());
        assertNull(hazards.get(1).spaceId());
        assertEquals("Ổ cắm cháy", hazards.get(1).description());
    }

    @Test
    void cascade_removal_listsOtherGroupsRemovedUnits() {
        GroupDocument stored = partsDocuments().get(ChecklistGroup.DIMENSIONS);
        GroupContent next = GroupCascade.of(TWO, ONE, partsDocuments()).changed().get(ChecklistGroup.DIMENSIONS);

        List<UnitChange> changes = UnitDiff.between(stored, TWO, next, ONE);

        assertTrue(changes.stream().allMatch(change -> change.kind() == ChangeKind.REMOVED
                && SPACE_2.equals(change.key().spaceId())), changes.toString());
        assertEquals(4, changes.size());
    }

    /** spec A-12: a group without entries of the removed space is not changed. */
    @Test
    void removal_groupWithoutEntriesOfTheSpace_isNotChanged() {
        Map<ChecklistGroup, GroupDocument> others = new LinkedHashMap<>(partsDocuments());
        others.put(ChecklistGroup.DIMENSIONS, document(dimensions(SPACE_1)));
        others.put(ChecklistGroup.PARTS, GroupDocument.initial(ChecklistGroup.PARTS));

        CascadeResult result = GroupCascade.of(TWO, ONE, others);

        assertTrue(!result.changed().containsKey(ChecklistGroup.DIMENSIONS));
        assertTrue(!result.changed().containsKey(ChecklistGroup.PARTS));
    }

    /** Review Focus 4: a space renamed keeps every entry, because entries are keyed by space id. */
    @Test
    void cascade_spaceRenamed_changesNoOtherGroup() {
        IdentificationContent renamed = identificationParts(observedSpace(SPACE_1, "A1"), observedSpace(SPACE_2,
                "A2"));

        assertEquals(Map.of(), GroupCascade.of(TWO, renamed, partsDocuments()).changed());
    }

    /** W16-37: PARTS → WHOLE clears the shared wet areas and the per-space overrides and fees. */
    @Test
    void partsToWhole_clearsSharedWetAreasOverridesAndSpaceFees() {
        Map<ChecklistGroup, GroupDocument> others = new LinkedHashMap<>();
        others.put(ChecklistGroup.CONDITION, document(conditionParts(SPACE_1)));
        others.put(ChecklistGroup.TERMS, document(new TermsContent(terms().common(), List.of(termsSpace(SPACE_1,
                new ChargeOverrides(charge(4_000, ChargeUnit.PER_KWH), null, null, null, null), null)))));
        IdentificationContent whole = identificationWhole();

        CascadeResult result = GroupCascade.of(ONE, whole, others);

        ConditionContent condition = (ConditionContent) result.changed().get(ChecklistGroup.CONDITION);
        assertNull(condition.sharedKitchen());
        assertNull(condition.sharedBathroom());
        assertEquals(conditionParts(SPACE_1).spaces(), condition.spaces());
        TermsSpace space = ((TermsContent) result.changed().get(ChecklistGroup.TERMS)).spaces().get(0);
        assertNull(space.chargeOverrides());
        assertNull(space.spaceFees());
        assertEquals(termsSpace(SPACE_1, null, null).monthlyRent(), space.monthlyRent());
    }

    /** W16-37, spec A-12: WHOLE → PARTS clears only the per-space owner's-form rent and available-from date. */
    @Test
    void wholeToParts_clearsOnlyOwnerFormRentAndAvailableFrom() {
        IdentificationContent whole = identificationWhole();
        ConditionSpace formDate = new ConditionSpace(SPACE_1, Field.value(
                vn.sunvibe.survey.core.domain.model.checklist.Occupancy.VACANT), SourcedField.unknown(),
                SourcedField.of(LocalDate.parse("2026-11-01"), ChecklistSource.OWNER_FORM),
                conditionSpace(SPACE_1).mainSpace(), conditionSpace(SPACE_1).kitchen(),
                conditionSpace(SPACE_1).bathroom());
        TermsSpace formRent = new TermsSpace(SPACE_1, SourcedField.of(RentMoney.monthly(15_000_000),
                ChecklistSource.OWNER_FORM), termsSpace(SPACE_1, null, null).deposit(),
                termsSpace(SPACE_1, null, null).paymentPeriodMonths(),
                termsSpace(SPACE_1, null, null).minimumTermMonths(), null, null);
        Map<ChecklistGroup, GroupDocument> others = new LinkedHashMap<>();
        others.put(ChecklistGroup.DIMENSIONS, document(dimensions(SPACE_1)));
        others.put(ChecklistGroup.CONDITION, document(new ConditionContent(null, null, List.of(formDate))));
        others.put(ChecklistGroup.TERMS, document(new TermsContent(terms().common(), List.of(formRent))));

        CascadeResult result = GroupCascade.of(whole, ONE, others);

        assertEquals(List.of(ChecklistGroup.CONDITION, ChecklistGroup.TERMS), List.copyOf(result.changed().keySet()));
        ConditionContent condition = (ConditionContent) result.changed().get(ChecklistGroup.CONDITION);
        assertTrue(condition.spaces().get(0).availableFrom().isUnknown());
        assertNull(condition.sharedKitchen());
        assertEquals(formDate.mainSpace(), condition.spaces().get(0).mainSpace());
        TermsSpace rent = ((TermsContent) result.changed().get(ChecklistGroup.TERMS)).spaces().get(0);
        assertTrue(rent.monthlyRent().isUnknown());
        assertEquals(formRent.deposit(), rent.deposit());
    }

    @Test
    void wholeToParts_withoutOwnerFormValues_changesNothing() {
        Map<ChecklistGroup, GroupDocument> others = new LinkedHashMap<>();
        others.put(ChecklistGroup.CONDITION, document(condition(SPACE_1)));
        others.put(ChecklistGroup.TERMS, document(terms(SPACE_1)));

        assertEquals(Map.of(), GroupCascade.of(identificationWhole(), ONE, others).changed());
    }

    // ==================== helpers ====================

    private static Map<ChecklistGroup, GroupDocument> partsDocuments() {
        Map<ChecklistGroup, GroupDocument> others = new LinkedHashMap<>();
        others.put(ChecklistGroup.PARTS, document(parts(SPACE_1, SPACE_2)));
        others.put(ChecklistGroup.DIMENSIONS, document(dimensions(SPACE_1, SPACE_2)));
        others.put(ChecklistGroup.CONDITION, document(conditionParts(SPACE_1, SPACE_2)));
        others.put(ChecklistGroup.SAFETY, document(safety(
                new Hazard(H1, SPACE_1, null, "Dây điện hở", HazardSeverity.IMPORTANT, HazardStatus.OPEN, null, null,
                        null, null),
                new Hazard(H2, SPACE_2, null, "Ổ cắm cháy", HazardSeverity.MINOR, HazardStatus.OPEN, null, null, null,
                        null))));
        AmenitiesContent amenities = amenities(SPACE_1, SPACE_2);
        others.put(ChecklistGroup.AMENITIES, document(new AmenitiesContent(amenities.sharedItems(),
                amenities.spaces(), List.of(
                new OtherItem(ITEM_1, "Máy lọc nước", null, YesNo.YES, ChecklistSource.OBSERVED_ON_SITE, null, null,
                        null),
                new OtherItem(ITEM_2, "Bàn ủi", SPACE_2, YesNo.YES, ChecklistSource.OBSERVED_ON_SITE, null, null,
                        null)))));
        others.put(ChecklistGroup.TERMS, document(new TermsContent(terms().common(), List.of(
                termsSpace(SPACE_1, ChargeOverrides.none(), null), termsSpace(SPACE_2, ChargeOverrides.none(), null)))));
        return others;
    }

    private static GroupDocument document(GroupContent content) {
        return new GroupDocument(content.group(), content, Map.of(), Map.of());
    }
}
