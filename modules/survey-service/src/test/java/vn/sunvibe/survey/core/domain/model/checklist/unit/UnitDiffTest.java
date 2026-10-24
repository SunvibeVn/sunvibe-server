package vn.sunvibe.survey.core.domain.model.checklist.unit;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesContent;
import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesSpace;
import vn.sunvibe.survey.core.domain.model.checklist.AmenityCode;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeUnit;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsContent;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Fee;
import vn.sunvibe.survey.core.domain.model.checklist.Field;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.HazardSeverity;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.Money;
import vn.sunvibe.survey.core.domain.model.checklist.OtherItem;
import vn.sunvibe.survey.core.domain.model.checklist.SafetyContent;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.TemplateV1;
import vn.sunvibe.survey.core.domain.model.checklist.TermsCommon;
import vn.sunvibe.survey.core.domain.model.checklist.TermsContent;
import vn.sunvibe.survey.core.domain.model.checklist.YesNo;
import vn.sunvibe.survey.core.domain.policy.checklist.TemplateV1Rules;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_1;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_2;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.TODAY;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.conditionParts;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.dimensions;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.dimensionsSpace;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identificationParts;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identificationWhole;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.inspected;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.item;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.observedSpace;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.openHazard;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.safety;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.terms;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.termsCommon;

/** Which provenance units a save changes, and how (survey-checklist spec 4.5, A-19, PD-5). */
class UnitDiffTest {

    private static final String A = "01JSA1E0000000000000000001";
    private static final String H1 = "01JHAZARD00000000000000001";
    private static final String H2 = "01JHAZARD00000000000000002";
    private static final UnitMeta META_A = new UnitMeta(A, Instant.parse("2026-10-20T03:00:00Z"), A,
            LocalDate.parse("2026-10-20"), null, ChecklistSource.MEASURED, null);
    private static final IdentificationContent WHOLE = identificationWhole();
    private static final UnitKey AREA = UnitKey.spaceField(ChecklistGroup.DIMENSIONS, SPACE_1, "area");
    private static final String ITEM_X = "01JTEM000000000000000000X1";
    private static final String ITEM_Y = "01JTEM000000000000000000Y1";

    /** Review Focus 1: the same values typed in another form are no change at all. */
    @Test
    void diff_normalisedEqualValues_unchanged() {
        DimensionsContent stored = (DimensionsContent) TemplateV1Rules.checkAndNormalise(
                new DimensionsContent(List.of(withLayout(dimensionsSpace(SPACE_1), "Phòng ngủ hướng đông"))), TODAY);
        DimensionsSpace typed = new DimensionsSpace(SPACE_1,
                SourcedField.of(new BigDecimal("45.50"), ChecklistSource.MEASURED), stored.spaces().get(0).bedrooms(),
                stored.spaces().get(0).privateBathrooms(), stored.spaces().get(0).sharedBathroomUse(),
                Normalizer.normalize("Phòng ngủ hướng đông ", Normalizer.Form.NFD));
        GroupContent next = TemplateV1Rules.checkAndNormalise(new DimensionsContent(List.of(typed)), TODAY);

        assertEquals(List.of(), UnitDiff.between(document(stored, Map.of(AREA, META_A)), next, WHOLE));
    }

    @Test
    void diff_partsSharedKitchenUnknownVsStoredNull_unchanged() {
        IdentificationContent parts = identificationParts(observedSpace(SPACE_1, "P101"));
        ConditionContent stored = new ConditionContent(null, null, conditionParts(SPACE_1).spaces());
        ConditionContent next = new ConditionContent(Field.unknown(), Field.unknown(), stored.spaces());

        assertEquals(List.of(), UnitDiff.between(document(stored, Map.of()), next, parts));
    }

    @Test
    void diff_firstFill_isFirstFill() {
        List<UnitChange> changes = UnitDiff.between(GroupDocument.initial(ChecklistGroup.DIMENSIONS),
                dimensions(SPACE_1), WHOLE);

        assertEquals(List.of(AREA, key("bedrooms"), key("privateBathrooms"), key("sharedBathroomUse")),
                changes.stream().map(UnitChange::key).toList());
        changes.forEach(change -> assertEquals(ChangeKind.FIRST_FILL, change.kind()));
        assertNull(changes.get(0).storedMeta());
        assertEquals("content.spaces.0.area", changes.get(0).path());
    }

