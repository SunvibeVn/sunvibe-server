package vn.sunvibe.brokerage.core.testsupport;

import vn.sunvibe.brokerage.core.domain.exception.AgreementExistsException;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.repository.AgreementDomainRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
