package vn.sunvibe.survey.core.domain.policy.checklist;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesContent;
import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesSpace;
import vn.sunvibe.survey.core.domain.model.checklist.AmenityCode;
import vn.sunvibe.survey.core.domain.model.checklist.AmenityItem;
import vn.sunvibe.survey.core.domain.model.checklist.AreaCheck;
import vn.sunvibe.survey.core.domain.model.checklist.AreaCheckResult;
import vn.sunvibe.survey.core.domain.model.checklist.Charge;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeBasis;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeOverrides;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeUnit;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionSpace;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsContent;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Fee;
import vn.sunvibe.survey.core.domain.model.checklist.Field;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.Hazard;
import vn.sunvibe.survey.core.domain.model.checklist.HazardSeverity;
import vn.sunvibe.survey.core.domain.model.checklist.HazardStatus;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.Money;
import vn.sunvibe.survey.core.domain.model.checklist.OtherItem;
import vn.sunvibe.survey.core.domain.model.checklist.PartsContent;
import vn.sunvibe.survey.core.domain.model.checklist.PartsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.PropertyTypeCode;
import vn.sunvibe.survey.core.domain.model.checklist.RentMoney;
import vn.sunvibe.survey.core.domain.model.checklist.RentalScope;
import vn.sunvibe.survey.core.domain.model.checklist.SafetyContent;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.TermsCommon;
import vn.sunvibe.survey.core.domain.model.checklist.TermsContent;
import vn.sunvibe.survey.core.domain.model.checklist.TermsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.YesNo;
import vn.sunvibe.survey.core.domain.model.checklist.unit.ChangeKind;
import vn.sunvibe.survey.core.domain.model.checklist.unit.ChecklistUnits;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupDocument;
import vn.sunvibe.survey.core.domain.model.checklist.unit.Tombstone;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitChange;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitDiff;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitKey;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitMeta;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_1;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_2;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.amenities;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.charge;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.conditionParts;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.conditionSpace;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.dimensionsSpace;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identification;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identificationParts;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identificationWhole;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.inspected;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.observedSpace;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.openHazard;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.parts;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.partsSpace;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.safety;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.terms;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.termsCommon;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.termsSpace;

/**
 * The correction rule (survey-checklist BR-22, BR-27, spec 4.5, A-19): which changed units of a save need a correction
 * basis, one test per case of W16-16 and W16-44. A performed the visit and saved; B took over; C is another staff
 * member who heard the owner.
 */
class CorrectionPolicyTest {

    private static final String A = "01JA00000000000000000000A1";
    private static final String B = "01JB00000000000000000000B1";
    private static final String C = "01JC00000000000000000000C1";
    private static final LocalDate ARRIVAL = LocalDate.parse("2026-10-20");
    private static final LocalDate D1 = LocalDate.parse("2026-10-18");
    private static final LocalDate D2 = LocalDate.parse("2026-10-22");
    private static final VisitFacts VISIT = new VisitFacts(A, ARRIVAL, 1);
    private static final Instant T = Instant.parse("2026-10-20T05:00:00Z");
    private static final String H1 = "01JHAZARD00000000000000001";
    private static final String H2 = "01JHAZARD00000000000000002";
    private static final String H3 = "01JHAZARD00000000000000003";
    private static final String H4 = "01JHAZARD00000000000000004";
    private static final Instant T2 = Instant.parse("2026-10-22T05:00:00Z");
    private static final Instant T3 = Instant.parse("2026-10-23T05:00:00Z");
    private static final AmenityItem AIR_CONDITIONER = item(AmenityCode.AIR_CONDITIONER, YesNo.YES,
            ChecklistSource.OBSERVED_ON_SITE, null, null);
    private static final AmenityItem WATER_HEATER = item(AmenityCode.WATER_HEATER, YesNo.YES,
            ChecklistSource.OBSERVED_ON_SITE, null, null);
    private static final AmenityItem ELEVATOR = item(AmenityCode.ELEVATOR, YesNo.YES, ChecklistSource.OBSERVED_ON_SITE,
            null, null);
    private static final AmenityItem BED_C = item(AmenityCode.BED, YesNo.YES, ChecklistSource.OWNER_STATED, D1, C);
    private static final AmenityItem DESK = item(AmenityCode.DESK, YesNo.YES, ChecklistSource.OBSERVED_ON_SITE, null,
            null);
    private static final OtherItem ITEM_X = new OtherItem("01JTEM000000000000000000X1", "Máy lọc nước", SPACE_1,
            YesNo.YES, ChecklistSource.OBSERVED_ON_SITE, null, null, null);
    private static final OtherItem ITEM_Y = new OtherItem("01JTEM000000000000000000Y1", "Kệ giày", SPACE_2,
            YesNo.YES, ChecklistSource.OBSERVED_ON_SITE, null, null, null);
    private static final OtherItem ITEM_Z = new OtherItem("01JTEM000000000000000000Z1", "Rèm cửa", SPACE_1,
            YesNo.YES, ChecklistSource.OBSERVED_ON_SITE, null, null, null);
    private static final UnitKey SHARED_AC = UnitKey.sharedItem(AmenityCode.AIR_CONDITIONER);
    private static final UnitKey OTHER_X = UnitKey.otherItem(ITEM_X.itemId());
    private static final UnitKey OTHER_Y = UnitKey.otherItem(ITEM_Y.itemId());
    private static final Hazard H1_IMPORTANT = new Hazard(H1, null, null, "Dây điện hở ở hành lang",
            HazardSeverity.IMPORTANT, HazardStatus.OPEN, null, null, null, null);
    private static final Hazard H2_REPORTED_BY_C = new Hazard(H2, null, null, "Ổ cắm lỏng trong bếp",
            HazardSeverity.MINOR, HazardStatus.OWNER_REPORTED_FIXED, "Chủ nói đã thay ổ cắm", D1, C, "C nghe chủ báo");
    private static final Hazard H3_MINOR = new Hazard(H3, null, null, "Bậc cầu thang mòn", HazardSeverity.MINOR,
            HazardStatus.OPEN, null, null, null, null);
    private static final IdentificationContent WHOLE = identificationWhole();
    private static final IdentificationContent PARTS = identificationParts(observedSpace(SPACE_1, "P101"),
            observedSpace(SPACE_2, "P102"));

