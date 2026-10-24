package vn.sunvibe.survey.core.domain.policy.checklist;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesContent;
import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesSpace;
import vn.sunvibe.survey.core.domain.model.checklist.AmenityCode;
import vn.sunvibe.survey.core.domain.model.checklist.AmenityItem;
import vn.sunvibe.survey.core.domain.model.checklist.Charge;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeBasis;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeOverrides;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeUnit;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;
import vn.sunvibe.survey.core.domain.model.checklist.Deposit;
import vn.sunvibe.survey.core.domain.model.checklist.DepositBasis;
import vn.sunvibe.survey.core.domain.model.checklist.Fee;
import vn.sunvibe.survey.core.domain.model.checklist.Furnishing;
import vn.sunvibe.survey.core.domain.model.checklist.Money;
import vn.sunvibe.survey.core.domain.model.checklist.OtherItem;
import vn.sunvibe.survey.core.domain.model.checklist.Pets;
import vn.sunvibe.survey.core.domain.model.checklist.PetsPolicy;
import vn.sunvibe.survey.core.domain.model.checklist.RentMoney;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.TermsCommon;
import vn.sunvibe.survey.core.domain.model.checklist.TermsContent;
import vn.sunvibe.survey.core.domain.model.checklist.TermsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.YesNo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static vn.sunvibe.survey.core.domain.policy.checklist.TemplateV1RulesIdentificationTest.assertPaths;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SALE;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_1;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_2;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.TODAY;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.amenities;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.charge;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identificationParts;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identificationWhole;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.item;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.observedSpace;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.ownerSaid;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.terms;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.termsCommon;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.termsSpace;

/**
 * The stateless body rules of G6 and G8 (survey-checklist spec 4.6, 5.5 save step 1; W16-6, W16-11) with the money of
 * D-1, and the canonical form of their entries (spec PD-5).
 */
class TemplateV1RulesAmenitiesTermsTest {

    private static final String PHONE = "gọi 0905 123 456";
    private static final String ITEM_1 = "01JTEM000000000000000000A1";

    @Test
    void validAmenitiesAndTerms_passUnchanged() {
        TermsContent parts = new TermsContent(termsCommon(), List.of(termsSpace(SPACE_1, new ChargeOverrides(
                charge(4_000, ChargeUnit.PER_KWH), null, null, null, null), SourcedField.of(List.of(
                new Fee("Phí thang máy", Money.vnd(30_000), ChargeUnit.PER_MONTH)), ChecklistSource.OBSERVED_ON_SITE))));
        for (var content : List.of(amenities(SPACE_1, SPACE_2), terms(SPACE_1), parts)) {
            assertEquals(content, TemplateV1Rules.checkAndNormalise(content, TODAY));
        }
    }

    // ==================== G6 ====================

    @Test
    void items_aCodeAtMostOncePerLevel() {
        AmenitiesContent valid = amenities(SPACE_1);
        assertPaths(new AmenitiesContent(List.of(item(AmenityCode.CCTV, YesNo.YES), item(AmenityCode.CCTV, YesNo.NO)),
                valid.spaces(), valid.otherItems()), "content.sharedItems.1.code");
        assertPaths(new AmenitiesContent(valid.sharedItems(), List.of(new AmenitiesSpace(SPACE_1,
                SourcedField.unknown(), List.of(item(AmenityCode.BED, YesNo.YES), item(AmenityCode.BED, YesNo.YES)))),
                valid.otherItems()), "content.spaces.0.items.1.code");
        AmenitiesContent bothLevels = new AmenitiesContent(List.of(item(AmenityCode.BED, YesNo.NO)),
                List.of(new AmenitiesSpace(SPACE_1, SourcedField.unknown(), List.of(item(AmenityCode.BED, YesNo.YES)))),
                List.of());
        assertEquals(bothLevels, TemplateV1Rules.checkAndNormalise(bothLevels, TODAY));
    }

