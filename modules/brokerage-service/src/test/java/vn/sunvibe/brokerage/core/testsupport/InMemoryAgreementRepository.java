package vn.sunvibe.brokerage.core.testsupport;

import org.springframework.dao.OptimisticLockingFailureException;
import vn.sunvibe.brokerage.core.domain.exception.AgreementExistsException;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementVersion;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionStatus;
import vn.sunvibe.brokerage.core.domain.repository.AgreementDomainRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
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
    private final Map<String, RuntimeException> failNextUpdate = new HashMap<>();
    private Runnable afterNextSelection;
    private boolean moveAfterNextRead;

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
        BrokerageAgreement read = agreements.get(id);
        if (read != null && moveAfterNextRead) {
            moveAfterNextRead = false;
            agreements.put(id, withRowVersion(read, read.getRowVersionOrNull() + 1));
        }
        return Optional.ofNullable(read);
    }

    /** After the next read, the stored row version moves on, as a concurrent command that commits in between does. */
    public InMemoryAgreementRepository moveAfterNextRead() {
        moveAfterNextRead = true;
        return this;
    }

    /**
     * Replaces the stored agreement when the row version it was read with is the stored one, and stores it with that
     * row version plus one, as the guarded update does; else throws Spring's optimistic-locking failure.
     */
    @Override
    public void update(BrokerageAgreement agreement) {
        calls.add("update");
        RuntimeException failure = failNextUpdate.remove(agreement.getId());
        if (failure != null) {
            throw failure;
        }
        BrokerageAgreement stored = agreements.get(agreement.getId());
        if (stored == null || !Objects.equals(stored.getRowVersionOrNull(), agreement.getRowVersionOrNull())) {
            throw new OptimisticLockingFailureException("The agreement changed since it was read.");
        }
        agreements.put(agreement.getId(), withRowVersion(agreement, agreement.getRowVersionOrNull() + 1));
    }

    private static BrokerageAgreement withRowVersion(BrokerageAgreement agreement, long rowVersion) {
        return BrokerageAgreement.load(agreement.getId(), agreement.getUnitId(), agreement.getUnitCodeOrNull(),
                agreement.getPropertyId(), agreement.getTransactionType(), agreement.getOwnerUserId(),
                agreement.getBasisLeadId(), agreement.getStatus(), agreement.getCreatedByUserId(),
                agreement.getCreatedAt(), agreement.getUpdatedAt(), rowVersion, agreement.getVersions());
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

    /** The owner's agreements with a sent version, by their latest sending (newest first), then id; at most limit. */
    @Override
    public List<BrokerageAgreement> sentToOwner(String ownerUserId, int limit) {
        calls.add("sentToOwner");
        return agreements.values().stream()
                .filter(agreement -> agreement.getOwnerUserId().equals(ownerUserId) && latestSending(agreement) != null)
                .sorted(Comparator.comparing(InMemoryAgreementRepository::latestSending).reversed()
                        .thenComparing(BrokerageAgreement::getId))
                .limit(limit)
                .toList();
    }

    /** The agreements whose AWAITING_OWNER version was sent at or before the bound, oldest first, then id. */
    @Override
    public List<String> dueLapseAgreementIds(Instant sentAtOrBefore, int limit) {
        calls.add("dueLapseAgreementIds");
        Map<String, Instant> sentById = new HashMap<>();
        for (BrokerageAgreement agreement : agreements.values()) {
            agreement.getVersions().stream()
                    .filter(version -> version.getStatus() == VersionStatus.AWAITING_OWNER
                            && version.getSentAtOrNull() != null && !version.getSentAtOrNull().isAfter(sentAtOrBefore))
                    .findFirst()
                    .ifPresent(version -> sentById.put(agreement.getId(), version.getSentAtOrNull()));
        }
        List<String> selected = sentById.entrySet().stream()
                .sorted(Map.Entry.<String, Instant>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .limit(limit)
                .toList();
        Runnable change = afterNextSelection;
        afterNextSelection = null;
        if (change != null) {
            change.run();
        }
        return selected;
    }

    /** Runs once, right after the next due-lapse selection: a change another transaction commits in between. */
    public InMemoryAgreementRepository afterNextSelection(Runnable change) {
        afterNextSelection = change;
        return this;
    }

    /** The next update of that agreement throws this failure and stores nothing, as a lost or failed write does. */
    public InMemoryAgreementRepository failNextUpdate(String agreementId, RuntimeException failure) {
        failNextUpdate.put(agreementId, failure);
        return this;
    }

    private static Instant latestSending(BrokerageAgreement agreement) {
        return agreement.getVersions().stream().map(AgreementVersion::getSentAtOrNull).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
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
