package vn.sunvibe.survey.core.domain.policy.checklist;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesContent;
import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesSpace;
import vn.sunvibe.survey.core.domain.model.checklist.AmenityCode;
import vn.sunvibe.survey.core.domain.model.checklist.AmenityItem;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeOverrides;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeUnit;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.Field;
import vn.sunvibe.survey.core.domain.model.checklist.HazardSeverity;
import vn.sunvibe.survey.core.domain.model.checklist.OtherItem;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.TermsContent;
import vn.sunvibe.survey.core.domain.model.checklist.YesNo;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupDocument;
import vn.sunvibe.survey.core.domain.model.checklist.unit.Tombstone;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitKey;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitMeta;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_1;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_2;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.charge;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.conditionParts;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.dimensions;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.item;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.openHazard;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.safety;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.terms;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.termsSpace;

/** The metadata and tombstones of a group's next document (survey-checklist spec A-19, A-29, PD-1). */
class GroupDocumentsTest {

    private static final String A = "01JA00000000000000000000A1";
    private static final String B = "01JB00000000000000000000B1";
    private static final String C = "01JC00000000000000000000C1";
    private static final Instant T2 = Instant.parse("2026-10-25T05:00:00Z");
    private static final LocalDate D1 = LocalDate.parse("2026-10-18");
    private static final UnitMeta META_A = new UnitMeta(A, Instant.parse("2026-10-20T05:00:00Z"), A,
            LocalDate.parse("2026-10-20"), null, null, null);
    private static final UnitMeta META_B = new UnitMeta(B, Instant.parse("2026-10-24T05:00:00Z"), A,
            LocalDate.parse("2026-10-20"), null, null, 7L);
    /** What a removal by B stamps on A's on-site unit (spec A-29). */
    private static final Tombstone STAMP_B = new Tombstone(B, T2, A, LocalDate.parse("2026-10-20"), null,
            ChecklistSource.OBSERVED_ON_SITE, 7L, null);
    private static final UnitKey KITCHEN = UnitKey.field(ChecklistGroup.CONDITION, "sharedKitchen");
    private static final String H1 = "01JHAZARD00000000000000001";
    private static final String H2 = "01JHAZARD00000000000000002";
    private static final AmenityItem AIR_CONDITIONER = item(AmenityCode.AIR_CONDITIONER, YesNo.YES);
    private static final AmenityItem ELEVATOR = item(AmenityCode.ELEVATOR, YesNo.YES);
    private static final AmenityItem BED = item(AmenityCode.BED, YesNo.YES);
    private static final AmenityItem DESK = item(AmenityCode.DESK, YesNo.YES);
    private static final OtherItem X = new OtherItem("01JTEM000000000000000000X1", "Máy lọc nước", SPACE_1,
            YesNo.YES, ChecklistSource.OBSERVED_ON_SITE, null, null, null);
    private static final OtherItem Y = new OtherItem("01JTEM000000000000000000Y1", "Kệ giày", SPACE_2, YesNo.YES,
            ChecklistSource.OBSERVED_ON_SITE, null, null, null);
    private static final UnitKey SHARED_AC = UnitKey.sharedItem(AmenityCode.AIR_CONDITIONER);
    private static final UnitKey SHARED_ELEVATOR = UnitKey.sharedItem(AmenityCode.ELEVATOR);
    private static final UnitKey P1_BED = UnitKey.spaceItem(SPACE_1, AmenityCode.BED);
    private static final UnitKey P2_DESK = UnitKey.spaceItem(SPACE_2, AmenityCode.DESK);
    private static final UnitKey OTHER_X = UnitKey.otherItem(X.itemId());
    private static final UnitKey OTHER_Y = UnitKey.otherItem(Y.itemId());

    /**
     * W16-44, BR-29: PARTS → WHOLE → PARTS brings the shared wet areas back with the tombstone the scope switch
     * stamped (the G1 saver as last editor, the observer kept) as metadata.
     */
    @Test
    void apply_partsWholePartsRoundTrip_tombstoneBecomesMetaAgain() {
        ConditionContent parts = conditionParts(SPACE_1);
        GroupDocument stored = new GroupDocument(ChecklistGroup.CONDITION, parts, Map.of(KITCHEN, META_A), Map.of());
        ConditionContent whole = new ConditionContent(null, null, parts.spaces());

        GroupDocument afterWhole = GroupDocuments.apply(stored, whole, Map.of(), Map.of(KITCHEN, STAMP_B));
        GroupDocument afterParts = GroupDocuments.apply(afterWhole, new ConditionContent(Field.unknown(),
                Field.unknown(), parts.spaces()), Map.of(), Map.of());

        assertFalse(afterWhole.meta().containsKey(KITCHEN));
        assertEquals(STAMP_B, afterWhole.tombstones().get(KITCHEN));
        assertEquals(STAMP_B.asMeta(), afterParts.meta().get(KITCHEN));
        assertFalse(afterParts.tombstones().containsKey(KITCHEN));
    }

