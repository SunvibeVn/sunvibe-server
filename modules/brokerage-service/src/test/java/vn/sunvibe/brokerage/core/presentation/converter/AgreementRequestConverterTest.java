package vn.sunvibe.brokerage.core.presentation.converter;

import org.junit.jupiter.api.Test;
import vn.sunvibe.brokerage.core.application.command.AgreementActivateCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementConfirmCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementDraftCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementRefuseActivationCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementRejectCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementVersionInput;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException.Entry;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException.Reason;
import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationRefusalType;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.EarlyLeaseEndRefund;
import vn.sunvibe.brokerage.core.domain.model.agreement.EarlyLeaseEndType;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeFormula;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeFormulaType;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeTier;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementActivationRefusalRequest;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementActivationRequest;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementConfirmationItemInput;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementConfirmationRequest;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementDraftRequest;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementRejectionRequest;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementEarlyLeaseEndInput;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementFeeFormulaInput;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementFeePolicyInput;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementFeeTierInput;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementTermsInput;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The draft request to its command (E05-S4 spec S4-D3, 3.1, FP-2): a fee type outside its place, known or unknown, is
 * refused before any fee value is built, with the place's allowed values; the other fee rules answer on their request
 * path; a body without a fee policy stores none.
 */
class AgreementRequestConverterTest {

    private static final String AGREEMENT = "01JAGRE0000000000000000001";
    private static final String AGREEMENT_2 = "01JAGRE0000000000000000002";
    private static final BrokerageAgreementTermsInput NO_TERMS = new BrokerageAgreementTermsInput(null, null, null, null,
            null);
    private static final BrokerageAgreementFeeFormulaInput PERCENT = formula("PERCENT_OF_RENT",
            List.of(new BrokerageAgreementFeeTierInput(1, 50), new BrokerageAgreementFeeTierInput(12, 100)), null, null,
            null);
    private static final BrokerageAgreementFeeFormulaInput NOT_CHARGED = formula("NOT_CHARGED", null, null, null, null);
    private static final BrokerageAgreementEarlyLeaseEndInput PARTIAL =
            new BrokerageAgreementEarlyLeaseEndInput("PARTIAL_REFUND", 50, 3);

    private final AgreementRequestConverter converter = new AgreementRequestConverter();

    // ==================== E05-S5: the owner's confirmation and rejection (spec 3.2, 3.3, S5-D5) ====================

    @Test
    void toConfirmCommand_body_itemsInOrderGiven() {
        AgreementConfirmCommand command = converter.toConfirmCommand(new BrokerageAgreementConfirmationRequest("secret",
                "brokerage-agreement-2026-11", List.of(new BrokerageAgreementConfirmationItemInput(AGREEMENT_2, 1),
                new BrokerageAgreementConfirmationItemInput(AGREEMENT, 3))));

        assertEquals("secret", command.currentPassword());
        assertEquals("brokerage-agreement-2026-11", command.statementVersion());
        assertEquals(List.of(new AgreementVersionInput(AGREEMENT_2, 1), new AgreementVersionInput(AGREEMENT, 3)),
                command.versions());
    }

    /** Review Focus 3, spec S5-D5: an agreement named twice is refused on versions before any read. */
    @Test
    void toConfirmCommand_sameAgreementTwice_invalidVersions() {
        AgreementRefusedException refused = assertThrows(AgreementRefusedException.class,
                () -> converter.toConfirmCommand(new BrokerageAgreementConfirmationRequest("secret",
                        "brokerage-agreement-2026-11", List.of(new BrokerageAgreementConfirmationItemInput(AGREEMENT, 1),
                        new BrokerageAgreementConfirmationItemInput(AGREEMENT, 2)))));

        assertEquals(Reason.VALIDATION_FAILED, refused.reason());
        assertEquals(List.of(new Entry("versions", null)), refused.entries());
    }

    @Test
    void toRejectCommand_reasonAsGiven() {
        assertEquals(new AgreementRejectCommand(AGREEMENT, 2, "  The rent is too low.  "),
                converter.toRejectCommand(AGREEMENT, new BrokerageAgreementRejectionRequest(2, "  The rent is too low.  ")));
        assertEquals(new AgreementRejectCommand(AGREEMENT, 1, null),
                converter.toRejectCommand(AGREEMENT, new BrokerageAgreementRejectionRequest(1, null)));
    }

    // ==================== E05-S6: the activation and its refusal (spec S6-D1, S6-D4) ====================

