package vn.sunvibe.survey.core.testsupport;

import vn.sunvibe.survey.core.domain.model.checklist.AccessExtent;
import vn.sunvibe.survey.core.domain.model.checklist.ActualAccess;
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
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Deposit;
import vn.sunvibe.survey.core.domain.model.checklist.DepositBasis;
import vn.sunvibe.survey.core.domain.model.checklist.Fee;
import vn.sunvibe.survey.core.domain.model.checklist.Furnishing;
import vn.sunvibe.survey.core.domain.model.checklist.Hazard;
import vn.sunvibe.survey.core.domain.model.checklist.HazardSeverity;
import vn.sunvibe.survey.core.domain.model.checklist.HazardStatus;
import vn.sunvibe.survey.core.domain.model.checklist.Money;
import vn.sunvibe.survey.core.domain.model.checklist.Observation;
import vn.sunvibe.survey.core.domain.model.checklist.Occupancy;
import vn.sunvibe.survey.core.domain.model.checklist.Pets;
import vn.sunvibe.survey.core.domain.model.checklist.PetsPolicy;
import vn.sunvibe.survey.core.domain.model.checklist.RentMoney;
import vn.sunvibe.survey.core.domain.model.checklist.SafetyContent;
import vn.sunvibe.survey.core.domain.model.checklist.TermsCommon;
import vn.sunvibe.survey.core.domain.model.checklist.TermsContent;
import vn.sunvibe.survey.core.domain.model.checklist.TermsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsContent;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Field;
import vn.sunvibe.survey.core.domain.model.checklist.Fit;
import vn.sunvibe.survey.core.domain.model.checklist.FitAssessment;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedLocation;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedSpace;
import vn.sunvibe.survey.core.domain.model.checklist.PartsContent;
import vn.sunvibe.survey.core.domain.model.checklist.PartsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.PropertyTypeCode;
import vn.sunvibe.survey.core.domain.model.checklist.RentalScope;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.YesNo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Valid template v1 contents for the checklist tests: every needed field filled, as a Sale who saw the asset would
 * record it. Tests copy one and break exactly one value.
 */
public final class ChecklistFixture {

    public static final LocalDate TODAY = LocalDate.parse("2026-10-24");
    public static final String SPACE_1 = "01JSPACE000000000000000001";
    public static final String SPACE_2 = "01JSPACE000000000000000002";
    public static final String SPACE_3 = "01JSPACE000000000000000003";
    public static final String SALE = "01JSA1E0000000000000000001";

    private ChecklistFixture() {
    }

    /** A space id that is a ULID: the n-th of the fixture spaces. */
    public static String space(int n) {
        return String.format("01JSPACE%018d", n);
    }

    public static ObservedSpace observedSpace(String spaceId, String code) {
        return new ObservedSpace(spaceId, code, "Tầng 2, phía đường", null);
    }

    /** G1 of the whole property: one space A1, address seen on site, apartment, in scope. */
    public static IdentificationContent identificationWhole() {
        return identification(RentalScope.WHOLE, List.of(observedSpace(SPACE_1, "A1")));
    }

    /** G1 of separate parts with these spaces. */
    public static IdentificationContent identificationParts(ObservedSpace... spaces) {
        return identification(RentalScope.PARTS, List.of(spaces));
    }

    public static IdentificationContent identification(RentalScope scope, List<ObservedSpace> spaces) {
        return new IdentificationContent(
                SourcedField.of(new ObservedLocation("12 Bạch Đằng", "48", "20194", null, null),
                        ChecklistSource.OBSERVED_ON_SITE),
                null, Field.value(PropertyTypeCode.APARTMENT),
                Field.value(new FitAssessment(Fit.IN_SCOPE, "Căn hộ cho thuê dài hạn")), scope, spaces, null,
                null);
    }

    /** G2 with an entry for each space. */
    public static PartsContent parts(String... spaceIds) {
        List<PartsSpace> spaces = new ArrayList<>();
        for (String id : spaceIds) {
            spaces.add(partsSpace(id));
        }
        return new PartsContent(Field.value("Hẻm 3m từ đường chính"), Field.notApplicable("Không có khu chung"),
                SourcedField.of(5, ChecklistSource.OBSERVED_ON_SITE), spaces);
    }

    public static PartsSpace partsSpace(String spaceId) {
        return new PartsSpace(spaceId, Field.value("Tầng 2"), Field.value("Phòng khách, bếp, ban công"),
                Field.value(new ActualAccess(AccessExtent.ENTERED_ALL, null)));
    }

    /** G3 with an entry for each space. */
    public static DimensionsContent dimensions(String... spaceIds) {
        List<DimensionsSpace> spaces = new ArrayList<>();
        for (String id : spaceIds) {
            spaces.add(dimensionsSpace(id));
        }
        return new DimensionsContent(spaces);
    }

    public static DimensionsSpace dimensionsSpace(String spaceId) {
        return new DimensionsSpace(spaceId, SourcedField.of(new BigDecimal("45.5"), ChecklistSource.MEASURED),
                SourcedField.of(2, ChecklistSource.OBSERVED_ON_SITE),
                SourcedField.of(1, ChecklistSource.OBSERVED_ON_SITE),
                SourcedField.of(YesNo.NO, ChecklistSource.OBSERVED_ON_SITE), null);
    }

