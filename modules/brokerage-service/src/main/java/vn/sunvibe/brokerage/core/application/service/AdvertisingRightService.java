package vn.sunvibe.brokerage.core.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.sunvibe.brokerage.core.application.shared.external.lead.BasisLeadAssigneePort;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingPair;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightUseCase;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightView;
import vn.sunvibe.brokerage.core.application.usecase.AgreementStandingView;
import vn.sunvibe.brokerage.core.application.usecase.UnfinishedAgreementView;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.policy.advertising.AdvertisingRightPolicy;
import vn.sunvibe.brokerage.core.domain.repository.AgreementDomainRepository;
import vn.sunvibe.shared.time.AppClock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Use Case Implementation - the published, viewer-less answer for listing (E05-S6 spec S6-D9 to S6-D11). The set reads
 * run read-only, joining the caller's transaction or in one of their own, at a fixed number of statements: the
 * agreements with their versions, fee and tier rows (three statements), then the reads of the assembler or the
 * assignees' port, each once per call. Nothing is written, and no access row: the caller decided first that it may
 * read what it asks (ADR-012 decision 2).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdvertisingRightService implements AdvertisingRightUseCase {

    private final AgreementDomainRepository agreements;
    private final AdvertisingRightAssembler assembler;
    private final BasisLeadAssigneePort assignees;

    @Override
    public List<AdvertisingRightView> answers(Collection<AdvertisingPair> pairs) {
        List<AdvertisingPair> distinct = pairs.stream()
                .filter(pair -> pair != null && pair.unitId() != null && pair.agreementId() != null)
                .distinct().toList();
        requireAtMostMax(distinct.size());
        if (distinct.isEmpty()) {
            return List.of();
        }
        return answer(distinct);
    }

    @Override
    public List<UnfinishedAgreementView> unfinishedOf(Collection<String> unitIds, String transactionType) {
        List<String> distinct = unitIds.stream().filter(Objects::nonNull).distinct().toList();
        requireAtMostMax(distinct.size());
        TransactionType type = TransactionType.of(transactionType).orElse(null);
        if (type == null || distinct.isEmpty()) {
            return List.of();
        }
        return agreements.unfinishedOf(distinct, type).stream()
                .map(unfinished -> new UnfinishedAgreementView(unfinished.unitId(), unfinished.agreementId(),
                        type.name()))
                .toList();
    }

    @Override
    public List<AgreementStandingView> standings(Collection<String> agreementIds) {
        List<String> distinct = agreementIds.stream().filter(Objects::nonNull).distinct().toList();
        requireAtMostMax(distinct.size());
        if (distinct.isEmpty()) {
            return List.of();
        }
        Map<String, BrokerageAgreement> found = new HashMap<>();
        agreements.findAllById(distinct).forEach(agreement -> found.put(agreement.getId(), agreement));
        List<BrokerageAgreement> inOrder = distinct.stream().map(found::get).filter(Objects::nonNull).toList();
        if (inOrder.isEmpty()) {
            return List.of();
        }
        Map<String, String> assigneeByLead = new HashMap<>();
        assignees.assigneesOf(inOrder.stream().map(BrokerageAgreement::getBasisLeadId).distinct().toList())
                .forEach(lead -> assigneeByLead.put(lead.leadId(), lead.assignedUserIdOrNull()));
        Instant now = AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
        return inOrder.stream()
                .map(agreement -> new AgreementStandingView(agreement.getId(), agreement.getUnitId(),
                        agreement.getTransactionType().name(),
                        AdvertisingRightPolicy.ended(agreement.effectiveAt(now), now),
                        assigneeByLead.get(agreement.getBasisLeadId())))
                .toList();
    }

    /**
     * E05-S6 spec S6-D11: it never opens a transaction of its own (SUPPORTS, which also overrides the class's read-only
     * rule); its first statement checks that the caller holds a read-write one, since PostgreSQL refuses FOR SHARE in a
     * read-only transaction and without one the lock would protect nothing.
     */
    @Override
    @Transactional(propagation = Propagation.SUPPORTS)
    public Optional<AdvertisingRightView> answerUnderShareLock(AdvertisingPair pair) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("the locking read of an agreement needs a read-write transaction");
        }
        if (pair == null || pair.unitId() == null || pair.agreementId() == null
                || !agreements.lockForShare(pair.agreementId())) {
            return Optional.empty();
        }
        // read after the lock, in the same transaction: a stop or activation that commits first is what it sees
        return answer(List.of(pair)).stream().findFirst();
    }

    /**
     * The answers of distinct, non-null pairs, in their order: a pair whose agreement is unknown or rests on another
     * unit is left out (fail closed).
     */
    private List<AdvertisingRightView> answer(List<AdvertisingPair> pairs) {
        Map<String, BrokerageAgreement> found = new HashMap<>();
        agreements.findAllById(pairs.stream().map(AdvertisingPair::agreementId).distinct().toList())
                .forEach(agreement -> found.put(agreement.getId(), agreement));
        Instant now = AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
        List<BrokerageAgreement> matched = pairs.stream()
                .map(pair -> {
                    BrokerageAgreement agreement = found.get(pair.agreementId());
                    return agreement != null && agreement.getUnitId().equals(pair.unitId()) ? agreement : null;
                })
                .filter(Objects::nonNull)
                .map(agreement -> agreement.effectiveAt(now))
                .toList();
        return matched.isEmpty() ? List.of() : assembler.answers(matched, now);
    }

    private static void requireAtMostMax(int count) {
        if (count > MAX_PAIRS) {
            throw new IllegalArgumentException("at most " + MAX_PAIRS + " distinct values per call");
        }
    }
}