    /** The activation's values as sent; the use case trims them and checks them with its own clock. */
    @Test
    void toActivateCommand_valuesAsSent() {
        assertEquals(new AgreementActivateCommand(AGREEMENT, 7L, 2, "brokerage-activation-2026-11", " HDMG-2026/014 ",
                        LocalDate.parse("2026-11-16"), "Nguyen Van A", "Director"),
                converter.toActivateCommand(AGREEMENT, new BrokerageAgreementActivationRequest(7L, 2,
                        "brokerage-activation-2026-11", " HDMG-2026/014 ", LocalDate.parse("2026-11-16"),
                        "Nguyen Van A", "Director")));
    }

    /** RF-1, spec S6-D4: another refusal type is refused on reasonType with the two allowed values. */
    @Test
    void toRefuseActivationCommand_unknownType_invalidWithAllowedValues() {
        for (String type : List.of("OTHER", "document_differs", "")) {
            AgreementRefusedException refused = assertThrows(AgreementRefusedException.class,
                    () -> converter.toRefuseActivationCommand(AGREEMENT,
                            new BrokerageAgreementActivationRefusalRequest(0L, 1, type, null)), type);
            assertEquals(Reason.VALIDATION_FAILED, refused.reason());
            assertEquals(List.of(new Entry("reasonType", List.of("DOCUMENT_DIFFERS", "SIGNER_NOT_OWNER"))),
                    refused.entries());
        }
        assertEquals(new AgreementRefuseActivationCommand(AGREEMENT, 3L, 1, ActivationRefusalType.SIGNER_NOT_OWNER,
                        "  Not the owner.  "),
                converter.toRefuseActivationCommand(AGREEMENT, new BrokerageAgreementActivationRefusalRequest(3L, 1,
                        "SIGNER_NOT_OWNER", "  Not the owner.  ")));
    }

    @Test
    void toDraftCommand_typeOutsideItsPlace_invalidWithThePlaceList() {
        List<String> renewalTypes = List.of("NOT_CHARGED", "PERCENT_OF_RENT", "FIXED_AMOUNT");
        List<String> feeTypes = List.of("PERCENT_OF_RENT", "FIXED_AMOUNT", "FREE");
        BrokerageAgreementFeeFormulaInput free = formula("FREE", null, null, null, "First unit");
        BrokerageAgreementFeeFormulaInput monthly = formula("MONTHLY", null, null, null, null);

        assertRefused(new Entry("feePolicy.renewal.type", renewalTypes), policy(PERCENT, 7, free, PARTIAL, 90));
        assertRefused(new Entry("feePolicy.renewal.type", renewalTypes), policy(PERCENT, 7, monthly, PARTIAL, 90));
        assertRefused(new Entry("feePolicy.formula.type", feeTypes), policy(NOT_CHARGED, 7, NOT_CHARGED, PARTIAL, 90));
        assertRefused(new Entry("feePolicy.formula.type", feeTypes), policy(monthly, 7, NOT_CHARGED, PARTIAL, 90));
        assertRefused(new Entry("feePolicy.earlyLeaseEnd.type", List.of("NO_REFUND", "PARTIAL_REFUND")),
                policy(PERCENT, 7, NOT_CHARGED, new BrokerageAgreementEarlyLeaseEndInput("FULL_REFUND", null, null), 90));
        // the place check comes before every fee value: a tier out of order beside a renewal type out of place
        assertRefused(new Entry("feePolicy.renewal.type", renewalTypes), policy(formula("PERCENT_OF_RENT",
                List.of(new BrokerageAgreementFeeTierInput(2, 50)), null, null, null), 7, free, PARTIAL, 90));
    }

    @Test
    void toDraftCommand_absentPart_notChecked() {
        AgreementDraftCommand command = converter.toDraftCommand(AGREEMENT, request(policy(null, 7, null, null, null)));

        assertEquals(new BrokerageFee(null, 7, null, null, null), command.fee());
    }

    @Test
    void toDraftCommand_noFeePolicy_none() {
        assertEquals(BrokerageFee.NONE, converter.toDraftCommand(AGREEMENT, request(null)).fee());
        assertEquals(BrokerageFee.NONE,
                converter.toDraftCommand(AGREEMENT, request(policy(null, null, null, null, null))).fee());
    }

