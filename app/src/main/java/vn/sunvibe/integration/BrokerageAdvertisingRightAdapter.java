package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingPair;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightUseCase;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightView;
import vn.sunvibe.brokerage.core.application.usecase.AgreementStandingView;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingAnswerFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingRightPort;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;

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

    private static AgreementStandingFacts toFacts(AgreementStandingView view) {
        return new AgreementStandingFacts(view.agreementId(), view.unitId(), view.transactionType(), view.ended(),
                view.basisLeadAssigneeIdOrNull());
    }

    private static AdvertisingAnswerFacts toFacts(AdvertisingRightView view) {
        return new AdvertisingAnswerFacts(view.unitId(), view.agreementId(), view.ended(), view.allowed(),
                view.reasonGroups());
    }
}