    @Test
    void apply_overridesClearedByTheScope_becomeTombstones() {
        UnitKey override = UnitKey.override(SPACE_1, "electricity");
        TermsContent parts = new TermsContent(terms().common(), List.of(termsSpace(SPACE_1,
                new ChargeOverrides(charge(4_000, ChargeUnit.PER_KWH), null, null, null, null), null)));
        GroupDocument stored = new GroupDocument(ChecklistGroup.TERMS, parts, Map.of(override, META_A), Map.of());

        GroupDocument whole = GroupDocuments.apply(stored, terms(SPACE_1), Map.of(), Map.of(override, STAMP_B));

        assertEquals(STAMP_B, whole.tombstones().get(override));
        assertFalse(whole.meta().containsKey(override));
    }

    /** W16-46, BR-28, BR-29: a removed shared item, item of a space and other item keep their stamped tombstones. */
    @Test
    void apply_removedItems_keepStampedTombstones() {
        GroupDocument stored = new GroupDocument(ChecklistGroup.AMENITIES, g6(List.of(AIR_CONDITIONER, ELEVATOR),
                List.of(BED), List.of(DESK), List.of(X, Y)), Map.of(SHARED_AC, META_A, SHARED_ELEVATOR, META_A,
                P1_BED, META_A, OTHER_X, META_A), Map.of());
        Tombstone bed = new Tombstone(B, T2, C, D1, null, ChecklistSource.OWNER_STATED, 7L, null);

        GroupDocument next = GroupDocuments.apply(stored, g6(List.of(ELEVATOR), List.of(), List.of(DESK),
                List.of(Y)), Map.of(), Map.of(SHARED_AC, STAMP_B, P1_BED, bed, OTHER_X, STAMP_B));

        assertEquals(Map.of(SHARED_AC, STAMP_B, P1_BED, bed, OTHER_X, STAMP_B), next.tombstones());
        assertEquals(Map.of(SHARED_ELEVATOR, META_A), next.meta());
    }

    /** W16-47, BR-30: a removed hazard keeps its stamped tombstone with the report's receiver and date. */
    @Test
    void apply_removedHazard_keepsStampedTombstoneWithReport() {
        UnitKey h2 = UnitKey.hazard(H2);
        GroupDocument stored = new GroupDocument(ChecklistGroup.SAFETY, safety(openHazard(H1, HazardSeverity.MINOR),
                openHazard(H2, HazardSeverity.MINOR)), Map.of(UnitKey.hazard(H1), META_A, h2, META_A), Map.of());
        Tombstone stamp = new Tombstone(A, T2, A, D1, null, null, 7L, new Tombstone.OwnerReportFacts(C, D1));

        GroupDocument next = GroupDocuments.apply(stored, safety(openHazard(H1, HazardSeverity.MINOR)), Map.of(),
                Map.of(h2, stamp));

        assertEquals(Map.of(h2, stamp), next.tombstones());
        assertEquals(Map.of(UnitKey.hazard(H1), META_A), next.meta());
    }

    /** BR-29: a later save that leaves a removed entry absent stamps nothing; its tombstone stays as it was. */
    @Test
    void apply_unrelatedSave_leavesTombstonesUnchanged() {
        Map<UnitKey, Tombstone> tombstones = Map.of(SHARED_AC, STAMP_B, OTHER_X, STAMP_B, P2_DESK, STAMP_B);
        GroupDocument stored = new GroupDocument(ChecklistGroup.AMENITIES, g6(List.of(ELEVATOR), List.of(BED),
                List.of(), List.of()), Map.of(SHARED_ELEVATOR, META_A), tombstones);
        UnitKey furnishing = UnitKey.spaceField(ChecklistGroup.AMENITIES, SPACE_1, "furnishing");

        GroupDocument next = GroupDocuments.apply(stored, g6(List.of(ELEVATOR), List.of(BED), List.of(), List.of()),
                Map.of(furnishing, META_B), Map.of());

        assertEquals(tombstones, next.tombstones());
        assertEquals(Map.of(SHARED_ELEVATOR, META_A, furnishing, META_B), next.meta());
    }