    @Test
    void items_sourceRules() {
        AmenitiesContent valid = amenities(SPACE_1);
        assertPaths(new AmenitiesContent(List.of(new AmenityItem(AmenityCode.CCTV, YesNo.YES,
                ChecklistSource.OWNER_FORM, null, null, null)), valid.spaces(), valid.otherItems()),
                "content.sharedItems.0.source");
        assertPaths(new AmenitiesContent(List.of(new AmenityItem(AmenityCode.CCTV, YesNo.YES,
                ChecklistSource.OWNER_STATED, TODAY, null, null)), valid.spaces(), valid.otherItems()),
                "content.sharedItems.0.receivedByUserId");
        assertPaths(new AmenitiesContent(List.of(new AmenityItem(AmenityCode.CCTV, null,
                ChecklistSource.OBSERVED_ON_SITE, null, null, null)), valid.spaces(), valid.otherItems()),
                "content.sharedItems.0.presence");
        assertPaths(new AmenitiesContent(List.of(new AmenityItem(AmenityCode.CCTV, YesNo.YES, null, null, null,
                null)), valid.spaces(), valid.otherItems()), "content.sharedItems.0.source");
        assertPaths(new AmenitiesContent(List.of(new AmenityItem(AmenityCode.CCTV, YesNo.YES,
                ChecklistSource.OBSERVED_ON_SITE, TODAY, null, null)), valid.spaces(), valid.otherItems()),
                "content.sharedItems.0.confirmedOn");
        AmenitiesContent ownerSaid = new AmenitiesContent(List.of(new AmenityItem(AmenityCode.CCTV, YesNo.YES,
                ChecklistSource.OWNER_STATED, TODAY, SALE, null)), valid.spaces(), valid.otherItems());
        assertEquals(ownerSaid, TemplateV1Rules.checkAndNormalise(ownerSaid, TODAY));
    }

    @Test
    void furnishing_sourceOfTheAmenityFamily() {
        assertPaths(new AmenitiesContent(List.of(), List.of(new AmenitiesSpace(SPACE_1,
                SourcedField.of(Furnishing.BASIC, ChecklistSource.MEASURED), List.of())), List.of()),
                "content.spaces.0.furnishing.source");
    }

    @Test
    void otherItems_atMostTwentyWithLabelsAndIds() {
        List<OtherItem> many = new ArrayList<>();
        for (int i = 1; i <= 21; i++) {
            many.add(other(String.format("01JTEM%020d", i), "Máy lọc nước"));
        }
        assertPaths(new AmenitiesContent(List.of(), List.of(), many), "content.otherItems");
        assertPaths(new AmenitiesContent(List.of(), List.of(), List.of(other(ITEM_1, "x".repeat(101)))),
                "content.otherItems.0.label");
        assertPaths(new AmenitiesContent(List.of(), List.of(), List.of(other(ITEM_1, PHONE))),
                "content.otherItems.0.label");
        assertPaths(new AmenitiesContent(List.of(), List.of(), List.of(other(ITEM_1, "A"), other(ITEM_1, "B"))),
                "content.otherItems.1.itemId");
        assertPaths(new AmenitiesContent(List.of(), List.of(), List.of(new OtherItem(ITEM_1, "Máy lọc", "P101",
                YesNo.YES, ChecklistSource.OBSERVED_ON_SITE, null, null, null))), "content.otherItems.0.spaceId");
    }

    @Test
    void canonical_amenityItemsInCodeOrder() {
        AmenitiesContent sent = new AmenitiesContent(
                List.of(item(AmenityCode.GARDEN, YesNo.NO), item(AmenityCode.AIR_CONDITIONER, YesNo.YES)),
                List.of(new AmenitiesSpace(SPACE_1, SourcedField.unknown(),
                        List.of(item(AmenityCode.WINDOW, YesNo.YES), item(AmenityCode.BED, YesNo.YES)))),
                List.of(other(ITEM_1, "Z"), other("01JTEM000000000000000000A2", "A")));

        AmenitiesContent canonical = (AmenitiesContent) TemplateV1Rules.canonical(sent,
                identificationParts(observedSpace(SPACE_1, "P101"), observedSpace(SPACE_2, "P102")));

        assertEquals(List.of(item(AmenityCode.AIR_CONDITIONER, YesNo.YES), item(AmenityCode.GARDEN, YesNo.NO)),
                canonical.sharedItems());
        assertEquals(List.of(item(AmenityCode.BED, YesNo.YES), item(AmenityCode.WINDOW, YesNo.YES)),
                canonical.spaces().get(0).items());
        assertEquals(new AmenitiesSpace(SPACE_2, SourcedField.unknown(), List.of()), canonical.spaces().get(1));
        assertEquals(sent.otherItems(), canonical.otherItems());
    }

    // ==================== G8 ====================

