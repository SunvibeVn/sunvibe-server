package vn.sunvibe.brokerage.core.presentation.converter;

import org.springframework.stereotype.Component;
import vn.sunvibe.brokerage.core.application.command.AgreementConfirmCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementCreateCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementDraftCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementOpenDraftCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementRejectCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementSendCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementVersionInput;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException;
import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;
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
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionTerms;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementConfirmationRequest;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementCreateRequest;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementDepositInput;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementDraftRequest;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementDraftSubmissionRequest;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementFeeFormulaInput;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementFeeInput;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementFeePolicyInput;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementFeeTierInput;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementNewDraftRequest;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementRejectionRequest;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementTermsInput;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The agreement requests to their commands; a pure function of its arguments. The draft's content is built as the
 * domain values, which hold its rules; a refused value names its field as a request path (E05-S3 plan E05S3-P4).
 */
@Component
public class AgreementRequestConverter {

    private static final String TERMS = "terms.";
    private static final String FEE_POLICY = "feePolicy.";

    public AgreementCreateCommand toCommand(BrokerageAgreementCreateRequest request) {
        return new AgreementCreateCommand(request.leadId(), request.unitId(), request.transactionType(),
                request.startDate(), request.endDate());
    }

    /**
     * The draft command of the request body; the bean constraints have already held.
     *
     * @throws AgreementRefusedException a fee type its place does not take, with the place's allowed values
     * @throws AgreementContentException a value outside its rule, with its path in the request
     */
    public AgreementDraftCommand toDraftCommand(String agreementId, BrokerageAgreementDraftRequest request) {
        BrokerageAgreementFeePolicyInput feeInput = request.feePolicy();
        checkFeeTypes(feeInput);
        PhotoConsent consent = new PhotoConsent(request.photoConsent(), request.exteriorPhotoConsent());
        BrokerageAgreementTermsInput input = request.terms();
        TermsDeposit deposit = within(TERMS, () -> deposit(input.deposit()));
        List<MandatoryFee> fees = fees(input.mandatoryFees());
        VersionTerms terms = within(TERMS, () -> new VersionTerms(
                input.monthlyRent() == null ? null : input.monthlyRent().amount(), deposit, input.minimumTermMonths(),
                input.paymentPeriodMonths(), fees));
        return new AgreementDraftCommand(agreementId, request.expectedDataVersion(), request.transactionType(),
                request.startDate(), request.endDate(), consent, terms, fee(feeInput));
    }

    /** The send command of the request body (E05-S4 spec S4-D2); the bean constraints have already held. */
    public AgreementSendCommand toSendCommand(String agreementId, BrokerageAgreementDraftSubmissionRequest request) {
        return new AgreementSendCommand(agreementId, request.expectedDataVersion());
    }

    /**
     * The owner's confirmation (E05-S5 spec S5-D5): the items in the order given; the bean constraints have already
     * held.
     *
     * @throws AgreementRefusedException an agreement named twice, on {@code versions}, before any read
     */
    public AgreementConfirmCommand toConfirmCommand(BrokerageAgreementConfirmationRequest request) {
        List<AgreementVersionInput> versions = request.versions().stream()
                .map(item -> new AgreementVersionInput(item.agreementId(), item.versionNo()))
                .toList();
        if (versions.stream().map(AgreementVersionInput::agreementId).distinct().count() != versions.size()) {
            throw AgreementRefusedException.invalid("versions", null);
        }
        return new AgreementConfirmCommand(request.currentPassword(), request.statementVersion(), versions);
    }

    /** The new draft command (E05-S5 spec S5-D10); the bean constraints have already held. */
    public AgreementOpenDraftCommand toOpenDraftCommand(String agreementId, BrokerageAgreementNewDraftRequest request) {
        return new AgreementOpenDraftCommand(agreementId, request.expectedDataVersion());
    }

    /** The owner's rejection (E05-S5 spec S5-D7); the reason as written, the use case trims and checks it. */
    public AgreementRejectCommand toRejectCommand(String agreementId, BrokerageAgreementRejectionRequest request) {
        return new AgreementRejectCommand(agreementId, request.versionNo(), request.reason());
    }