    /**
     * spec A-29: a space's item tombstone goes with its space (a space id never comes back); an other item keeps its
     * tombstone when its space is removed, because its identity is its itemId.
     */
    @Test
    void apply_spaceRemoved_dropsItsItemTombstonesKeepsOtherItems() {
        GroupDocument stored = new GroupDocument(ChecklistGroup.AMENITIES, g6(List.of(ELEVATOR), List.of(BED),
                List.of(), List.of(Y)), Map.of(SHARED_ELEVATOR, META_A, OTHER_Y, META_A), Map.of(P2_DESK, STAMP_B));
        AmenitiesContent withoutP2 = new AmenitiesContent(List.of(ELEVATOR), List.of(new AmenitiesSpace(SPACE_1,
                SourcedField.unknown(), List.of(BED))), List.of());

        GroupDocument next = GroupDocuments.apply(stored, withoutP2, Map.of(), Map.of(OTHER_Y, STAMP_B));

        assertEquals(Map.of(OTHER_Y, STAMP_B), next.tombstones());
        assertEquals(Map.of(SHARED_ELEVATOR, META_A), next.meta());
    }

    /** spec A-29: a key coming back takes its tombstone as metadata; the save's new metadata wins. */
    @Test
    void apply_keyBack_takesItsTombstoneAsMeta() {
        GroupDocument stored = new GroupDocument(ChecklistGroup.AMENITIES, g6(List.of(), List.of(), List.of(),
                List.of()), Map.of(), Map.of(SHARED_AC, STAMP_B, OTHER_X, STAMP_B));

        GroupDocument next = GroupDocuments.apply(stored, g6(List.of(AIR_CONDITIONER), List.of(), List.of(),
                List.of(X)), Map.of(SHARED_AC, META_B), Map.of());

        assertEquals(Map.of(SHARED_AC, META_B, OTHER_X, STAMP_B.asMeta()), next.meta());
        assertEquals(Map.of(), next.tombstones());
    }

    @Test
    void apply_removedSpace_dropsItsMetaAndTombstones() {
        UnitKey area2 = UnitKey.spaceField(ChecklistGroup.DIMENSIONS, SPACE_2, "area");
        UnitKey area1 = UnitKey.spaceField(ChecklistGroup.DIMENSIONS, SPACE_1, "area");
        GroupDocument stored = new GroupDocument(ChecklistGroup.DIMENSIONS, dimensions(SPACE_1, SPACE_2),
                Map.of(area1, META_A, area2, META_A), Map.of());

        GroupDocument next = GroupDocuments.apply(stored, dimensions(SPACE_1), Map.of(), Map.of());

        assertEquals(Map.of(area1, META_A), next.meta());
        assertEquals(Map.of(), next.tombstones());
    }

    @Test
    void apply_changedMetaReplacesAndOthersAreKept() {
        UnitKey area1 = UnitKey.spaceField(ChecklistGroup.DIMENSIONS, SPACE_1, "area");
        UnitKey beds = UnitKey.spaceField(ChecklistGroup.DIMENSIONS, SPACE_1, "bedrooms");
        GroupDocument stored = new GroupDocument(ChecklistGroup.DIMENSIONS, dimensions(SPACE_1),
                Map.of(area1, META_A, beds, META_A), Map.of());

        GroupDocument next = GroupDocuments.apply(stored, dimensions(SPACE_1), Map.of(area1, META_B), Map.of());

        assertEquals(Map.of(area1, META_B, beds, META_A), next.meta());
        assertEquals(dimensions(SPACE_1), next.content());
    }

    // ==================== helpers ====================

    /** G6 of P1 and P2 (furnishing unknown): the shared items, each space's items, the other items. */
    private static AmenitiesContent g6(List<AmenityItem> shared, List<AmenityItem> p1, List<AmenityItem> p2,
                                       List<OtherItem> others) {
        return new AmenitiesContent(shared, List.of(new AmenitiesSpace(SPACE_1, SourcedField.unknown(), p1),
                new AmenitiesSpace(SPACE_2, SourcedField.unknown(), p2)), others);
    }
}
