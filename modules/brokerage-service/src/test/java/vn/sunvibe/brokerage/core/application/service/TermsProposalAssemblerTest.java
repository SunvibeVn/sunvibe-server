package vn.sunvibe.brokerage.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.brokerage.core.application.query.SurveyTermsView;
import vn.sunvibe.brokerage.core.application.query.SurveyTermsView.TermSource;
import vn.sunvibe.brokerage.core.application.shared.external.survey.SourceTermsFacts;
import vn.sunvibe.brokerage.core.application.shared.external.survey.SourceTermsFacts.Space;
import vn.sunvibe.brokerage.core.application.shared.external.survey.SourceTermsFacts.Term;
import vn.sunvibe.brokerage.core.domain.model.agreement.ChargeUnit;
import vn.sunvibe.brokerage.core.domain.model.agreement.DepositBasis;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeBasis;
import vn.sunvibe.brokerage.core.domain.model.agreement.MandatoryFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.TermsDeposit;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionTerms;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The survey proposal of a draft (E05-S3 spec S3-D12): survey's flattened G8 terms of the unit's space mapped to the
 * confirmed terms of brokerage-agreement BR-8, each part with its source and date; anything outside a rule left out.
 * The facts are written in survey's forms, which SurveyIntakeSourcePostgresTest pins.
 */
class TermsProposalAssemblerTest {

    private static final LocalDate VISIT = LocalDate.of(2026, 11, 9);
    private static final LocalDate STATED = LocalDate.of(2026, 11, 10);
    private static final String SEEN = "OBSERVED_ON_SITE";
    private static final List<Space> SPACES = List.of(new Space("SA", "P101"), new Space("SB", "P102"));

    @Test
    void proposal_spaceTerms_rentDepositPeriodAndTerm() {
        SourceTermsFacts facts = facts(
                money("monthlyRent", "P101", 8_000_000L, "MONTH", "OWNER_STATED", STATED),
                text("deposit", "P101", "2 months"),
                text("paymentPeriodMonths", "P101", "1"),
                text("minimumTermMonths", "P101", "12"),
                money("monthlyRent", "P102", 9_000_000L, "MONTH", SEEN, null),
                money("deposit", "P102", 3_000_000L, null, SEEN, null));

        VersionTerms p101 = TermsProposalAssembler.proposal(facts, "SA").terms();
        VersionTerms p102 = TermsProposalAssembler.proposal(facts, "SB").terms();

        assertEquals(new VersionTerms(8_000_000L, new TermsDeposit(DepositBasis.MONTHS, 2, null), 12, 1, null), p101);
        assertEquals(new VersionTerms(9_000_000L, new TermsDeposit(DepositBasis.AMOUNT, null, 3_000_000L), null, null,
                null), p102);
    }

    @Test
    void proposal_charges_overrideThenCommonBasisMapping() {
        SourceTermsFacts facts = facts(
                charge("electricity", null, "AMOUNT", 3_500L, "PER_KWH"),
                charge("water", null, "PROVIDER_TARIFF", null, null),
                charge("internet", null, "INCLUDED", null, null),
                charge("parking", null, "NOT_OFFERED", null, null),
                new Term("serviceFee", null, null, null, null, null, null, null),
                charge("electricity", "P101", "AMOUNT", 4_000L, "PER_KWH"));

        assertEquals(List.of(new MandatoryFee("Electricity", FeeBasis.AMOUNT, 4_000L, ChargeUnit.PER_KWH),
                new MandatoryFee("Water", FeeBasis.PROVIDER_TARIFF, null, null),
                new MandatoryFee("Internet", FeeBasis.INCLUDED, null, null)),
                TermsProposalAssembler.proposal(facts, "SA").terms().mandatoryFeesOrNull());
        assertEquals(new MandatoryFee("Electricity", FeeBasis.AMOUNT, 3_500L, ChargeUnit.PER_KWH),
                TermsProposalAssembler.proposal(facts, "SB").terms().mandatoryFeesOrNull().get(0));
    }

    @Test
    void proposal_fees_orderNamesAndAtMostTwenty() {
        List<Term> terms = new ArrayList<>(List.of(
                charge("serviceFee", null, "AMOUNT", 200_000L, "PER_MONTH"),
                money("spaceFees:Balcony", "P101", 50_000L, "PER_MONTH", SEEN, null),
                money("otherFees:Cleaning", null, 100_000L, "PER_MONTH", SEEN, null),
                charge("parking", null, "AMOUNT", 150_000L, "PER_VEHICLE_PER_MONTH"),
                money("spaceFees:Garden", "P102", 70_000L, "PER_MONTH", SEEN, null),
                charge("internet", null, "INCLUDED", null, null),
                charge("water", null, "AMOUNT", 15_000L, "PER_M3"),
                charge("electricity", null, "AMOUNT", 3_500L, "PER_KWH")));

        List<MandatoryFee> fees = TermsProposalAssembler.proposal(facts(terms.toArray(Term[]::new)), "SA").terms()
                .mandatoryFeesOrNull();

        assertEquals(List.of("Electricity", "Water", "Internet", "Parking", "Service fee", "Cleaning", "Balcony"),
                fees.stream().map(MandatoryFee::name).toList());
        assertEquals(new MandatoryFee("Service fee", FeeBasis.AMOUNT, 200_000L, ChargeUnit.PER_MONTH), fees.get(4));

        for (int i = 1; i <= 20; i++) {
            terms.add(money("otherFees:Fee " + i, null, 1_000L * i, "ONE_OFF", SEEN, null));
        }
        SurveyTermsView many = TermsProposalAssembler.proposal(facts(terms.toArray(Term[]::new)), "SA");

        assertEquals(20, many.terms().mandatoryFeesOrNull().size());
        assertEquals("Fee 14", many.terms().mandatoryFeesOrNull().get(19).name());
        assertEquals("mandatoryFees[19]", many.sources().get(19).field());
    }