    // ==================== W16-16 ====================

    @Test
    void fillUnknownFromRecords_isFree() {
        assertEquals(List.of(), requiring(B, GroupDocument.initial(ChecklistGroup.DIMENSIONS),
                new DimensionsContent(List.of(dimensionsSpace(SPACE_1))), WHOLE));
    }

    @Test
    void onSiteValuesOfA_changedByB_needACorrectionInEveryGroup() {
        IdentificationContent g1 = WHOLE;
        IdentificationContent otherType = new IdentificationContent(g1.observedAddress(), null,
                Field.value(PropertyTypeCode.HOUSE), g1.fit(), g1.rentalScope(), g1.spaces(), null, null);
        assertEquals(List.of(UnitKey.field(ChecklistGroup.IDENTIFICATION, "propertyType")),
                requiring(B, recordedByA(g1, WHOLE), otherType, otherType));

        IdentificationContent scoped = identification(RentalScope.PARTS, g1.spaces());
        assertEquals(List.of(UnitKey.field(ChecklistGroup.IDENTIFICATION, "rentalScope")),
                requiring(B, recordedByA(g1, WHOLE), scoped, scoped));

        PartsContent floor = new PartsContent(parts().accessRoute(), parts().sharedAreas(), parts().totalFloors(),
                List.of(new PartsSpace(SPACE_1, Field.value("Tầng 3"), partsSpace(SPACE_1).privateAreas(),
                        partsSpace(SPACE_1).actualAccess())));
        assertEquals(List.of(UnitKey.spaceField(ChecklistGroup.PARTS, SPACE_1, "floor")),
                requiring(B, recordedByA(parts(SPACE_1), WHOLE), floor, WHOLE));

        assertEquals(List.of(area()), requiring(B, recordedByA(dims(dimensionsSpace(SPACE_1)), WHOLE),
                dims(withArea(SourcedField.of(new BigDecimal("50.0"), ChecklistSource.MEASURED))), WHOLE));

        ConditionContent kitchen = new ConditionContent(null, null, List.of(withKitchen(new AreaCheck(
                AreaCheckResult.NOT_ENTERED, null, null, "Khóa"))));
        assertEquals(List.of(UnitKey.spaceField(ChecklistGroup.CONDITION, SPACE_1, "kitchen")), requiring(B,
                recordedByA(new ConditionContent(null, null, List.of(conditionSpace(SPACE_1))), WHOLE), kitchen,
                WHOLE));

        assertEquals(List.of(UnitKey.hazard(H1)), requiring(B, recordedByA(safety(openHazard(H1,
                HazardSeverity.IMPORTANT)), WHOLE), safety(openHazard(H1, HazardSeverity.MINOR)), WHOLE));

        AmenitiesContent items = amenities(SPACE_1);
        AmenitiesContent noLift = new AmenitiesContent(List.of(item(AmenityCode.ELEVATOR, YesNo.YES,
                ChecklistSource.OBSERVED_ON_SITE, null, null)), items.spaces(), items.otherItems());
        assertEquals(List.of(UnitKey.sharedItem(AmenityCode.ELEVATOR)), requiring(B, recordedByA(items, WHOLE),
                noLift, WHOLE));

        assertEquals(List.of(rent()), requiring(B, recordedByA(terms(SPACE_1), WHOLE),
                withRent(SourcedField.of(RentMoney.monthly(13_000_000), ChecklistSource.OBSERVED_ON_SITE)), WHOLE));

        assertEquals(List.of(UnitKey.commonTerm("electricity")), requiring(B, recordedByA(terms(SPACE_1), WHOLE),
                withCommonElectricity(charge(4_000, ChargeUnit.PER_KWH)), WHOLE));
    }

    @Test
    void removalOfAsSpace_needsACorrection() {
        assertEquals(List.of(UnitKey.space(SPACE_2)), requiring(B, recordedByA(PARTS, PARTS),
                identificationParts(observedSpace(SPACE_1, "P101")), identificationParts(observedSpace(SPACE_1,
                        "P101"))));
    }

    @Test
    void secondChangeOfCorrectedValue_needsCorrectionAgain() {
        GroupDocument corrected = document(dims(dimensionsSpace(SPACE_1)), Map.of(area(),
                new UnitMeta(B, T, A, ARRIVAL, null, ChecklistSource.MEASURED, 9L)));

        assertEquals(List.of(area()), requiring(B, corrected, dims(withArea(SourcedField.of(new BigDecimal("51.0"),
                ChecklistSource.MEASURED))), WHOLE));
    }

    @Test
    void onSiteChargeTurnedIntoOwnerStatement_needsACorrection() {
        assertEquals(List.of(UnitKey.commonTerm("electricity")), requiring(B, recordedByA(terms(SPACE_1), WHOLE),
                withCommonElectricity(SourcedField.ownerStated(new Charge(
                        ChargeBasis.AMOUNT, Money.vnd(3_500),
                        ChargeUnit.PER_KWH, null), D2, B, null)), WHOLE));
    }

    @Test
    void ownerStatedRent_replacedByANewStatementLaterAndOwnReceiver_isFree() {
        assertEquals(List.of(), requiring(B, statedRent(12_000_000, D1, A), withRent(SourcedField.ownerStated(
                RentMoney.monthly(13_000_000), D2, B, null)), WHOLE));
    }

    @Test
    void ownerStatement_editedKeepingDateAndReceiver_needsACorrection() {
        assertEquals(List.of(rent()), requiring(B, statedRent(12_000_000, D1, A), withRent(SourcedField.ownerStated(
                RentMoney.monthly(13_000_000), D1, A, "relay")), WHOLE));
    }

    @Test
    void ownerStatement_removed_needsACorrection() {
        assertEquals(List.of(rent()), requiring(B, statedRent(12_000_000, D1, A), withRent(SourcedField.unknown()),
                WHOLE));
    }