    @Test
    void diff_clearedUnitRefilled_isReplacedWithTheClearedMeta() {
        DimensionsContent cleared = new DimensionsContent(List.of(new DimensionsSpace(SPACE_1, SourcedField.unknown(),
                dimensionsSpace(SPACE_1).bedrooms(), dimensionsSpace(SPACE_1).privateBathrooms(),
                dimensionsSpace(SPACE_1).sharedBathroomUse(), null)));

        List<UnitChange> changes = UnitDiff.between(document(cleared, Map.of(AREA, META_A)), dimensions(SPACE_1),
                WHOLE);

        assertEquals(1, changes.size());
        assertEquals(ChangeKind.REPLACED, changes.get(0).kind());
        assertEquals(META_A, changes.get(0).storedMeta());
    }

    @Test
    void diff_valueToUnknown_isCleared() {
        DimensionsContent next = new DimensionsContent(List.of(new DimensionsSpace(SPACE_1, SourcedField.unknown(),
                dimensionsSpace(SPACE_1).bedrooms(), dimensionsSpace(SPACE_1).privateBathrooms(),
                dimensionsSpace(SPACE_1).sharedBathroomUse(), null)));

        List<UnitChange> changes = UnitDiff.between(document(dimensions(SPACE_1), Map.of(AREA, META_A)), next, WHOLE);

        assertEquals(List.of(ChangeKind.CLEARED), changes.stream().map(UnitChange::kind).toList());
        assertEquals(new BigDecimal("45.5"), changes.get(0).before().businessValue());
    }

    @Test
    void diff_hazardAddedToANonEmptyList_isAnAddedEntry() {
        List<UnitChange> changes = UnitDiff.between(document(safety(openHazard(H1, HazardSeverity.MINOR)), Map.of()),
                safety(openHazard(H1, HazardSeverity.MINOR), openHazard(H2, HazardSeverity.IMPORTANT)), WHOLE);

        assertEquals(List.of(UnitKey.hazard(H2)), changes.stream().map(UnitChange::key).toList());
        assertEquals(ChangeKind.ADDED_ENTRY, changes.get(0).kind());
        assertEquals("content.hazards.value.1", changes.get(0).path());
    }

    @Test
    void diff_hazardAddedToANoneSeenList_replacesTheListWrapper() {
        SafetyContent noneSeen = safety();
        UnitMeta listMeta = META_A;

        List<UnitChange> changes = UnitDiff.between(document(noneSeen, Map.of(UnitKey.hazardList(), listMeta)),
                safety(openHazard(H1, HazardSeverity.MINOR)), WHOLE);

        assertEquals(List.of(UnitKey.hazardList(), UnitKey.hazard(H1)), changes.stream().map(UnitChange::key).toList());
        assertEquals(ChangeKind.REPLACED, changes.get(0).kind());
        assertEquals(listMeta, changes.get(0).storedMeta());
        assertEquals(ChangeKind.ADDED_ENTRY, changes.get(1).kind());
    }

    @Test
    void diff_hazardRemoved_isRemoved() {
        List<UnitChange> changes = UnitDiff.between(document(safety(openHazard(H1, HazardSeverity.MINOR),
                        openHazard(H2, HazardSeverity.MINOR)), Map.of(UnitKey.hazard(H2), META_A)),
                safety(openHazard(H1, HazardSeverity.MINOR)), WHOLE);

        assertEquals(List.of(UnitKey.hazard(H2)), changes.stream().map(UnitChange::key).toList());
        assertEquals(ChangeKind.REMOVED, changes.get(0).kind());
        assertEquals(META_A, changes.get(0).storedMeta());
        assertNull(changes.get(0).after());
    }

    /** spec A-19: a fee list is one unit; changing one fee replaces it. */
    @Test
    void diff_feeChangedInOtherFees_replacesTheList() {
        TermsCommon common = termsCommon();
        TermsCommon changed = new TermsCommon(common.electricity(), common.water(), common.internet(),
                common.parking(), common.serviceFee(), SourcedField.of(List.of(new Fee("Phí vệ sinh",
                Money.vnd(60_000), ChargeUnit.PER_MONTH)), ChecklistSource.OBSERVED_ON_SITE), common.houseRules(),
                common.pets(), common.handoverMaintenance());

        List<UnitChange> changes = UnitDiff.between(document(terms(SPACE_1),
                Map.of(UnitKey.commonTerm("otherFees"), META_A)), new TermsContent(changed, terms(SPACE_1).spaces()),
                WHOLE);

        assertEquals(List.of(UnitKey.commonTerm("otherFees")), changes.stream().map(UnitChange::key).toList());
        assertEquals(ChangeKind.REPLACED, changes.get(0).kind());
        assertEquals(META_A, changes.get(0).storedMeta());
    }