    @Test
    void toDraftCommand_completePolicy_built() {
        AgreementDraftCommand command = converter.toDraftCommand(AGREEMENT, request(policy(PERCENT, 7,
                formula("FIXED_AMOUNT", List.of(), 1_000_000L, "VND", null), PARTIAL, 90)));

        assertEquals(new BrokerageFee(
                FeeFormula.of(FeeFormulaType.PERCENT_OF_RENT, List.of(new FeeTier(1, 50), new FeeTier(12, 100)), null,
                        null), 7, FeeFormula.of(FeeFormulaType.FIXED_AMOUNT, null, 1_000_000L, null),
                new EarlyLeaseEndRefund(EarlyLeaseEndType.PARTIAL_REFUND, 50, 3), 90), command.fee());
    }

    @Test
    void toDraftCommand_amountWithoutCurrency_refusedOnCurrency() {
        assertEquals("feePolicy.formula.currency", contentRefused(policy(
                formula("FIXED_AMOUNT", null, 2_000_000L, null, null), null, null, null, null)));
        assertEquals("feePolicy.renewal.currency", contentRefused(policy(null, null,
                formula("FIXED_AMOUNT", null, 2_000_000L, null, null), null, null)));
        // a currency beside no amount stores nothing (E05-S4 plan E05S4-P2)
        assertEquals(new BrokerageFee(null, null, FeeFormula.of(FeeFormulaType.NOT_CHARGED, null, null, null), null,
                null), converter.toDraftCommand(AGREEMENT, request(policy(null, null,
                formula("NOT_CHARGED", null, null, "VND", null), null, null))).fee());
    }

    @Test
    void toDraftCommand_feeValueRules_refusedOnTheirRequestPath() {
        assertEquals("feePolicy.formula.tiers[0].fromMonths", contentRefused(policy(formula("PERCENT_OF_RENT",
                List.of(new BrokerageAgreementFeeTierInput(2, 50)), null, null, null), null, null, null, null)));
        assertEquals("feePolicy.renewal.tiers[2].fromMonths", contentRefused(policy(null, null, formula(
                "PERCENT_OF_RENT", List.of(new BrokerageAgreementFeeTierInput(1, 50),
                        new BrokerageAgreementFeeTierInput(12, 60), new BrokerageAgreementFeeTierInput(12, 70)), null,
                null, null), null, null)));
        assertEquals("feePolicy.formula.tiers[1].percent", contentRefused(policy(formula("PERCENT_OF_RENT",
                List.of(new BrokerageAgreementFeeTierInput(1, 50), new BrokerageAgreementFeeTierInput(12, 301)), null,
                null, null), null, null, null, null)));
        assertEquals("feePolicy.formula.freeReason", contentRefused(policy(formula("FREE", null, null, null, "   "),
                null, null, null, null)));
        assertEquals("feePolicy.earlyLeaseEnd.withinMonths", contentRefused(policy(null, null, null,
                new BrokerageAgreementEarlyLeaseEndInput("PARTIAL_REFUND", 50, null), null)));
        assertEquals("feePolicy.dueDays", contentRefused(policy(null, 31, null, null, null)));
    }

    private void assertRefused(Entry expected, BrokerageAgreementFeePolicyInput policy) {
        AgreementRefusedException refused = assertThrows(AgreementRefusedException.class,
                () -> converter.toDraftCommand(AGREEMENT, request(policy)));
        assertEquals(Reason.VALIDATION_FAILED, refused.reason());
        assertEquals(List.of(expected), refused.entries());
    }

    private String contentRefused(BrokerageAgreementFeePolicyInput policy) {
        return assertThrows(AgreementContentException.class,
                () -> converter.toDraftCommand(AGREEMENT, request(policy))).field();
    }

    private static BrokerageAgreementFeeFormulaInput formula(String type, List<BrokerageAgreementFeeTierInput> tiers,
                                                             Long amount, String currency, String freeReason) {
        return new BrokerageAgreementFeeFormulaInput(type, tiers, amount, currency, freeReason);
    }

    private static BrokerageAgreementFeePolicyInput policy(BrokerageAgreementFeeFormulaInput formula, Integer dueDays,
                                                           BrokerageAgreementFeeFormulaInput renewal,
                                                           BrokerageAgreementEarlyLeaseEndInput earlyLeaseEnd,
                                                           Integer protectionDays) {
        return new BrokerageAgreementFeePolicyInput(formula, dueDays, renewal, earlyLeaseEnd, protectionDays);
    }

    private static BrokerageAgreementDraftRequest request(BrokerageAgreementFeePolicyInput feePolicyOrNull) {
        return new BrokerageAgreementDraftRequest(0L, "LONG_TERM_RENT", LocalDate.parse("2027-01-01"),
                LocalDate.parse("2027-12-31"), null, false, NO_TERMS, feePolicyOrNull);
    }
}