    @Test
    void ownerFormRent_replaced_isFree() {
        GroupDocument stored = document(withRent(SourcedField.of(RentMoney.monthly(15_000_000),
                ChecklistSource.OWNER_FORM)), Map.of(rent(), new UnitMeta(A, T, null, LocalDate.parse("2026-10-01"),
                "askingPrice", ChecklistSource.OWNER_FORM, null)));

        assertEquals(List.of(), requiring(B, stored, withRent(SourcedField.of(RentMoney.monthly(14_000_000),
                ChecklistSource.OBSERVED_ON_SITE)), WHOLE));
    }

    @Test
    void performerAndLastEditor_changesOwnValue_isFree() {
        assertEquals(List.of(), requiring(A, recordedByA(dims(dimensionsSpace(SPACE_1)), WHOLE),
                dims(withArea(SourcedField.of(new BigDecimal("50.0"), ChecklistSource.MEASURED))), WHOLE));
    }

    /** spec 4.5: a starter who is not the performer typed an on-site value; it is attributed to the performer. */
    @Test
    void starterNotPerformer_ownTypedOnSiteValue_needsCorrection() {
        GroupDocument typedByB = document(dims(dimensionsSpace(SPACE_1)), Map.of(area(),
                new UnitMeta(B, T, A, ARRIVAL, null, ChecklistSource.MEASURED, null)));

        assertEquals(List.of(area()), requiring(B, typedByB, dims(withArea(SourcedField.of(new BigDecimal("50.0"),
                ChecklistSource.MEASURED))), WHOLE));
    }

    // ==================== W16-44 ====================

    @Test
    void clearedAreaOfA_refilledByB_needsCorrectionAgain() {
        GroupDocument cleared = document(dims(withArea(SourcedField.unknown())), Map.of(area(),
                new UnitMeta(B, T, A, ARRIVAL, null, ChecklistSource.MEASURED, 9L)));

        assertEquals(List.of(area()), requiring(B, cleared, dims(dimensionsSpace(SPACE_1)), WHOLE));
    }

    @Test
    void noneSeenHazardList_firstHazardAdded_needsCorrection() {
        assertEquals(List.of(UnitKey.hazardList()), requiring(B, recordedByA(safety(), WHOLE),
                safety(openHazard(H1, HazardSeverity.MINOR)), WHOLE));
    }

    @Test
    void hazardAddedToANonEmptyList_isFree() {
        assertEquals(List.of(), requiring(B, recordedByA(safety(openHazard(H1, HazardSeverity.MINOR)), WHOLE),
                safety(openHazard(H1, HazardSeverity.MINOR), openHazard(H2, HazardSeverity.IMPORTANT)), WHOLE));
    }

    @Test
    void feeListOfA_changedByB_needsCorrection() {
        TermsCommon common = termsCommon();
        TermsContent changed = new TermsContent(new TermsCommon(common.electricity(), common.water(),
                common.internet(), common.parking(), common.serviceFee(), SourcedField.of(List.of(new Fee("Phí rác",
                Money.vnd(20_000), ChargeUnit.PER_MONTH)), ChecklistSource.OBSERVED_ON_SITE), common.houseRules(),
                common.pets(), common.handoverMaintenance()), terms(SPACE_1).spaces());

        assertEquals(List.of(UnitKey.commonTerm("otherFees")), requiring(B, recordedByA(terms(SPACE_1), WHOLE),
                changed, WHOLE));
    }

    /**
     * spec A-19: an override replaces what A's recorded common charge gives that space; the correction covers the new
     * override (it carries the pointer to the correcting save, spec 4.5) and the common charge it overrides.
     */
    @Test
    void chargeOverride_underOthersCommonCharge_needsCorrection() {
        TermsContent stored = partsTerms(ChargeOverrides.none());
        TermsContent next = partsTerms(new ChargeOverrides(charge(4_000, ChargeUnit.PER_KWH), null, null, null,
                null));

        assertEquals(List.of(UnitKey.override(SPACE_1, "electricity"), UnitKey.commonTerm("electricity")),
                requiring(B, recordedByA(stored, PARTS), next, PARTS));
    }

    @Test
    void spaceFees_underOthersCommonFees_isFree() {
        TermsContent stored = partsTerms(ChargeOverrides.none());
        TermsContent next = new TermsContent(stored.common(), List.of(termsSpace(SPACE_1, ChargeOverrides.none(),
                SourcedField.of(List.of(new Fee("Phí thang máy", Money.vnd(30_000), ChargeUnit.PER_MONTH)),
                        ChecklistSource.OBSERVED_ON_SITE)), stored.spaces().get(1)));

        assertEquals(List.of(), requiring(B, recordedByA(stored, PARTS), next, PARTS));
    }

    @Test
    void overrideAsANewStatementAgainstAnOwnerStatedCommonCharge_isFree() {
        TermsContent stored = partsTermsWithCommon(SourcedField.ownerStated(new Charge(
                ChargeBasis.INCLUDED, null, null, null), D1, A, null),
                ChargeOverrides.none());
        TermsContent next = partsTermsWithCommon(stored.common().electricity(), new ChargeOverrides(
                SourcedField.ownerStated(new Charge(ChargeBasis.AMOUNT,
                        Money.vnd(4_000), ChargeUnit.PER_KWH, null), D2, B, null), null, null, null, null));
        Map<UnitKey, UnitMeta> meta = Map.of(UnitKey.commonTerm("electricity"),
                new UnitMeta(A, T, A, D1, null, ChecklistSource.OWNER_STATED, null));

        assertEquals(List.of(), requiring(B, document(stored, meta), next, PARTS));
    }

    @Test
    void newFixReportOnAsHazard_nothingElseChanged_isFree() {
        assertEquals(List.of(), requiring(B, recordedByA(safety(openHazard(H1, HazardSeverity.IMPORTANT)), WHOLE),
                safety(reported(D2, B, "Chủ nói đã sửa")), WHOLE));
    }

    @Test
    void fixReportOfA_removedByB_needsCorrection() {
        assertEquals(List.of(UnitKey.hazard(H1)), requiring(B, reportedByA(D1, A, "Chủ nói đã sửa"),
                safety(openHazard(H1, HazardSeverity.IMPORTANT)), WHOLE));
    }