    /** spec A-19: a shared wet area coming back after PARTS → WHOLE → PARTS is a replacement of its tombstone. */
    @Test
    void diff_keyBackFromTombstone_isReplacedWithTheTombstone() {
        IdentificationContent parts = identificationParts(observedSpace(SPACE_1, "P101"));
        ConditionContent stored = new ConditionContent(null, null, conditionParts(SPACE_1).spaces());
        UnitKey kitchen = UnitKey.field(ChecklistGroup.CONDITION, "sharedKitchen");
        GroupDocument document = new GroupDocument(ChecklistGroup.CONDITION, stored, Map.of(), Map.of(kitchen,
                Tombstone.of(META_A)));

        List<UnitChange> changes = UnitDiff.between(document, new ConditionContent(Field.value(inspected()),
                Field.unknown(), stored.spaces()), parts);

        assertEquals(List.of(kitchen), changes.stream().map(UnitChange::key).toList());
        assertEquals(ChangeKind.REPLACED, changes.get(0).kind());
        assertEquals(META_A, changes.get(0).storedMeta());
    }

    /**
     * spec A-29 (guard): a G6 item back under the code and placement it had, or an other item under the same itemId,
     * replaces its tombstone; it is never an added entry.
     */
    @Test
    void diff_g6ItemBackFromTombstone_replaced() {
        AmenitiesContent empty = new AmenitiesContent(List.of(), List.of(new AmenitiesSpace(SPACE_1,
                SourcedField.unknown(), List.of())), List.of());
        UnitKey shared = UnitKey.sharedItem(AmenityCode.AIR_CONDITIONER);
        UnitKey ofSpace = UnitKey.spaceItem(SPACE_1, AmenityCode.BED);
        UnitKey other = UnitKey.otherItem(ITEM_X);
        GroupDocument stored = new GroupDocument(ChecklistGroup.AMENITIES, empty, Map.of(), Map.of(shared,
                Tombstone.of(META_A), ofSpace, Tombstone.of(META_A), other, Tombstone.of(META_A)));
        AmenitiesContent back = new AmenitiesContent(List.of(item(AmenityCode.AIR_CONDITIONER, YesNo.YES)),
                List.of(new AmenitiesSpace(SPACE_1, SourcedField.unknown(), List.of(item(AmenityCode.BED,
                        YesNo.NO)))), List.of(otherItem(ITEM_X, "Kệ giày mới", null)));

        List<UnitChange> changes = UnitDiff.between(stored, back, WHOLE);

        assertEquals(List.of(shared, ofSpace, other), changes.stream().map(UnitChange::key).toList());
        for (UnitChange change : changes) {
            assertEquals(ChangeKind.REPLACED, change.kind());
            assertEquals(META_A, change.storedMeta());
            assertNull(change.before());
        }
    }

    /** spec A-29 (guard): the same hazardId back after its removal replaces its tombstone. */
    @Test
    void diff_hazardBackFromTombstone_replaced() {
        GroupDocument stored = new GroupDocument(ChecklistGroup.SAFETY, safety(openHazard(H1, HazardSeverity.MINOR)),
                Map.of(), Map.of(UnitKey.hazard(H2), Tombstone.of(META_A)));

        List<UnitChange> changes = UnitDiff.between(stored, safety(openHazard(H1, HazardSeverity.MINOR),
                openHazard(H2, HazardSeverity.IMPORTANT)), WHOLE);

        assertEquals(List.of(UnitKey.hazard(H2)), changes.stream().map(UnitChange::key).toList());
        assertEquals(ChangeKind.REPLACED, changes.get(0).kind());
        assertEquals(META_A, changes.get(0).storedMeta());
    }

