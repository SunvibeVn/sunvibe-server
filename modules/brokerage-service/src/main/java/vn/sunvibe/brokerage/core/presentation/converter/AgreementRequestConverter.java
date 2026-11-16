package vn.sunvibe.brokerage.core.presentation.converter;

import org.springframework.stereotype.Component;
import vn.sunvibe.brokerage.core.application.command.AgreementCreateCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementDraftCommand;
import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.ChargeUnit;
import vn.sunvibe.brokerage.core.domain.model.agreement.DepositBasis;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeBasis;
import vn.sunvibe.brokerage.core.domain.model.agreement.MandatoryFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.PhotoConsent;
import vn.sunvibe.brokerage.core.domain.model.agreement.TermsDeposit;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionTerms;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementCreateRequest;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementDepositInput;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementDraftRequest;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementFeeInput;
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

    public AgreementCreateCommand toCommand(BrokerageAgreementCreateRequest request) {
        return new AgreementCreateCommand(request.leadId(), request.unitId(), request.transactionType(),
                request.startDate(), request.endDate());
    }

    /**
     * The draft command of the request body; the bean constraints have already held.
     *
     * @throws AgreementContentException a value outside its rule, with its path in the request
     */
    public AgreementDraftCommand toDraftCommand(String agreementId, BrokerageAgreementDraftRequest request) {
        PhotoConsent consent = new PhotoConsent(request.photoConsent(), request.exteriorPhotoConsent());
        BrokerageAgreementTermsInput input = request.terms();
        TermsDeposit deposit = within(TERMS, () -> deposit(input.deposit()));
        List<MandatoryFee> fees = fees(input.mandatoryFees());
        VersionTerms terms = within(TERMS, () -> new VersionTerms(
                input.monthlyRent() == null ? null : input.monthlyRent().amount(), deposit, input.minimumTermMonths(),
                input.paymentPeriodMonths(), fees));
        return new AgreementDraftCommand(agreementId, request.expectedDataVersion(), request.transactionType(),
                request.startDate(), request.endDate(), consent, terms, BrokerageFee.NONE);
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