    @Test
    void fixReport_replacedByALaterOne_isFree() {
        assertEquals(List.of(), requiring(B, reportedByA(D1, A, "Đã sửa"), safety(reported(D2, A, "Đã sửa")), WHOLE));
    }

    @Test
    void fixReport_sameDateOtherReceiverOtherNote_isFree() {
        assertEquals(List.of(), requiring(B, reportedByA(D1, A, "Đã sửa"), safety(reported(D1, B, "Đã thay dây")),
                WHOLE));
    }

    @Test
    void fixReport_sameDateAndNoteOtherReceiver_needsCorrection() {
        assertEquals(List.of(UnitKey.hazard(H1)), requiring(B, reportedByA(D1, A, "Đã sửa"),
                safety(reported(D1, B, "Đã sửa")), WHOLE));
    }

    @Test
    void fixReport_sameDateAndReceiverOtherNote_needsCorrection() {
        assertEquals(List.of(UnitKey.hazard(H1)), requiring(B, reportedByA(D1, A, "Đã sửa"),
                safety(reported(D1, A, "Đã thay dây")), WHOLE));
    }

    /** BR-27: the report's receiver protects the report also from the performer who entered the hazard. */
    @Test
    void fixReport_performerEditsReportHeardByC_needsCorrection() {
        assertEquals(List.of(UnitKey.hazard(H1)), requiring(A, reportedByA(D1, C, "Đã sửa"),
                safety(reported(D1, C, "Đã sửa xong")), WHOLE));
        assertEquals(List.of(UnitKey.hazard(H1)), requiring(A, reportedByA(D1, C, "Đã sửa"),
                safety(openHazard(H1, HazardSeverity.IMPORTANT)), WHOLE));
    }

    @Test
    void ownerFormRefill_isFree() {
        GroupDocument cleared = document(withRent(SourcedField.unknown()), Map.of(rent(),
                new UnitMeta(A, T, null, LocalDate.parse("2026-10-01"), "askingPrice", ChecklistSource.OWNER_FORM,
                        null)));

        assertEquals(List.of(), requiring(B, cleared, withRent(SourcedField.of(RentMoney.monthly(15_000_000),
                ChecklistSource.OWNER_FORM)), WHOLE));
    }

    @Test
    void clearedOwnerStatement_laterDateRefill_isFree() {
        assertEquals(List.of(), requiring(B, clearedStatement(D1), withRent(SourcedField.ownerStated(
                RentMoney.monthly(12_000_000), D2, B, null)), WHOLE));
    }

    @Test
    void clearedOwnerStatement_sameDateRefill_needsCorrection() {
        assertEquals(List.of(rent()), requiring(B, clearedStatement(D1), withRent(SourcedField.ownerStated(
                RentMoney.monthly(13_000_000), D1, B, null)), WHOLE));
    }

    /** spec A-19: after PARTS → WHOLE → PARTS the shared wet areas come back from their tombstones. */
    @Test
    void sharedKitchenBackFromTombstone_refilledByB_needsCorrection() {
        UnitKey kitchen = UnitKey.field(ChecklistGroup.CONDITION, "sharedKitchen");
        GroupDocument stored = new GroupDocument(ChecklistGroup.CONDITION, new ConditionContent(null, null,
                List.of(conditionSpace(SPACE_1), conditionSpace(SPACE_2))), Map.of(),
                Map.of(kitchen, Tombstone.of(new UnitMeta(A, T, A, ARRIVAL, null, null, null))));

        assertEquals(List.of(kitchen), requiring(B, stored, new ConditionContent(Field.value(inspected()),
                Field.unknown(), List.of(conditionSpace(SPACE_1), conditionSpace(SPACE_2))), PARTS));
    }

    @Test
    void overrideBackFromTombstone_needsCorrection() {
        UnitKey override = UnitKey.override(SPACE_1, "electricity");
        GroupDocument stored = new GroupDocument(ChecklistGroup.TERMS, partsTerms(ChargeOverrides.none()), Map.of(),
                Map.of(override, Tombstone.of(new UnitMeta(A, T, A, ARRIVAL, null, ChecklistSource.OBSERVED_ON_SITE,
                        null))));

        assertEquals(List.of(override), requiring(B, stored, partsTerms(new ChargeOverrides(
                charge(4_000, ChargeUnit.PER_KWH), null, null, null, null)), PARTS));
    }

    @Test
    void ownerStatement_sameDateAndValueOtherReceiver_needsCorrection() {
        assertEquals(List.of(rent()), requiring(B, statedRent(12_000_000, D1, A), withRent(SourcedField.ownerStated(
                RentMoney.monthly(12_000_000), D1, B, null)), WHOLE));
    }

    @Test
    void ownerStatement_sameDayOtherReceiverOtherValue_isFree() {
        assertEquals(List.of(), requiring(B, statedRent(12_000_000, D1, A), withRent(SourcedField.ownerStated(
                RentMoney.monthly(11_000_000), D1, B, null)), WHOLE));
    }

    @Test
    void g6PresenceChangedBySameDayOtherReceiver_isFree() {
        AmenitiesContent stored = new AmenitiesContent(List.of(item(AmenityCode.CCTV, YesNo.YES,
                ChecklistSource.OWNER_STATED, D1, A)), List.of(), List.of());
        AmenitiesContent next = new AmenitiesContent(List.of(item(AmenityCode.CCTV, YesNo.NO,
                ChecklistSource.OWNER_STATED, D1, B)), List.of(), List.of());
        Map<UnitKey, UnitMeta> meta = Map.of(UnitKey.sharedItem(AmenityCode.CCTV),
                new UnitMeta(A, T, A, D1, null, ChecklistSource.OWNER_STATED, null));

        assertEquals(List.of(), requiring(B, document(stored, meta), next, WHOLE));
    }