    @Test
    void proposal_valuesOutsideTheRules_leftOut() {
        SourceTermsFacts first = facts(
                money("monthlyRent", "P101", 0L, "MONTH", SEEN, null),
                text("deposit", "P101", "two months"),
                text("minimumTermMonths", "P101", "0"),
                text("paymentPeriodMonths", "P101", "13"),
                charge("electricity", null, "OTHER", null, null),
                charge("water", null, "AMOUNT", 15_000L, "PER_WEEK"),
                charge("internet", null, "AMOUNT", 0L, "PER_MONTH"),
                money("otherFees:" + "a".repeat(101), null, 1_000L, "PER_MONTH", SEEN, null),
                money("otherFees:Cleaning", null, 100_000L, "PER_MONTH", SEEN, null),
                money("otherFees:Laundry", null, 0L, "PER_MONTH", SEEN, null),
                money("otherFees:Gym", null, 50_000L, "PER_WEEK", SEEN, null));
        SourceTermsFacts second = facts(
                new Term("monthlyRent", "P101", 8_000_000L, "USD", "MONTH", null, SEEN, null),
                text("deposit", "P101", "13 months"),
                text("minimumTermMonths", "P101", "abc"),
                text("paymentPeriodMonths", "P101", "12"));
        SourceTermsFacts third = facts(
                money("monthlyRent", "P101", 8_000_000L, "WEEK", SEEN, null),
                new Term("deposit", "P101", 2_000_000L, "USD", null, null, SEEN, null),
                text("minimumTermMonths", "P101", "120"));

        assertEquals(new VersionTerms(null, null, null, null, List.of(
                new MandatoryFee("Cleaning", FeeBasis.AMOUNT, 100_000L, ChargeUnit.PER_MONTH))),
                TermsProposalAssembler.proposal(first, "SA").terms());
        assertEquals(new VersionTerms(null, null, null, 12, null), TermsProposalAssembler.proposal(second, "SA").terms());
        assertEquals(new VersionTerms(null, null, 120, null, null), TermsProposalAssembler.proposal(third, "SA").terms());
    }

    @Test
    void proposal_sourcesAndDates_termDateElseVisitDate() {
        SourceTermsFacts facts = facts(
                money("monthlyRent", "P101", 8_000_000L, "MONTH", "OWNER_STATED", STATED),
                new Term("deposit", "P101", null, null, null, "1 months", "DOCUMENT", null),
                new Term("paymentPeriodMonths", "P101", null, null, null, "3", "OWNER_FORM", null),
                charge("water", null, "PROVIDER_TARIFF", null, null),
                new Term("internet", null, null, null, null, "INCLUDED", null, null));

        SurveyTermsView view = TermsProposalAssembler.proposal(facts, "SA");

        assertEquals(List.of(new TermSource("monthlyRent", "OWNER_STATED", STATED),
                new TermSource("deposit", null, VISIT),
                new TermSource("paymentPeriodMonths", "OWNER_FORM", VISIT),
                new TermSource("mandatoryFees[0]", SEEN, VISIT),
                new TermSource("mandatoryFees[1]", null, VISIT)), view.sources());
    }

    @Test
    void proposal_otherSpaceOnly_empty() {
        SourceTermsFacts facts = facts(money("monthlyRent", "P102", 9_000_000L, "MONTH", SEEN, null),
                money("spaceFees:Garden", "P102", 70_000L, "PER_MONTH", SEEN, null));

        assertEquals(SurveyTermsView.EMPTY, TermsProposalAssembler.proposal(facts, "SA"));
    }

    @Test
    void proposal_unknownSpace_empty() {
        SourceTermsFacts facts = facts(money("monthlyRent", "P101", 8_000_000L, "MONTH", SEEN, null),
                charge("water", null, "PROVIDER_TARIFF", null, null));

        SurveyTermsView view = TermsProposalAssembler.proposal(facts, "SZ");

        assertEquals(SurveyTermsView.EMPTY, view);
        assertNull(view.terms().mandatoryFeesOrNull());
    }

    private static SourceTermsFacts facts(Term... terms) {
        return new SourceTermsFacts("01REV", VISIT, SPACES, List.of(terms));
    }

    private static Term money(String name, String code, Long amount, String unit, String source, LocalDate date) {
        return new Term(name, code, amount, "VND", unit, null, source, date);
    }

    private static Term text(String name, String code, String text) {
        return new Term(name, code, null, null, null, text, SEEN, null);
    }

    private static Term charge(String name, String code, String basis, Long amount, String unit) {
        return new Term(name, code, amount, amount == null ? null : "VND", unit, basis, SEEN, null);
    }
}