    /** W16-6 and spec D-1: rent at least 1 VND per month, other amounts at least 0, VND only. */
    @Test
    void money_rangesCurrencyAndUnit() {
        assertPaths(withRent(SourcedField.of(RentMoney.monthly(0), ChecklistSource.OBSERVED_ON_SITE)),
                "content.spaces.0.monthlyRent.value.amount");
        assertPaths(withRent(SourcedField.of(new RentMoney(12_000_000L, "USD", "MONTH"),
                ChecklistSource.OBSERVED_ON_SITE)), "content.spaces.0.monthlyRent.value.currency");
        assertPaths(withRent(SourcedField.of(new RentMoney(12_000_000L, "VND", "NIGHT"),
                ChecklistSource.OBSERVED_ON_SITE)), "content.spaces.0.monthlyRent.value.unit");
        assertPaths(withRent(SourcedField.of(RentMoney.monthly(Money.MAX_AMOUNT + 1),
                ChecklistSource.OBSERVED_ON_SITE)), "content.spaces.0.monthlyRent.value.amount");
        assertPaths(withCommon(c -> new TermsCommon(charge(-1, ChargeUnit.PER_KWH), c.water(), c.internet(),
                c.parking(), c.serviceFee(), c.otherFees(), c.houseRules(), c.pets(), c.handoverMaintenance())),
                "content.common.electricity.value.amount.amount");
        assertPaths(withCommon(c -> new TermsCommon(SourcedField.of(new Charge(ChargeBasis.AMOUNT,
                        new Money(3_500L, "USD"), ChargeUnit.PER_KWH, null), ChecklistSource.OBSERVED_ON_SITE),
                c.water(), c.internet(), c.parking(), c.serviceFee(), c.otherFees(), c.houseRules(), c.pets(),
                c.handoverMaintenance())), "content.common.electricity.value.amount.currency");
        TermsContent maxRent = withRent(SourcedField.of(RentMoney.monthly(Money.MAX_AMOUNT),
                ChecklistSource.OBSERVED_ON_SITE));
        assertEquals(maxRent, TemplateV1Rules.checkAndNormalise(maxRent, TODAY));
    }

    /** W16-6, W16-11: one deposit basis; 0 is no deposit. */
    @Test
    void deposit_oneBasisAndZeroIsNoDeposit() {
        assertPaths(withDeposit(new Deposit(DepositBasis.MONTHS, 2, Money.vnd(1_000_000))),
                "content.spaces.0.deposit.value.amount");
        assertPaths(withDeposit(new Deposit(DepositBasis.MONTHS, null, null)), "content.spaces.0.deposit.value.months");
        assertPaths(withDeposit(new Deposit(DepositBasis.AMOUNT, null, null)), "content.spaces.0.deposit.value.amount");
        assertPaths(withDeposit(new Deposit(DepositBasis.AMOUNT, 1, Money.vnd(5))),
                "content.spaces.0.deposit.value.months");
        assertPaths(withDeposit(new Deposit(DepositBasis.MONTHS, 13, null)), "content.spaces.0.deposit.value.months");
        assertPaths(withDeposit(new Deposit(null, 1, null)), "content.spaces.0.deposit.value.basis");
        for (Deposit none : List.of(new Deposit(DepositBasis.MONTHS, 0, null),
                new Deposit(DepositBasis.AMOUNT, null, Money.vnd(0)))) {
            assertEquals(withDeposit(none), TemplateV1Rules.checkAndNormalise(withDeposit(none), TODAY));
        }
    }

    @Test
    void charge_amountAndUnitExactlyForAmount() {
        assertPaths(withElectricity(new Charge(ChargeBasis.AMOUNT, Money.vnd(3_500), null, null)),
                "content.common.electricity.value.chargeUnit");
        assertPaths(withElectricity(new Charge(ChargeBasis.AMOUNT, null, ChargeUnit.PER_KWH, null)),
                "content.common.electricity.value.amount");
        assertPaths(withElectricity(new Charge(ChargeBasis.INCLUDED, Money.vnd(1), null, null)),
                "content.common.electricity.value.amount");
        assertPaths(withElectricity(new Charge(ChargeBasis.PROVIDER_TARIFF, null, ChargeUnit.PER_KWH, null)),
                "content.common.electricity.value.chargeUnit");
        assertPaths(withElectricity(new Charge(ChargeBasis.PROVIDER_TARIFF, null, null, "x".repeat(201))),
                "content.common.electricity.value.note");
        assertPaths(withElectricity(new Charge(ChargeBasis.PROVIDER_TARIFF, null, null, PHONE)),
                "content.common.electricity.value.note");
        assertPaths(withElectricity(new Charge(null, null, null, null)), "content.common.electricity.value.basis");
    }