    @Test
    void note_othersNote_isFree() {
        IdentificationContent stored = new IdentificationContent(WHOLE.observedAddress(), null, WHOLE.propertyType(),
                WHOLE.fit(), WHOLE.rentalScope(), WHOLE.spaces(), null, "Ghi chú của A");
        IdentificationContent next = new IdentificationContent(WHOLE.observedAddress(), null, WHOLE.propertyType(),
                WHOLE.fit(), WHOLE.rentalScope(), WHOLE.spaces(), null, "Ghi chú của B");

        assertEquals(List.of(), requiring(B, recordedByA(stored, WHOLE), next, next));
    }

    // ==================== W16-46: G6 identity across removal (BR-28, BR-29, spec A-29) ====================
    // Each removal is written as the save writes it (MetaRules.removed, GroupDocuments.apply); each return is the
    // diff of a later save over that document.

    @Test
    void g6SharedItem_removedThenAddedBack_needsCorrection() {
        GroupDocument removed = save(B, g6ByA(), g6(List.of(WATER_HEATER, ELEVATOR), List.of(BED_C), List.of(DESK),
                List.of(ITEM_X, ITEM_Y)));

        assertEquals(List.of(SHARED_AC), requiring(B, removed, g6Base(), PARTS));
    }

    @Test
    void g6SharedItem_unrelatedSaveBetween_stillNeedsCorrection() {
        GroupDocument removed = save(B, g6ByA(), g6(List.of(WATER_HEATER, ELEVATOR), List.of(BED_C), List.of(DESK),
                List.of(ITEM_X, ITEM_Y)));
        GroupDocument between = save(B, removed, g6(List.of(WATER_HEATER, ELEVATOR), List.of(BED_C), List.of(DESK),
                List.of(ITEM_X, ITEM_Y, ITEM_Z)));

        assertEquals(removed.tombstone(SHARED_AC), between.tombstone(SHARED_AC));
        assertEquals(List.of(SHARED_AC), requiring(B, between, g6(List.of(AIR_CONDITIONER, WATER_HEATER, ELEVATOR),
                List.of(BED_C), List.of(DESK), List.of(ITEM_X, ITEM_Y, ITEM_Z)), PARTS));
    }

    @Test
    void g6SpaceItem_removedThenAddedBack_needsCorrection() {
        GroupDocument removed = save(B, g6ByA(), g6(List.of(AIR_CONDITIONER, WATER_HEATER, ELEVATOR),
                List.of(BED_C), List.of(), List.of(ITEM_X, ITEM_Y)));

        assertEquals(List.of(UnitKey.spaceItem(SPACE_2, AmenityCode.DESK)), requiring(B, removed, g6Base(), PARTS));
    }

    /** spec A-29: over an on-site tombstone an owner statement is no new statement. */
    @Test
    void g6OnSiteTombstone_ownerStatementLaterDate_needsCorrection() {
        GroupDocument removed = save(B, g6ByA(), g6(List.of(WATER_HEATER, ELEVATOR), List.of(BED_C), List.of(DESK),
                List.of(ITEM_X, ITEM_Y)));
        AmenityItem stated = item(AmenityCode.AIR_CONDITIONER, YesNo.YES, ChecklistSource.OWNER_STATED, D2, B);

        assertEquals(List.of(SHARED_AC), requiring(B, removed, g6(List.of(stated, WATER_HEATER, ELEVATOR),
                List.of(BED_C), List.of(DESK), List.of(ITEM_X, ITEM_Y)), PARTS));
    }

    /** Guard (BR-27 kept by BR-28): an owner statement later than the tombstone's date is a new statement. */
    @Test
    void g6ReturningOwnerStatement_laterDate_isFree() {
        GroupDocument removed = save(B, g6ByA(), g6(List.of(AIR_CONDITIONER, WATER_HEATER, ELEVATOR), List.of(),
                List.of(DESK), List.of(ITEM_X, ITEM_Y)));
        AmenityItem later = item(AmenityCode.BED, YesNo.YES, ChecklistSource.OWNER_STATED, D2, B);

        assertEquals(List.of(), requiring(B, removed, g6(List.of(AIR_CONDITIONER, WATER_HEATER, ELEVATOR),
                List.of(later), List.of(DESK), List.of(ITEM_X, ITEM_Y)), PARTS));
    }

    /** BR-28: no business value is left to compare, so a same-date statement needs a correction. */
    @Test
    void g6ReturningOwnerStatement_sameDateOtherReceiver_needsCorrection() {
        GroupDocument removed = save(B, g6ByA(), g6(List.of(AIR_CONDITIONER, WATER_HEATER, ELEVATOR), List.of(),
                List.of(DESK), List.of(ITEM_X, ITEM_Y)));
        AmenityItem sameDay = item(AmenityCode.BED, YesNo.NO, ChecklistSource.OWNER_STATED, D1, B);

        assertEquals(List.of(UnitKey.spaceItem(SPACE_1, AmenityCode.BED)), requiring(B, removed,
                g6(List.of(AIR_CONDITIONER, WATER_HEATER, ELEVATOR), List.of(sameDay), List.of(DESK),
                        List.of(ITEM_X, ITEM_Y)), PARTS));
    }

    /** Guard (BR-28): the same code at another placement is a new entry. */
    @Test
    void g6SameCodeOtherPlacement_isFree() {
        GroupDocument removed = save(B, g6ByA(), g6(List.of(WATER_HEATER, ELEVATOR), List.of(BED_C), List.of(DESK),
                List.of(ITEM_X, ITEM_Y)));

        assertEquals(List.of(), requiring(B, removed, g6(List.of(WATER_HEATER, ELEVATOR),
                List.of(AIR_CONDITIONER, BED_C), List.of(DESK), List.of(ITEM_X, ITEM_Y)), PARTS));
    }

    @Test
    void otherItem_sameItemIdBack_needsCorrection() {
        GroupDocument removed = save(B, g6ByA(), g6(List.of(AIR_CONDITIONER, WATER_HEATER, ELEVATOR),
                List.of(BED_C), List.of(DESK), List.of(ITEM_Y)));
        OtherItem changed = new OtherItem(ITEM_X.itemId(), "Máy lọc nước RO", SPACE_2, YesNo.NO,
                ChecklistSource.OBSERVED_ON_SITE, null, null, null);

        assertEquals(List.of(OTHER_X), requiring(B, removed, g6Base(), PARTS));
        assertEquals(List.of(OTHER_X), requiring(B, removed, g6(List.of(AIR_CONDITIONER, WATER_HEATER, ELEVATOR),
                List.of(BED_C), List.of(DESK), List.of(changed, ITEM_Y)), PARTS));
    }

