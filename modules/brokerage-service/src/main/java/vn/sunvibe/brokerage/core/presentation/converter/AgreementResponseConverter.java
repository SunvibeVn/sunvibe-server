package vn.sunvibe.brokerage.core.presentation.converter;

import org.springframework.stereotype.Component;
import vn.sunvibe.brokerage.core.application.query.AgreementDetailView;
import vn.sunvibe.brokerage.core.application.query.SurveyTermsView;
import vn.sunvibe.brokerage.core.application.usecase.AgreementCreateResult;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.EarlyLeaseEndRefund;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeFormula;
import vn.sunvibe.brokerage.core.domain.model.agreement.MandatoryFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.TermsDeposit;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionTerms;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementCreatedResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementDepositResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementDetailResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementEarlyLeaseEndResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementFeeFormulaResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementFeePolicyResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementFeeResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementFeeTierResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementRentResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementSurveyTermsResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementTermSourceResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementTermsResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementVersionResponse;

/** The agreement results and views to their responses; a pure function of its argument. */
@Component
public class AgreementResponseConverter {

    /** ADR-006: every amount is whole VND. */
    private static final String VND = "VND";

    public BrokerageAgreementCreatedResponse toCreated(AgreementCreateResult result) {
        return new BrokerageAgreementCreatedResponse(result.agreementId());
    }

    public BrokerageAgreementDetailResponse toDetail(AgreementDetailView view) {
        return new BrokerageAgreementDetailResponse(view.agreementId(), view.status().name(),
                view.transactionType().name(), view.unitId(), view.unitCodeOrNull(), view.basisLeadId(), view.createdAt(),
                view.versions().stream()
                        .map(version -> new BrokerageAgreementVersionResponse(version.versionNo(), version.status().name(),
                                version.startDate(), version.endDate(), version.createdAt(),
                                version.advertisingScope().name(), version.photoConsent().interiorOrNull(),
                                version.photoConsent().exterior(), terms(version.terms()), feePolicy(version.fee()),
                                version.sentAtOrNull()))
                        .toList(),
                view.dataVersion(), view.viewerIsAssignedSale());
    }

    /** brokerage-agreement BR-7: the fixed payer and moment, and every part, null when not entered (spec S4-D10). */
    private static BrokerageAgreementFeePolicyResponse feePolicy(BrokerageFee fee) {
        EarlyLeaseEndRefund early = fee.earlyLeaseEndOrNull();
        return new BrokerageAgreementFeePolicyResponse(fee.payer().name(), fee.chargedWhen().name(),
                formula(fee.formulaOrNull()), fee.dueDaysOrNull(), formula(fee.renewalOrNull()),
                early == null ? null : new BrokerageAgreementEarlyLeaseEndResponse(early.type().name(),
                        early.refundPercentOrNull(), early.withinMonthsOrNull()),
                fee.protectionDaysOrNull());
    }

    private static BrokerageAgreementFeeFormulaResponse formula(FeeFormula formulaOrNull) {
        if (formulaOrNull == null) {
            return null;
        }
        return new BrokerageAgreementFeeFormulaResponse(formulaOrNull.type().name(),
                formulaOrNull.tiers().stream()
                        .map(tier -> new BrokerageAgreementFeeTierResponse(tier.fromMonths(), tier.percent()))
                        .toList(),
                formulaOrNull.amountOrNull(), formulaOrNull.amountOrNull() == null ? null : VND,
                formulaOrNull.freeReasonOrNull());
    }

    public BrokerageAgreementSurveyTermsResponse toSurveyTerms(SurveyTermsView view) {
        return new BrokerageAgreementSurveyTermsResponse(terms(view.terms()), view.sources().stream()
                .map(source -> new BrokerageAgreementTermSourceResponse(source.field(), source.sourceOrNull(),
                        source.date()))
                .toList());
    }

    private static BrokerageAgreementTermsResponse terms(VersionTerms terms) {
        TermsDeposit deposit = terms.depositOrNull();
        return new BrokerageAgreementTermsResponse(
                terms.monthlyRentOrNull() == null ? null
                        : new BrokerageAgreementRentResponse(terms.monthlyRentOrNull(), VND, "MONTH"),
                deposit == null ? null : new BrokerageAgreementDepositResponse(deposit.basis().name(),
                        deposit.monthsOrNull(), deposit.amountOrNull(), deposit.amountOrNull() == null ? null : VND),
                terms.minimumTermMonthsOrNull(), terms.paymentPeriodMonthsOrNull(),
                terms.mandatoryFeesOrNull() == null ? null
                        : terms.mandatoryFeesOrNull().stream().map(AgreementResponseConverter::fee).toList());
    }

    private static BrokerageAgreementFeeResponse fee(MandatoryFee fee) {
        return new BrokerageAgreementFeeResponse(fee.name(), fee.basis().name(), fee.amountOrNull(),
                fee.amountOrNull() == null ? null : VND,
                fee.chargeUnitOrNull() == null ? null : fee.chargeUnitOrNull().name());
    }
}