    /** spec A-29 (guard): the same code at another placement is a new entry. */
    @Test
    void diff_sameCodeOtherPlacement_addedEntry() {
        AmenitiesContent empty = new AmenitiesContent(List.of(), List.of(new AmenitiesSpace(SPACE_1,
                SourcedField.unknown(), List.of())), List.of());
        GroupDocument stored = new GroupDocument(ChecklistGroup.AMENITIES, empty, Map.of(),
                Map.of(UnitKey.sharedItem(AmenityCode.AIR_CONDITIONER), Tombstone.of(META_A)));

        List<UnitChange> changes = UnitDiff.between(stored, new AmenitiesContent(List.of(), List.of(
                new AmenitiesSpace(SPACE_1, SourcedField.unknown(), List.of(item(AmenityCode.AIR_CONDITIONER,
                        YesNo.YES)))), List.of()), WHOLE);

        assertEquals(List.of(UnitKey.spaceItem(SPACE_1, AmenityCode.AIR_CONDITIONER)),
                changes.stream().map(UnitChange::key).toList());
        assertEquals(ChangeKind.ADDED_ENTRY, changes.get(0).kind());
    }

    /** spec A-29 (guard): a new itemId with a removed item's label is a new entry; labels are never matched. */
    @Test
    void diff_otherItemNewIdSameLabel_addedEntry() {
        GroupDocument stored = new GroupDocument(ChecklistGroup.AMENITIES, new AmenitiesContent(List.of(), List.of(),
                List.of()), Map.of(), Map.of(UnitKey.otherItem(ITEM_X), Tombstone.of(META_A)));

        List<UnitChange> changes = UnitDiff.between(stored, new AmenitiesContent(List.of(), List.of(),
                List.of(otherItem(ITEM_Y, "Máy lọc nước", null))), WHOLE);

        assertEquals(List.of(UnitKey.otherItem(ITEM_Y)), changes.stream().map(UnitChange::key).toList());
        assertEquals(ChangeKind.ADDED_ENTRY, changes.get(0).kind());
    }

    /** spec PD-5: a reorder of spaces is a real save whose diff lists no unit. */
    @Test
    void diff_reorderedListWithEqualUnits_listsNoUnit() {
        IdentificationContent stored = identificationParts(observedSpace(SPACE_1, "P101"), observedSpace(SPACE_2,
                "P102"));
        IdentificationContent next = identificationParts(observedSpace(SPACE_2, "P102"), observedSpace(SPACE_1,
                "P101"));

        assertEquals(List.of(), UnitDiff.between(document(stored, Map.of()), next, next));
    }

    @Test
    void diff_unknownSpaceEntry_isAnAddedFieldWithItsRequestPath() {
        DimensionsContent next = new DimensionsContent(List.of(dimensionsSpace(SPACE_2)));

        List<UnitChange> changes = UnitDiff.between(document(dimensions(SPACE_1), Map.of()), next, WHOLE);

        assertEquals("content.spaces.0.area", changes.stream().filter(c -> c.key().spaceId().equals(SPACE_2))
                .findFirst().orElseThrow().path());
        assertEquals(ChangeKind.CLEARED, changes.stream().filter(c -> c.key().equals(AREA)).findFirst().orElseThrow()
                .kind());
    }

    @Test
    void initial_isTheEmptyTemplateWithoutMeta() {
        GroupDocument initial = GroupDocument.initial(ChecklistGroup.TERMS);

        assertEquals(TemplateV1.empty(ChecklistGroup.TERMS), initial.content());
        assertEquals(Map.of(), initial.meta());
        assertEquals(Map.of(), initial.tombstones());
    }

    // ==================== helpers ====================

    private static UnitKey key(String field) {
        return UnitKey.spaceField(ChecklistGroup.DIMENSIONS, SPACE_1, field);
    }

    private static DimensionsSpace withLayout(DimensionsSpace space, String note) {
        return new DimensionsSpace(space.spaceId(), space.area(), space.bedrooms(), space.privateBathrooms(),
                space.sharedBathroomUse(), note);
    }

    private static GroupDocument document(GroupContent content, Map<UnitKey, UnitMeta> meta) {
        return new GroupDocument(content.group(), content, meta, Map.of());
    }

    private static OtherItem otherItem(String itemId, String label, String spaceId) {
        return new OtherItem(itemId, label, spaceId, YesNo.YES, ChecklistSource.OBSERVED_ON_SITE, null, null, null);
    }
}