    /** Guard (BR-28): a new itemId with a removed item's label is a new entry; labels are never matched. */
    @Test
    void otherItem_newItemIdSameLabel_isFree() {
        GroupDocument removed = save(B, g6ByA(), g6(List.of(AIR_CONDITIONER, WATER_HEATER, ELEVATOR),
                List.of(BED_C), List.of(DESK), List.of(ITEM_Y)));
        OtherItem sameLabel = new OtherItem(ITEM_Z.itemId(), ITEM_X.label(), SPACE_1, YesNo.YES,
                ChecklistSource.OBSERVED_ON_SITE, null, null, null);

        assertEquals(List.of(), requiring(B, removed, g6(List.of(AIR_CONDITIONER, WATER_HEATER, ELEVATOR),
                List.of(BED_C), List.of(DESK), List.of(sameLabel, ITEM_Y)), PARTS));
    }

    /**
     * BR-29, spec A-12, A-29: P2's removal takes Y with it; Y's tombstone (the G1 saver as last editor) stays, the
     * DESK tombstone goes with P2, and Y sent again without a space needs a correction.
     */
    @Test
    void otherItem_backAfterItsSpaceRemoved_needsCorrection() {
        GroupDocument withoutDesk = save(B, g6ByA(), g6(List.of(AIR_CONDITIONER, WATER_HEATER, ELEVATOR),
                List.of(BED_C), List.of(), List.of(ITEM_X, ITEM_Y)));
        IdentificationContent onlyP1 = identificationParts(observedSpace(SPACE_1, "P101"));
        GroupContent cascaded = GroupCascade.of(PARTS, onlyP1, Map.of(ChecklistGroup.AMENITIES, withoutDesk))
                .changed().get(ChecklistGroup.AMENITIES);
        GroupDocument afterRemoval = saved(withoutDesk, PARTS, cascaded, onlyP1, B, T3, 11L);
        OtherItem yWithoutSpace = new OtherItem(ITEM_Y.itemId(), ITEM_Y.label(), null, YesNo.YES,
                ChecklistSource.OBSERVED_ON_SITE, null, null, null);

        assertEquals(B, afterRemoval.tombstone(OTHER_Y).enteredByUserId());
        assertEquals(A, afterRemoval.tombstone(OTHER_Y).observedByUserId());
        assertEquals(List.of(OTHER_Y), afterRemoval.tombstones().keySet().stream()
                .filter(key -> key.value().startsWith("amenities/otherItems/") || SPACE_2.equals(key.spaceId()))
                .toList());
        assertEquals(List.of(OTHER_Y), requiring(B, afterRemoval, new AmenitiesContent(List.of(AIR_CONDITIONER,
                WATER_HEATER, ELEVATOR), List.of(new AmenitiesSpace(SPACE_1, SourcedField.unknown(), List.of(BED_C))),
                List.of(ITEM_X, yWithoutSpace)), onlyP1));
    }

    @Test
    void g6RemovedBeforeTakeover_putBackByNewHolder_needsCorrection() {
        GroupDocument removed = save(A, g6ByA(), g6(List.of(AIR_CONDITIONER, ELEVATOR), List.of(BED_C),
                List.of(DESK), List.of(ITEM_X, ITEM_Y)));

        assertEquals(List.of(UnitKey.sharedItem(AmenityCode.WATER_HEATER)), requiring(B, removed, g6Base(), PARTS));
    }

    /** BR-29: A, who observed and entered it, reverses B's removal. */
    @Test
    void g6Observer_putsBackWhatOtherRemoved_needsCorrection() {
        GroupDocument removed = save(B, g6ByA(), g6(List.of(WATER_HEATER, ELEVATOR), List.of(BED_C), List.of(DESK),
                List.of(ITEM_X, ITEM_Y)));

        assertEquals(List.of(SHARED_AC), requiring(A, removed, g6Base(), PARTS));
    }

    /** Guard (BR-29): A removes and puts back A's own item; nobody else is protected. */
    @Test
    void g6OwnItem_removedAndPutBackByObserver_isFree() {
        GroupDocument removed = save(A, g6ByA(), g6(List.of(AIR_CONDITIONER, ELEVATOR), List.of(BED_C),
                List.of(DESK), List.of(ITEM_X, ITEM_Y)));

        assertEquals(List.of(), requiring(A, removed, g6Base(), PARTS));
    }

    /** BR-29 on the scope tombstones: after B's PARTS → WHOLE → PARTS, A refilling A's shared wet areas needs one. */
    @Test
    void scopeRoundTrip_observerRefills_needsCorrection() {
        IdentificationContent onlyP1 = identificationParts(observedSpace(SPACE_1, "P101"));
        IdentificationContent whole = identification(RentalScope.WHOLE, onlyP1.spaces());
        GroupDocument byA = saved(GroupDocument.initial(ChecklistGroup.CONDITION), onlyP1, conditionParts(SPACE_1),
                onlyP1, A, T, null);
        GroupContent cleared = GroupCascade.of(onlyP1, whole, Map.of(ChecklistGroup.CONDITION, byA)).changed()
                .get(ChecklistGroup.CONDITION);
        GroupDocument afterWhole = saved(byA, onlyP1, cleared, whole, B, T2, 11L);

        assertEquals(List.of(UnitKey.field(ChecklistGroup.CONDITION, "sharedKitchen"),
                UnitKey.field(ChecklistGroup.CONDITION, "sharedBathroom")), requiring(A, afterWhole,
                conditionParts(SPACE_1), onlyP1));
    }

    // ==================== W16-47: the same hazardId across removal (BR-30, spec A-29) ====================

