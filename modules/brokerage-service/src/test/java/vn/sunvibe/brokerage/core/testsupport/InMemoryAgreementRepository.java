package vn.sunvibe.brokerage.core.testsupport;

import org.springframework.dao.OptimisticLockingFailureException;
import vn.sunvibe.brokerage.core.domain.exception.AgreementExistsException;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.repository.AgreementDomainRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Agreements in a map by id. {@link #create} refuses a second unfinished agreement of a unit and type as the
 * database's partial unique index does; every repository call is recorded by method name.
 */
public final class InMemoryAgreementRepository implements AgreementDomainRepository {

    private final Map<String, BrokerageAgreement> agreements = new LinkedHashMap<>();
    private final List<String> calls = new ArrayList<>();

    /** Stores an agreement as if an earlier transaction had written it; not recorded as a call. */
    public InMemoryAgreementRepository store(BrokerageAgreement agreement) {
        agreements.put(agreement.getId(), agreement);
        return this;
    }

    @Override
    public void create(BrokerageAgreement agreement) {
        calls.add("create");
        boolean taken = agreements.values().stream().anyMatch(stored -> stored.getStatus().isUnfinished()
                && stored.getUnitId().equals(agreement.getUnitId())
                && stored.getTransactionType() == agreement.getTransactionType());
        if (taken) {
            throw new AgreementExistsException();
        }
        agreements.put(agreement.getId(), agreement);
    }

    @Override
    public Optional<BrokerageAgreement> findById(String id) {
        calls.add("findById");
        return Optional.ofNullable(agreements.get(id));
    }

    /**
     * Replaces the stored agreement when the row version it was read with is the stored one, and stores it with that
     * row version plus one, as the guarded update does; else throws Spring's optimistic-locking failure.
     */
    @Override
    public void update(BrokerageAgreement agreement) {
        calls.add("update");
        BrokerageAgreement stored = agreements.get(agreement.getId());
        if (stored == null || !Objects.equals(stored.getRowVersionOrNull(), agreement.getRowVersionOrNull())) {
            throw new OptimisticLockingFailureException("The agreement changed since it was read.");
        }
        agreements.put(agreement.getId(), BrokerageAgreement.load(agreement.getId(), agreement.getUnitId(),
                agreement.getUnitCodeOrNull(), agreement.getPropertyId(), agreement.getTransactionType(),
                agreement.getOwnerUserId(), agreement.getBasisLeadId(), agreement.getStatus(),
                agreement.getCreatedByUserId(), agreement.getCreatedAt(), agreement.getUpdatedAt(),
                agreement.getRowVersionOrNull() + 1, agreement.getVersions()));
    }

    @Override
    public List<UnfinishedAgreement> unfinishedOf(Collection<String> unitIds, TransactionType type) {
        calls.add("unfinishedOf");
        return agreements.values().stream()
                .filter(agreement -> agreement.getStatus().isUnfinished() && agreement.getTransactionType() == type
                        && unitIds.contains(agreement.getUnitId()))
                .map(agreement -> new UnfinishedAgreement(agreement.getId(), agreement.getUnitId(),
                        agreement.getBasisLeadId()))
                .toList();
    }

    /** Every stored agreement, in insertion order. */
    public List<BrokerageAgreement> all() {
        return List.copyOf(agreements.values());
    }

    /** The calls made so far, by method name, in order. */
    public List<String> calls() {
        return List.copyOf(calls);
    }

    /** How many times the method was called. */
    public long count(String method) {
        return calls.stream().filter(method::equals).count();
    }
}