    @Test
    void months_ranges() {
        assertPaths(withSpace(s -> new TermsSpace(s.spaceId(), s.monthlyRent(), s.deposit(),
                SourcedField.of(13, ChecklistSource.OBSERVED_ON_SITE), s.minimumTermMonths(), null, null)),
                "content.spaces.0.paymentPeriodMonths.value");
        assertPaths(withSpace(s -> new TermsSpace(s.spaceId(), s.monthlyRent(), s.deposit(),
                SourcedField.of(0, ChecklistSource.OBSERVED_ON_SITE), s.minimumTermMonths(), null, null)),
                "content.spaces.0.paymentPeriodMonths.value");
        assertPaths(withSpace(s -> new TermsSpace(s.spaceId(), s.monthlyRent(), s.deposit(), s.paymentPeriodMonths(),
                SourcedField.of(121, ChecklistSource.OBSERVED_ON_SITE), null, null)),
                "content.spaces.0.minimumTermMonths.value");
    }

    @Test
    void fees_atMostTenWithNames() {
        List<Fee> eleven = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            eleven.add(new Fee("Phí " + i, Money.vnd(1_000), ChargeUnit.ONE_OFF));
        }
        assertPaths(withOtherFees(eleven), "content.common.otherFees.value");
        assertPaths(withOtherFees(List.of(new Fee("x".repeat(101), Money.vnd(1), ChargeUnit.ONE_OFF))),
                "content.common.otherFees.value.0.name");
        assertPaths(withOtherFees(List.of(new Fee(PHONE, Money.vnd(1), ChargeUnit.ONE_OFF))),
                "content.common.otherFees.value.0.name");
        assertPaths(withOtherFees(List.of(new Fee("Phí", Money.vnd(1), null))),
                "content.common.otherFees.value.0.chargeUnit");
        assertPaths(withOtherFees(List.of(new Fee("Phí", null, ChargeUnit.ONE_OFF))),
                "content.common.otherFees.value.0.amount");
        assertEquals(withOtherFees(List.of()), TemplateV1Rules.checkAndNormalise(withOtherFees(List.of()), TODAY));
    }

    @Test
    void textsPetsAndOwnerFormOnTerms() {
        assertPaths(withCommon(c -> new TermsCommon(c.electricity(), c.water(), c.internet(), c.parking(),
                c.serviceFee(), c.otherFees(), c.houseRules(), SourcedField.of(new Pets(PetsPolicy.CONDITIONAL, null),
                ChecklistSource.OBSERVED_ON_SITE), c.handoverMaintenance())), "content.common.pets.value.note");
        assertPaths(withCommon(c -> new TermsCommon(c.electricity(), c.water(), c.internet(), c.parking(),
                c.serviceFee(), c.otherFees(), SourcedField.of("Nội quy", ChecklistSource.OWNER_FORM), c.pets(),
                c.handoverMaintenance())), "content.common.houseRules.source");
        assertPaths(withCommon(c -> new TermsCommon(c.electricity(), c.water(), c.internet(), c.parking(),
                c.serviceFee(), c.otherFees(), SourcedField.of(PHONE, ChecklistSource.OBSERVED_ON_SITE), c.pets(),
                c.handoverMaintenance())), "content.common.houseRules.value");
        assertPaths(withCommon(c -> new TermsCommon(c.electricity(), c.water(), c.internet(), c.parking(),
                c.serviceFee(), c.otherFees(), c.houseRules(), c.pets(), ownerSaid("x".repeat(2001), TODAY))),
                "content.common.handoverMaintenance.value");
        assertPaths(withCommon(c -> new TermsCommon(c.electricity(), c.water(), c.internet(), c.parking(),
                c.serviceFee(), c.otherFees(), c.houseRules(), c.pets(), SourcedField.notApplicable("x"))),
                "content.common.handoverMaintenance.state");
        assertPaths(withSpace(s -> new TermsSpace(s.spaceId(), s.monthlyRent(),
                SourcedField.of(new Deposit(DepositBasis.MONTHS, 1, null), ChecklistSource.OWNER_FORM),
                s.paymentPeriodMonths(), s.minimumTermMonths(), null, null)), "content.spaces.0.deposit.source");
        TermsContent formRent = withRent(SourcedField.of(RentMoney.monthly(15_000_000), ChecklistSource.OWNER_FORM));
        assertEquals(formRent, TemplateV1Rules.checkAndNormalise(formRent, TODAY));
    }

    /** spec 4.6: an override member is null (the common charge) or a charge term in VALUE; space fees likewise. */
    @Test
    void overridesAndSpaceFees_nullOrValue() {
        assertPaths(withSpace(s -> new TermsSpace(s.spaceId(), s.monthlyRent(), s.deposit(), s.paymentPeriodMonths(),
                s.minimumTermMonths(), new ChargeOverrides(SourcedField.unknown(), null, null, null, null), null)),
                "content.spaces.0.chargeOverrides.electricity.state");
        assertPaths(withSpace(s -> new TermsSpace(s.spaceId(), s.monthlyRent(), s.deposit(), s.paymentPeriodMonths(),
                s.minimumTermMonths(), ChargeOverrides.none(), SourcedField.unknown())),
                "content.spaces.0.spaceFees.state");
        assertPaths(withSpace(s -> new TermsSpace(s.spaceId(), s.monthlyRent(), s.deposit(), s.paymentPeriodMonths(),
                s.minimumTermMonths(), new ChargeOverrides(null, null, charge(-5, ChargeUnit.PER_MONTH), null, null),
                null)), "content.spaces.0.chargeOverrides.internet.value.amount.amount");
    }

    @Test
    void canonical_partsMissingSpace_hasFiveNullOverrides() {
        TermsContent sent = terms(SPACE_2);

        TermsContent parts = (TermsContent) TemplateV1Rules.canonical(sent,
                identificationParts(observedSpace(SPACE_1, "P101"), observedSpace(SPACE_2, "P102")));
        TermsContent whole = (TermsContent) TemplateV1Rules.canonical(terms(), identificationWhole());

        assertEquals(new TermsSpace(SPACE_1, SourcedField.unknown(), SourcedField.unknown(), SourcedField.unknown(),
                SourcedField.unknown(), ChargeOverrides.none(), null), parts.spaces().get(0));
        assertEquals(sent.spaces().get(0), parts.spaces().get(1));
        assertEquals(new TermsSpace(SPACE_1, SourcedField.unknown(), SourcedField.unknown(), SourcedField.unknown(),
                SourcedField.unknown(), null, null), whole.spaces().get(0));
    }

    // ==================== helpers ====================

    private static OtherItem other(String id, String label) {
        return new OtherItem(id, label, null, YesNo.YES, ChecklistSource.OBSERVED_ON_SITE, null, null, null);
    }

    private static TermsContent withCommon(UnaryOperator<TermsCommon> change) {
        return new TermsContent(change.apply(termsCommon()), terms(SPACE_1).spaces());
    }

    private static TermsContent withElectricity(Charge electricity) {
        return withCommon(c -> new TermsCommon(SourcedField.of(electricity, ChecklistSource.OBSERVED_ON_SITE),
                c.water(), c.internet(), c.parking(), c.serviceFee(), c.otherFees(), c.houseRules(), c.pets(),
                c.handoverMaintenance()));
    }

    private static TermsContent withOtherFees(List<Fee> fees) {
        return withCommon(c -> new TermsCommon(c.electricity(), c.water(), c.internet(), c.parking(), c.serviceFee(),
                SourcedField.of(fees, ChecklistSource.OBSERVED_ON_SITE), c.houseRules(), c.pets(),
                c.handoverMaintenance()));
    }

    private static TermsContent withSpace(UnaryOperator<TermsSpace> change) {
        return new TermsContent(termsCommon(), List.of(change.apply(termsSpace(SPACE_1, null, null))));
    }

    private static TermsContent withRent(SourcedField<RentMoney> rent) {
        return withSpace(s -> new TermsSpace(s.spaceId(), rent, s.deposit(), s.paymentPeriodMonths(),
                s.minimumTermMonths(), null, null));
    }

    private static TermsContent withDeposit(Deposit deposit) {
        return withSpace(s -> new TermsSpace(s.spaceId(), s.monthlyRent(),
                SourcedField.of(deposit, ChecklistSource.OBSERVED_ON_SITE), s.paymentPeriodMonths(),
                s.minimumTermMonths(), null, null));
    }
}
