package vn.sunvibe.brokerage.core.domain.policy.agreement;

import org.junit.jupiter.api.Test;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementPeriod;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementVersion;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.ChargeUnit;
import vn.sunvibe.brokerage.core.domain.model.agreement.DepositBasis;
import vn.sunvibe.brokerage.core.domain.model.agreement.EarlyLeaseEndRefund;
import vn.sunvibe.brokerage.core.domain.model.agreement.EarlyLeaseEndType;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeBasis;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeFormula;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeFormulaType;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeTier;
import vn.sunvibe.brokerage.core.domain.model.agreement.MandatoryFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.PhotoConsent;
import vn.sunvibe.brokerage.core.domain.model.agreement.TermsDeposit;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionTerms;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a version needs before it is sent to the owner (brokerage-agreement BR-7, BR-8, E-13; E05-S4 spec S4-D5, SD-3):
 * every part present, in a fixed order; 0 and an empty fee list are stated values.
 */
class VersionSendRulesTest {

    private static final Instant AT = Instant.parse("2026-11-20T03:15:00Z");
    private static final AgreementPeriod PERIOD =
            new AgreementPeriod(LocalDate.parse("2027-01-01"), LocalDate.parse("2027-12-31"));
    static final PhotoConsent CONSENT = new PhotoConsent(true, false);
    static final VersionTerms TERMS = new VersionTerms(8_000_000L, new TermsDeposit(DepositBasis.MONTHS, 2, null), 12, 1,
            List.of(new MandatoryFee("Electricity", FeeBasis.AMOUNT, 3_500L, ChargeUnit.PER_KWH)));
    static final BrokerageFee FEE = new BrokerageFee(
            FeeFormula.of(FeeFormulaType.PERCENT_OF_RENT, List.of(new FeeTier(1, 50), new FeeTier(12, 100)), null, null),
            7, FeeFormula.of(FeeFormulaType.NOT_CHARGED, null, null, null),
            new EarlyLeaseEndRefund(EarlyLeaseEndType.PARTIAL_REFUND, 50, 3), 90);

    private static final List<String> ELEVEN = List.of("photoConsent", "terms.monthlyRent", "terms.deposit",
            "terms.minimumTermMonths", "terms.paymentPeriodMonths", "terms.mandatoryFees", "feePolicy.formula",
            "feePolicy.dueDays", "feePolicy.renewal", "feePolicy.earlyLeaseEnd", "feePolicy.protectionDays");

    @Test
    void missingParts_newDraft_elevenInOrder() {
        AgreementVersion created = AgreementVersion.load("V1", 1, VersionStatus.DRAFT, PERIOD, "L1", "I1", "S1", AT, AT);

        assertEquals(ELEVEN, VersionSendRules.missingParts(created));
    }

    @Test
    void missingParts_eachPartNull_exactlyThatPath() {
        List<AgreementVersion> oneMissing = List.of(
                version(new PhotoConsent(null, false), TERMS, FEE),
                version(CONSENT, new VersionTerms(null, TERMS.depositOrNull(), 12, 1, TERMS.mandatoryFeesOrNull()), FEE),
                version(CONSENT, new VersionTerms(8_000_000L, null, 12, 1, TERMS.mandatoryFeesOrNull()), FEE),
                version(CONSENT, new VersionTerms(8_000_000L, TERMS.depositOrNull(), null, 1,
                        TERMS.mandatoryFeesOrNull()), FEE),
                version(CONSENT, new VersionTerms(8_000_000L, TERMS.depositOrNull(), 12, null,
                        TERMS.mandatoryFeesOrNull()), FEE),
                version(CONSENT, new VersionTerms(8_000_000L, TERMS.depositOrNull(), 12, 1, null), FEE),
                version(CONSENT, TERMS, new BrokerageFee(null, 7, FEE.renewalOrNull(), FEE.earlyLeaseEndOrNull(), 90)),
                version(CONSENT, TERMS, new BrokerageFee(FEE.formulaOrNull(), null, FEE.renewalOrNull(),
                        FEE.earlyLeaseEndOrNull(), 90)),
                version(CONSENT, TERMS, new BrokerageFee(FEE.formulaOrNull(), 7, null, FEE.earlyLeaseEndOrNull(), 90)),
                version(CONSENT, TERMS, new BrokerageFee(FEE.formulaOrNull(), 7, FEE.renewalOrNull(), null, 90)),
                version(CONSENT, TERMS, new BrokerageFee(FEE.formulaOrNull(), 7, FEE.renewalOrNull(),
                        FEE.earlyLeaseEndOrNull(), null)));

        assertEquals(ELEVEN.size(), oneMissing.size());
        for (int index = 0; index < ELEVEN.size(); index++) {
            assertEquals(List.of(ELEVEN.get(index)), VersionSendRules.missingParts(oneMissing.get(index)),
                    ELEVEN.get(index));
        }
        assertEquals(List.of(), VersionSendRules.missingParts(version(CONSENT, TERMS, FEE)));
    }

    /** brokerage-agreement BR-8: "0 là xác nhận không cọc"; 0 days and an empty fee list are stated values too. */
    @Test
    void missingParts_zerosAndEmptyList_complete() {
        BrokerageFee zeros = new BrokerageFee(FEE.formulaOrNull(), 0, FEE.renewalOrNull(), FEE.earlyLeaseEndOrNull(), 0);
        VersionTerms noDepositMonths = new VersionTerms(8_000_000L, new TermsDeposit(DepositBasis.MONTHS, 0, null), 12, 1,
                List.of());
        VersionTerms noDepositAmount = new VersionTerms(8_000_000L, new TermsDeposit(DepositBasis.AMOUNT, null, 0L), 12, 1,
                List.of());
        BrokerageFee free = new BrokerageFee(FeeFormula.of(FeeFormulaType.FREE, null, null, "First unit"), 7,
                FEE.renewalOrNull(), new EarlyLeaseEndRefund(EarlyLeaseEndType.NO_REFUND, null, null), 90);

        assertEquals(List.of(), VersionSendRules.missingParts(version(new PhotoConsent(false, false), noDepositMonths,
                zeros)));
        assertEquals(List.of(), VersionSendRules.missingParts(version(CONSENT, noDepositAmount, zeros)));
        assertEquals(List.of(), VersionSendRules.missingParts(version(CONSENT, TERMS, free)));
    }

    private static AgreementVersion version(PhotoConsent consent, VersionTerms terms, BrokerageFee fee) {
        return AgreementVersion.load("V1", 1, VersionStatus.DRAFT, PERIOD, "L1", "I1", "S1", AT, AT, consent, terms, fee,
                null);
    }
}