    @Test
    void hazard_removedThenRestoredByRemover_observerProtected() {
        GroupDocument removed = save(B, g5ByA(), safety(H2_REPORTED_BY_C, H3_MINOR), WHOLE);

        assertEquals(List.of(UnitKey.hazard(H1)), requiring(B, removed, g5Base(), WHOLE));
    }

    /** BR-30: the performer removes and restores a hazard whose fix report C heard; only C is protected. */
    @Test
    void hazard_removedWithReportThenRestoredByRemover_reportReceiverProtected() {
        GroupDocument removed = save(A, g5ByA(), safety(H1_IMPORTANT, H3_MINOR), WHOLE);

        assertEquals(new Tombstone.OwnerReportFacts(C, D1), removed.tombstone(UnitKey.hazard(H2)).ownerReportOrNull());
        assertEquals(A, removed.tombstone(UnitKey.hazard(H2)).observedByUserId());
        assertEquals(List.of(UnitKey.hazard(H2)), requiring(A, removed, g5Base(), WHOLE));
    }

    @Test
    void hazard_observerRestoresWhatOtherRemoved_needsCorrection() {
        GroupDocument removed = save(B, g5ByA(), safety(H2_REPORTED_BY_C, H3_MINOR), WHOLE);

        assertEquals(List.of(UnitKey.hazard(H1)), requiring(A, removed, g5Base(), WHOLE));
    }

    /** BR-30: a returning hazard never uses the fix-report exception, and the rule never fails on it. */
    @Test
    void hazard_restoredAsOwnerReportedFixed_needsCorrection() {
        GroupDocument removed = save(B, g5ByA(), safety(H2_REPORTED_BY_C, H3_MINOR), WHOLE);
        Hazard reportedByB = new Hazard(H1, null, null, H1_IMPORTANT.description(), HazardSeverity.IMPORTANT,
                HazardStatus.OWNER_REPORTED_FIXED, "Chủ nói đã đi lại dây", D2, B, null);

        assertEquals(List.of(UnitKey.hazard(H1)), requiring(B, removed, safety(reportedByB, H2_REPORTED_BY_C,
                H3_MINOR), WHOLE));
    }

    /** spec A-29: for a returning hazard the rule reads the tombstone alone, never an old unit that is not there. */
    @Test
    void hazard_restoredWithoutOldContent_neverReadsMissingUnit() {
        GroupDocument stored = new GroupDocument(ChecklistGroup.SAFETY, safety(H3_MINOR), Map.of(UnitKey.hazard(H3),
                new UnitMeta(A, T, A, ARRIVAL, null, null, null)), Map.of(UnitKey.hazard(H1), new Tombstone(A, T2, A,
                ARRIVAL, null, null, null, new Tombstone.OwnerReportFacts(C, D1))));
        Hazard newerReport = new Hazard(H1, null, null, "Dây điện hở", HazardSeverity.IMPORTANT,
                HazardStatus.OWNER_REPORTED_FIXED, "Chủ nói đã sửa", D2, A, null);

        assertEquals(List.of(UnitKey.hazard(H1)), requiring(A, stored, safety(newerReport, H3_MINOR), WHOLE));
    }

    /** Guard (BR-29): A removes and restores A's own hazard. */
    @Test
    void hazard_ownItemRemovedAndRestored_isFree() {
        GroupDocument removed = save(A, g5ByA(), safety(H1_IMPORTANT, H2_REPORTED_BY_C), WHOLE);

        assertEquals(List.of(), requiring(A, removed, g5Base(), WHOLE));
    }

    /** Guard (BR-30): a new hazardId with a removed hazard's description is an added entry. */
    @Test
    void hazard_newHazardIdSameDescription_isFree() {
        GroupDocument removed = save(B, g5ByA(), safety(H2_REPORTED_BY_C, H3_MINOR), WHOLE);
        Hazard renamed = new Hazard(H4, null, null, H1_IMPORTANT.description(), HazardSeverity.IMPORTANT,
                HazardStatus.OPEN, null, null, null, null);

        assertEquals(List.of(), requiring(B, removed, safety(renamed, H2_REPORTED_BY_C, H3_MINOR), WHOLE));
    }

    // ==================== helpers ====================

    /**
     * The document after {@code caller} saved {@code next} at {@code at}, as the save writes it (plan Task A11, A16):
     * changed units get {@link MetaRules#next}, removed ones {@link MetaRules#removed}, all pointing to
     * {@code correctionSaveId} when one is given.
     */
    private static GroupDocument saved(GroupDocument stored, IdentificationContent storedG1, GroupContent next,
                                       IdentificationContent g1, String caller, Instant at, Long correctionSaveId) {
        Map<UnitKey, UnitMeta> meta = new HashMap<>();
        Map<UnitKey, Tombstone> stamps = new HashMap<>();
        for (UnitChange change : UnitDiff.between(stored, storedG1, next, g1)) {
            if (change.kind() == ChangeKind.REMOVED) {
                Tombstone stamp = MetaRules.removed(change, caller, at);
                stamps.put(change.key(), correctionSaveId == null ? stamp : stamp.withCorrection(correctionSaveId));
            } else {
                UnitMeta changed = MetaRules.next(change, caller, at, VISIT, Optional.empty());
                meta.put(change.key(), correctionSaveId == null ? changed : changed.withCorrection(correctionSaveId));
            }
        }
        return GroupDocuments.apply(stored, TemplateV1Rules.canonical(next, g1), meta, stamps);
    }

    /** A save of G6 in P1 and P2 (with a correction when the caller is not A), or of G5 in WHOLE. */
    private static GroupDocument save(String caller, GroupDocument stored, GroupContent next) {
        return save(caller, stored, next, PARTS);
    }

    private static GroupDocument save(String caller, GroupDocument stored, GroupContent next,
                                      IdentificationContent g1) {
        return saved(stored, g1, next, g1, caller, caller.equals(A) ? T : T2, caller.equals(A) ? null : 9L);
    }