    /**
     * brokerage-agreement BR-7 (E05-S4 spec S4-D3): each present fee type must be one its place takes, known or unknown;
     * refused with the place's values, in their order, before any fee value is built.
     */
    private static void checkFeeTypes(BrokerageAgreementFeePolicyInput inputOrNull) {
        if (inputOrNull == null) {
            return;
        }
        if (inputOrNull.formula() != null && !FeeFormulaType.of(inputOrNull.formula().type())
                .map(FeeFormulaType::forFee).orElse(false)) {
            throw AgreementRefusedException.invalid(FEE_POLICY + "formula.type", FeeFormulaType.FEE_TYPES);
        }
        if (inputOrNull.renewal() != null && !FeeFormulaType.of(inputOrNull.renewal().type())
                .map(FeeFormulaType::forRenewal).orElse(false)) {
            throw AgreementRefusedException.invalid(FEE_POLICY + "renewal.type", FeeFormulaType.RENEWAL_TYPES);
        }
        if (inputOrNull.earlyLeaseEnd() != null && EarlyLeaseEndType.of(inputOrNull.earlyLeaseEnd().type()).isEmpty()) {
            throw AgreementRefusedException.invalid(FEE_POLICY + "earlyLeaseEnd.type", EarlyLeaseEndType.NAMES);
        }
    }

    /** The fee policy of the body, each part inside its request path; none for a body without one (spec S4-D1). */
    private static BrokerageFee fee(BrokerageAgreementFeePolicyInput inputOrNull) {
        if (inputOrNull == null) {
            return BrokerageFee.NONE;
        }
        FeeFormula formula = formula(FEE_POLICY + "formula.", inputOrNull.formula());
        FeeFormula renewal = formula(FEE_POLICY + "renewal.", inputOrNull.renewal());
        EarlyLeaseEndRefund early = inputOrNull.earlyLeaseEnd() == null ? null
                : within(FEE_POLICY + "earlyLeaseEnd.", () -> new EarlyLeaseEndRefund(
                        EarlyLeaseEndType.of(inputOrNull.earlyLeaseEnd().type()).orElse(null),
                        inputOrNull.earlyLeaseEnd().refundPercent(), inputOrNull.earlyLeaseEnd().withinMonths()));
        return within(FEE_POLICY, () -> new BrokerageFee(formula, inputOrNull.dueDays(), renewal, early,
                inputOrNull.protectionDays()));
    }

    private static FeeFormula formula(String prefix, BrokerageAgreementFeeFormulaInput inputOrNull) {
        if (inputOrNull == null) {
            return null;
        }
        List<FeeTier> tiers = null;
        if (inputOrNull.tiers() != null) {
            tiers = new ArrayList<>();
            for (int index = 0; index < inputOrNull.tiers().size(); index++) {
                BrokerageAgreementFeeTierInput tier = inputOrNull.tiers().get(index);
                tiers.add(within(prefix + "tiers[" + index + "].", () -> new FeeTier(tier.fromMonths(),
                        tier.percent())));
            }
        }
        List<FeeTier> built = tiers;
        FeeFormula formula = within(prefix, () -> FeeFormula.of(FeeFormulaType.of(inputOrNull.type()).orElse(null),
                built, inputOrNull.amount(), inputOrNull.freeReason()));
        // ADR-006: an amount is whole VND and says so; a currency beside no amount stores nothing
        if (inputOrNull.amount() != null && inputOrNull.currency() == null) {
            throw new AgreementContentException(prefix + "currency");
        }
        return formula;
    }

    private static TermsDeposit deposit(BrokerageAgreementDepositInput input) {
        if (input == null) {
            return null;
        }
        TermsDeposit deposit = new TermsDeposit(DepositBasis.of(input.basis()).orElse(null), input.months(),
                input.amount());
        // ADR-006: an amount is whole VND and says so; a currency beside no amount stores nothing
        if (input.amount() != null && input.currency() == null) {
            throw new AgreementContentException("deposit.currency");
        }
        return deposit;
    }

    private static List<MandatoryFee> fees(List<BrokerageAgreementFeeInput> inputs) {
        if (inputs == null) {
            return null;
        }
        List<MandatoryFee> fees = new ArrayList<>();
        for (int index = 0; index < inputs.size(); index++) {
            BrokerageAgreementFeeInput input = inputs.get(index);
            fees.add(within(TERMS + "mandatoryFees[" + index + "].", () -> fee(input)));
        }
        return fees;
    }

    private static MandatoryFee fee(BrokerageAgreementFeeInput input) {
        MandatoryFee fee = new MandatoryFee(input.name(), FeeBasis.of(input.basis()).orElse(null), input.amount(),
                ChargeUnit.of(input.chargeUnit()).orElse(null));
        if (input.amount() != null && input.currency() == null) {
            throw new AgreementContentException("currency");
        }
        return fee;
    }

    /** The value, its refusal's field path placed inside {@code prefix}. */
    private static <T> T within(String prefix, Supplier<T> value) {
        try {
            return value.get();
        } catch (AgreementContentException refused) {
            throw refused.within(prefix);
        }
    }
}
