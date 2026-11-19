package vn.sunvibe.listing.core.testsupport;

import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingAnswerFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingRightPort;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * E05's advertising read for the service tests: standings and answers set per agreement, each call recorded by method.
 * An answer is given only for the pair of its own unit, as E05 leaves out a pair on another unit.
 */
public class StubAdvertisingRightPort implements AdvertisingRightPort {

    private final Map<String, AgreementStandingFacts> standings = new HashMap<>();
    private final Map<String, AdvertisingAnswerFacts> answers = new HashMap<>();
    private final List<String> calls = new ArrayList<>();

    public void standing(AgreementStandingFacts standing) {
        standings.put(standing.agreementId(), standing);
    }

    public void answer(AdvertisingAnswerFacts answer) {
        answers.put(answer.agreementId(), answer);
    }

    public List<String> calls() {
        return List.copyOf(calls);
    }

    @Override
    public List<AgreementStandingFacts> standings(Collection<String> agreementIds) {
        calls.add("standings " + agreementIds);
        return agreementIds.stream().map(standings::get).filter(Objects::nonNull).toList();
    }

    @Override
    public Optional<AdvertisingAnswerFacts> answer(String unitId, String agreementId) {
        calls.add("answer " + unitId + " " + agreementId);
        return answerOf(unitId, agreementId);
    }

    @Override
    public Optional<AdvertisingAnswerFacts> answerUnderShareLock(String unitId, String agreementId) {
        calls.add("answerUnderShareLock " + unitId + " " + agreementId);
        return answerOf(unitId, agreementId);
    }

    private Optional<AdvertisingAnswerFacts> answerOf(String unitId, String agreementId) {
        return Optional.ofNullable(answers.get(agreementId)).filter(answer -> answer.unitId().equals(unitId));
    }
}