    /** An owner statement heard by the Sale on the date. */
    public static <V> SourcedField<V> ownerSaid(V value, LocalDate on) {
        return SourcedField.ownerStated(value, on, SALE, null);
    }

    public static AreaCheck inspected() {
        return new AreaCheck(AreaCheckResult.INSPECTED, true, null, null);
    }

    /** G4 of the whole property (no shared wet areas) with an entry for each space. */
    public static ConditionContent condition(String... spaceIds) {
        List<ConditionSpace> spaces = new ArrayList<>();
        for (String id : spaceIds) {
            spaces.add(conditionSpace(id));
        }
        return new ConditionContent(null, null, spaces);
    }

    /** G4 of separate parts: shared wet areas inspected. */
    public static ConditionContent conditionParts(String... spaceIds) {
        ConditionContent whole = condition(spaceIds);
        return new ConditionContent(Field.value(inspected()), Field.notApplicable("Không có phòng tắm chung"),
                whole.spaces());
    }

    public static ConditionSpace conditionSpace(String spaceId) {
        return new ConditionSpace(spaceId, Field.value(Occupancy.VACANT), SourcedField.unknown(),
                SourcedField.of(TODAY.plusDays(10), ChecklistSource.OBSERVED_ON_SITE), Field.value(inspected()),
                Field.value(inspected()), Field.value(inspected()));
    }

    /** G5 with one open important hazard of the first space. */
    public static SafetyContent safety(Hazard... hazards) {
        return new SafetyContent(Field.value("Cửa chính và cầu thang bộ"),
                Field.value(new Observation(true, null)), Field.value(List.of(hazards)));
    }

    public static Hazard openHazard(String hazardId, HazardSeverity severity) {
        return new Hazard(hazardId, null, null, "Dây điện hở ở hành lang", severity, HazardStatus.OPEN, null, null,
                null, null);
    }

    /** G6 with shared items and one entry per space. */
    public static AmenitiesContent amenities(String... spaceIds) {
        List<AmenitiesSpace> spaces = new ArrayList<>();
        for (String id : spaceIds) {
            spaces.add(new AmenitiesSpace(id, SourcedField.of(Furnishing.BASIC, ChecklistSource.OBSERVED_ON_SITE),
                    List.of(item(AmenityCode.AIR_CONDITIONER, YesNo.YES), item(AmenityCode.BED, YesNo.YES))));
        }
        return new AmenitiesContent(List.of(item(AmenityCode.ELEVATOR, YesNo.NO)), spaces, List.of());
    }

    public static AmenityItem item(AmenityCode code, YesNo presence) {
        return new AmenityItem(code, presence, ChecklistSource.OBSERVED_ON_SITE, null, null, null);
    }

    public static SourcedField<Charge> charge(long amount, ChargeUnit unit) {
        return SourcedField.of(new Charge(ChargeBasis.AMOUNT, Money.vnd(amount), unit, null),
                ChecklistSource.OBSERVED_ON_SITE);
    }

    /** G8 of the whole property: every term given on site, one entry per space without overrides or space fees. */
    public static TermsContent terms(String... spaceIds) {
        List<TermsSpace> spaces = new ArrayList<>();
        for (String id : spaceIds) {
            spaces.add(termsSpace(id, null, null));
        }
        return new TermsContent(termsCommon(), spaces);
    }

    public static TermsCommon termsCommon() {
        return new TermsCommon(charge(3_500, ChargeUnit.PER_KWH), charge(100_000, ChargeUnit.PER_PERSON_PER_MONTH),
                SourcedField.of(new Charge(ChargeBasis.INCLUDED, null, null, null), ChecklistSource.OBSERVED_ON_SITE),
                SourcedField.of(new Charge(ChargeBasis.NOT_OFFERED, null, null, null),
                        ChecklistSource.OBSERVED_ON_SITE),
                charge(0, ChargeUnit.PER_MONTH),
                SourcedField.of(List.of(new Fee("Phí vệ sinh", Money.vnd(50_000), ChargeUnit.PER_MONTH)),
                        ChecklistSource.OBSERVED_ON_SITE),
                SourcedField.of("Không hút thuốc trong nhà", ChecklistSource.OBSERVED_ON_SITE),
                SourcedField.of(new Pets(PetsPolicy.NOT_ALLOWED, null), ChecklistSource.OBSERVED_ON_SITE),
                SourcedField.of("Chủ sửa chữa hư hỏng lớn", ChecklistSource.OBSERVED_ON_SITE));
    }

    public static TermsSpace termsSpace(String spaceId, ChargeOverrides overrides, SourcedField<List<Fee>> spaceFees) {
        return new TermsSpace(spaceId, SourcedField.of(RentMoney.monthly(12_000_000), ChecklistSource.OBSERVED_ON_SITE),
                SourcedField.of(new Deposit(DepositBasis.MONTHS, 2, null), ChecklistSource.OBSERVED_ON_SITE),
                SourcedField.of(1, ChecklistSource.OBSERVED_ON_SITE),
                SourcedField.of(12, ChecklistSource.OBSERVED_ON_SITE), overrides, spaceFees);
    }
}