    /** W16-46 setup: shared AIR_CONDITIONER, WATER_HEATER and ELEVATOR seen on site, P1's BED heard by C, P2's DESK. */
    private static AmenitiesContent g6Base() {
        return g6(List.of(AIR_CONDITIONER, WATER_HEATER, ELEVATOR), List.of(BED_C), List.of(DESK),
                List.of(ITEM_X, ITEM_Y));
    }

    private static GroupDocument g6ByA() {
        return saved(GroupDocument.initial(ChecklistGroup.AMENITIES), PARTS, g6Base(), PARTS, A, T, null);
    }

    private static AmenitiesContent g6(List<AmenityItem> shared, List<AmenityItem> p1, List<AmenityItem> p2,
                                       List<OtherItem> others) {
        return new AmenitiesContent(shared, List.of(new AmenitiesSpace(SPACE_1, SourcedField.unknown(), p1),
                new AmenitiesSpace(SPACE_2, SourcedField.unknown(), p2)), others);
    }

    /** W16-47 setup: H1 IMPORTANT OPEN, H2 MINOR with a fix report C heard on D1, H3 MINOR OPEN. */
    private static SafetyContent g5Base() {
        return safety(H1_IMPORTANT, H2_REPORTED_BY_C, H3_MINOR);
    }

    private static GroupDocument g5ByA() {
        return saved(GroupDocument.initial(ChecklistGroup.SAFETY), WHOLE, g5Base(), WHOLE, A, T, null);
    }

    private static List<UnitKey> requiring(String caller, GroupDocument stored, GroupContent next,
                                           IdentificationContent g1) {
        IdentificationContent storedG1 = stored.content() instanceof IdentificationContent g ? g : g1;
        return CorrectionPolicy.requiring(stored, UnitDiff.between(stored, storedG1, next, g1), caller, VISIT);
    }

    /** Every unit of the content holding a value recorded by A at A's visit. */
    private static GroupDocument recordedByA(GroupContent content, IdentificationContent g1) {
        Map<UnitKey, UnitMeta> meta = new HashMap<>();
        for (var unit : ChecklistUnits.of(InputForm.ofStored(document(content, Map.of()), g1)).values()) {
            if (!unit.isUnknown()) {
                meta.put(unit.key(), new UnitMeta(A, T, A, ARRIVAL, null, unit.source(), null));
            }
        }
        return document(content, meta);
    }

    private static GroupDocument document(GroupContent content, Map<UnitKey, UnitMeta> meta) {
        return new GroupDocument(content.group(), content, meta, Map.of());
    }

    private static UnitKey area() {
        return UnitKey.spaceField(ChecklistGroup.DIMENSIONS, SPACE_1, "area");
    }

    private static UnitKey rent() {
        return UnitKey.spaceField(ChecklistGroup.TERMS, SPACE_1, "monthlyRent");
    }

    private static DimensionsContent dims(DimensionsSpace space) {
        return new DimensionsContent(List.of(space));
    }

    private static DimensionsSpace withArea(SourcedField<BigDecimal> area) {
        DimensionsSpace s = dimensionsSpace(SPACE_1);
        return new DimensionsSpace(SPACE_1, area, s.bedrooms(), s.privateBathrooms(), s.sharedBathroomUse(), null);
    }

    private static ConditionSpace withKitchen(AreaCheck kitchen) {
        ConditionSpace s = conditionSpace(SPACE_1);
        return new ConditionSpace(SPACE_1, s.occupancy(), s.expectedVacantFrom(), s.availableFrom(), s.mainSpace(),
                Field.value(kitchen), s.bathroom());
    }

    private static AmenityItem item(AmenityCode code, YesNo presence, ChecklistSource source, LocalDate on,
                                    String receiver) {
        return new AmenityItem(code, presence, source, on, receiver, null);
    }

    private static TermsContent withRent(SourcedField<RentMoney> rent) {
        TermsSpace s = termsSpace(SPACE_1, null, null);
        return new TermsContent(termsCommon(), List.of(new TermsSpace(SPACE_1, rent, s.deposit(),
                s.paymentPeriodMonths(), s.minimumTermMonths(), null, null)));
    }

    private static TermsContent withCommonElectricity(SourcedField<Charge> electricity) {
        TermsCommon c = termsCommon();
        return new TermsContent(new TermsCommon(electricity, c.water(), c.internet(), c.parking(), c.serviceFee(),
                c.otherFees(), c.houseRules(), c.pets(), c.handoverMaintenance()), terms(SPACE_1).spaces());
    }

    private static TermsContent partsTerms(ChargeOverrides firstSpace) {
        return partsTermsWithCommon(termsCommon().electricity(), firstSpace);
    }

    private static TermsContent partsTermsWithCommon(SourcedField<Charge> electricity, ChargeOverrides firstSpace) {
        TermsCommon c = termsCommon();
        return new TermsContent(new TermsCommon(electricity, c.water(), c.internet(), c.parking(), c.serviceFee(),
                c.otherFees(), c.houseRules(), c.pets(), c.handoverMaintenance()),
                List.of(termsSpace(SPACE_1, firstSpace, null), termsSpace(SPACE_2, ChargeOverrides.none(), null)));
    }

    private static GroupDocument statedRent(long amount, LocalDate on, String receiver) {
        return document(withRent(SourcedField.ownerStated(RentMoney.monthly(amount), on, receiver,
                receiver.equals(A) ? null : "relay")), Map.of(rent(), new UnitMeta(A, T, receiver, on, null,
                ChecklistSource.OWNER_STATED, null)));
    }

    private static GroupDocument clearedStatement(LocalDate on) {
        return document(withRent(SourcedField.unknown()), Map.of(rent(), new UnitMeta(A, T, A, on, null,
                ChecklistSource.OWNER_STATED, null)));
    }

    private static Hazard reported(LocalDate on, String receiver, String note) {
        return new Hazard(H1, null, null, "Dây điện hở ở hành lang", HazardSeverity.IMPORTANT,
                HazardStatus.OWNER_REPORTED_FIXED, note, on, receiver, null);
    }

    private static GroupDocument reportedByA(LocalDate on, String receiver, String note) {
        SafetyContent content = safety(reported(on, receiver, note));
        return recordedByA(content, WHOLE);
    }
}
