package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingPair;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightUseCase;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightView;
import vn.sunvibe.brokerage.core.application.usecase.AgreementStandingView;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingAnswerFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingReviewFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingRightPort;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementTermsFacts;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Cross-module composition (listing-publish E06-S1 spec E06S1-D9, ADR-009): answers listing-service's advertising port
 * with brokerage-service's published, viewer-less {@link AdvertisingRightUseCase}, after listing checked its caller.
 * It forwards each call, the locked one to the locked read (ADR-012 decision 2), and copies the fields; the reason and
 * type codes pass as E05 sends them, unmapped. It joins the caller's transaction.
 */
@Component
@RequiredArgsConstructor
public class BrokerageAdvertisingRightAdapter implements AdvertisingRightPort {

    private final AdvertisingRightUseCase rights;

    @Override
    public List<AgreementStandingFacts> standings(Collection<String> agreementIds) {
        return rights.standings(agreementIds).stream().map(BrokerageAdvertisingRightAdapter::toFacts).toList();
    }

    @Override
    public Optional<AdvertisingAnswerFacts> answer(String unitId, String agreementId) {
        return rights.answers(List.of(new AdvertisingPair(unitId, agreementId))).stream()
                .findFirst()
                .map(BrokerageAdvertisingRightAdapter::toFacts);
    }

    @Override
    public Optional<AdvertisingAnswerFacts> answerUnderShareLock(String unitId, String agreementId) {
        return rights.answerUnderShareLock(new AdvertisingPair(unitId, agreementId))
                .map(BrokerageAdvertisingRightAdapter::toFacts);
    }

    /**
     * listing-publish E06-S2 spec E06S2-D13: E05's answer for the one pair, read as {@link #answer} reads it, and its
     * terms in effect, pending start date and pending terms copied as they are; listing applies the bounds.
     */
    @Override
    public Optional<AgreementTermsFacts> terms(String unitId, String agreementId) {
        return rights.answers(List.of(new AdvertisingPair(unitId, agreementId))).stream()
                .findFirst()
                .map(view -> new AgreementTermsFacts(view.ended(), toFacts(view.termsInEffectOrNull()),
                        view.pendingStartDateOrNull(), toFacts(view.pendingTermsOrNull())));
    }

    /**
     * listing-publish E06-S4 spec E06S4-D13: E05's answer for the one pair, read as {@link #answer} reads it, every field
     * copied as it is and the terms translated as {@link #terms} translates them; listing maps the codes.
     */
    @Override
    public Optional<AdvertisingReviewFacts> review(String unitId, String agreementId) {
        return rights.answers(List.of(new AdvertisingPair(unitId, agreementId))).stream()
                .findFirst()
                .map(view -> new AdvertisingReviewFacts(view.unitId(), view.agreementId(), view.ended(), view.allowed(),
                        view.reasonGroups(), view.endDateOrNull(), view.interiorPhotos(), view.exteriorPhotos(),
                        toFacts(view.termsInEffectOrNull()), view.pendingStartDateOrNull(),
                        toFacts(view.pendingTermsOrNull())));
    }

    private static AgreementTermsFacts.Terms toFacts(AdvertisingRightView.Terms termsOrNull) {
        if (termsOrNull == null) {
            return null;
        }
        AdvertisingRightView.Deposit deposit = termsOrNull.depositOrNull();
        return new AgreementTermsFacts.Terms(termsOrNull.monthlyRentOrNull(), deposit == null ? null : deposit.basis(),
                deposit == null ? null : deposit.monthsOrNull(), deposit == null ? null : deposit.amountOrNull(),
                termsOrNull.minimumTermMonthsOrNull(), termsOrNull.paymentPeriodMonthsOrNull(),
                termsOrNull.mandatoryFees().stream()
                        .map(fee -> new AgreementTermsFacts.Fee(fee.name(), fee.basis(), fee.amountOrNull(),
                                fee.chargeUnitOrNull()))
                        .toList());
    }

    private static AgreementStandingFacts toFacts(AgreementStandingView view) {
        return new AgreementStandingFacts(view.agreementId(), view.unitId(), view.transactionType(), view.ended(),
                view.basisLeadAssigneeIdOrNull());
    }

    private static AdvertisingAnswerFacts toFacts(AdvertisingRightView view) {
        return new AdvertisingAnswerFacts(view.unitId(), view.agreementId(), view.ended(), view.allowed(),
                view.reasonGroups());
    }
}
