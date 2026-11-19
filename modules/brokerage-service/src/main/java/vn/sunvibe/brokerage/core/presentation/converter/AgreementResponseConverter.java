package vn.sunvibe.brokerage.core.presentation.converter;

import org.springframework.stereotype.Component;
import vn.sunvibe.brokerage.core.application.query.AgreementDetailView;
import vn.sunvibe.brokerage.core.application.query.AgreementHistoryView;
import vn.sunvibe.brokerage.core.application.query.AgreementPageView;
import vn.sunvibe.brokerage.core.application.query.OwnerAgreementsView;
import vn.sunvibe.brokerage.core.application.query.SurveyTermsView;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightView;
import vn.sunvibe.brokerage.core.application.usecase.AgreementCreateResult;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationDecision;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.EarlyLeaseEndRefund;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeFormula;
import vn.sunvibe.brokerage.core.domain.model.agreement.MandatoryFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.SignedDocument;
import vn.sunvibe.brokerage.core.domain.model.agreement.TermsDeposit;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionTerms;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementAdvertisingPendingVersionResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementAdvertisingPhotoConsentResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementAdvertisingResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementCreatedResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementDepositResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementDetailResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementEarlyLeaseEndResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementFeeFormulaResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementFeePolicyResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementFeeResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementFeeTierResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementHistoryEntryResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementHistoryResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementListAdvertisingResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementListItemResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementOwnerItemResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementOwnerListResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementPageResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementStatementResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementRentResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementSurveyTermsResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementSuspensionResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementTerminationResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementTermSourceResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementTermsResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementVersionActivationResponse;
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
        AgreementDetailView.ActivationStatementView statement = view.activationStatementOrNull();
        return new BrokerageAgreementDetailResponse(view.agreementId(), view.status().name(),
                view.transactionType().name(), view.unitId(), view.unitCodeOrNull(), view.basisLeadId(), view.createdAt(),
                view.versions().stream().map(AgreementResponseConverter::version).toList(),
                view.dataVersion(), view.viewerIsAssignedSale(), advertising(view.advertising()),
                view.viewerMayDecideActivation(),
                statement == null ? null : new BrokerageAgreementStatementResponse(statement.version(), statement.text()),
                view.endedAtOrNull(), termination(view.terminationOrNull()),
                view.suspensions().stream().map(AgreementResponseConverter::suspension).toList(),
                view.moreSuspensions(), view.viewerStopActions().stream().map(Enum::name).toList());
    }

    /** E05-S8 spec S8-D11: the termination record as codes and plain values; staff only (BR-17). */
    private static BrokerageAgreementTerminationResponse termination(
            AgreementDetailView.TerminationView terminationOrNull) {
        if (terminationOrNull == null) {
            return null;
        }
        return new BrokerageAgreementTerminationResponse(terminationOrNull.type().name(),
                terminationOrNull.reportedOnOrNull(), nameOrNull(terminationOrNull.channelOrNull()),
                nameOrNull(terminationOrNull.introducedOrNull()), terminationOrNull.noteOrNull(),
                terminationOrNull.terminatedAt());
    }

    /** E05-S8 spec S8-D11: one suspension record, no user id. */
    private static BrokerageAgreementSuspensionResponse suspension(AgreementDetailView.SuspensionView record) {
        return new BrokerageAgreementSuspensionResponse(record.reasonType().name(), record.noteOrNull(),
                record.suspendedAt(), record.releasedAtOrNull(), record.releaseReasonOrNull());
    }

    private static String nameOrNull(Enum<?> codeOrNull) {
        return codeOrNull == null ? null : codeOrNull.name();
    }

    /** brokerage-agreement BR-12 (E05-S6 spec S6-D7, S6-D8): the answer as codes and plain values. */
    private static BrokerageAgreementAdvertisingResponse advertising(AdvertisingRightView answer) {
        return new BrokerageAgreementAdvertisingResponse(answer.allowed(), answer.reasonGroups(), answer.endDateOrNull(),
                answer.endsAtOrNull(),
                new BrokerageAgreementAdvertisingPhotoConsentResponse(answer.interiorPhotos(), answer.exteriorPhotos()),
                answer.termsInEffectOrNull() == null ? null : terms(answer.termsInEffectOrNull()),
                answer.pendingStartDateOrNull() == null ? null : new BrokerageAgreementAdvertisingPendingVersionResponse(
                        answer.pendingStartDateOrNull(), answer.pendingStartsAtOrNull(),
                        terms(answer.pendingTermsOrNull())));
    }

    private static BrokerageAgreementTermsResponse terms(AdvertisingRightView.Terms terms) {
        AdvertisingRightView.Deposit deposit = terms.depositOrNull();
        return new BrokerageAgreementTermsResponse(
                terms.monthlyRentOrNull() == null ? null
                        : new BrokerageAgreementRentResponse(terms.monthlyRentOrNull(), VND, "MONTH"),
                deposit == null ? null : new BrokerageAgreementDepositResponse(deposit.basis(), deposit.monthsOrNull(),
                        deposit.amountOrNull(), deposit.amountOrNull() == null ? null : VND),
                terms.minimumTermMonthsOrNull(), terms.paymentPeriodMonthsOrNull(),
                terms.mandatoryFees().stream().map(fee -> new BrokerageAgreementFeeResponse(fee.name(), fee.basis(),
                        fee.amountOrNull(), fee.amountOrNull() == null ? null : VND, fee.chargeUnitOrNull())).toList());
    }

    /** E05-S6 spec S6-D21: a manager's decision without its user id; null when there is none or for the owner. */
    private static BrokerageAgreementVersionActivationResponse activation(ActivationDecision decisionOrNull) {
        if (decisionOrNull == null) {
            return null;
        }
        SignedDocument document = decisionOrNull.signedDocumentOrNull();
        return new BrokerageAgreementVersionActivationResponse(decisionOrNull.type().name(), decisionOrNull.decidedAt(),
                document == null ? null : document.number(), document == null ? null : document.signedOn(),
                document == null ? null : document.sunvibeSignatoryName(),
                document == null ? null : document.sunvibeSignatoryTitle(),
                decisionOrNull.refusalTypeOrNull() == null ? null : decisionOrNull.refusalTypeOrNull().name(),
                decisionOrNull.refusalNoteOrNull());
    }

    /** The owner's read (E05-S5 spec S5-D1, 4): the same version schema as the staff detail (spec S5-D13). */
    public BrokerageAgreementOwnerListResponse toOwnerList(OwnerAgreementsView view) {
        return new BrokerageAgreementOwnerListResponse(view.items().stream()
                .map(item -> new BrokerageAgreementOwnerItemResponse(item.agreementId(), item.status().name(),
                        item.transactionType().name(), item.unitCodeOrNull(), item.unitLocationOrNull(),
                        item.streetOrNull(), item.wardNameOrNull(),
                        item.versions().stream().map(AgreementResponseConverter::version).toList(),
                        item.statementOrNull() == null ? null : new BrokerageAgreementStatementResponse(
                                item.statementOrNull().version(), item.statementOrNull().text())))
                .toList(), view.more());
    }

    private static BrokerageAgreementVersionResponse version(AgreementDetailView.VersionView version) {
        return new BrokerageAgreementVersionResponse(version.versionNo(), version.status().name(), version.startDate(),
                version.endDate(), version.createdAt(), version.advertisingScope().name(),
                version.photoConsent().interiorOrNull(), version.photoConsent().exterior(), terms(version.terms()),
                feePolicy(version.fee()), version.sentAtOrNull(), version.answerByOrNull(),
                version.ownerAnsweredAtOrNull(), version.ownerRejectionReasonOrNull(), version.activateByOrNull(),
                activation(version.activationOrNull()));
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

    /**
     * The staff list (E05-S7 spec S7-D5, section 4): each row with its advertising flag, groups and dates only; the
     * start date is that of the activated version not in effect yet, as the detail's pending version gives it.
     */
    public BrokerageAgreementPageResponse toAgreementPage(AgreementPageView view) {
        return new BrokerageAgreementPageResponse(view.items().stream()
                .map(item -> new BrokerageAgreementListItemResponse(item.agreementId(), item.unitCodeOrNull(),
                        item.transactionType().name(), item.status().name(), item.latestVersionNo(),
                        item.latestVersionStatus().name(), item.createdAt(),
                        new BrokerageAgreementListAdvertisingResponse(item.advertisingAllowed(), item.reasonGroups(),
                                item.endDateOrNull(), item.endsAtOrNull(), item.startDateOrNull())))
                .toList(), view.page(), view.size(), view.totalElements());
    }

    /** The history (E05-S7 spec S7-D7, section 4): each event's code, version, actor type and staff name, and time. */
    public BrokerageAgreementHistoryResponse toHistory(AgreementHistoryView view) {
        return new BrokerageAgreementHistoryResponse(view.entries().stream()
                .map(entry -> new BrokerageAgreementHistoryEntryResponse(entry.type(), entry.versionNoOrNull(),
                        entry.actorType().name(), entry.actorNameOrNull(), entry.occurredAt()))
                .toList(), view.page(), view.size(), view.totalElements());
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
