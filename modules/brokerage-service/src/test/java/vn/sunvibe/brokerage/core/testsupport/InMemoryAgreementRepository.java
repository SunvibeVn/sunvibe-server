package vn.sunvibe.brokerage.core.testsupport;

import org.springframework.dao.OptimisticLockingFailureException;
import vn.sunvibe.brokerage.core.domain.exception.AgreementExistsException;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationDecisionType;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementVersion;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.OwnerAnswerType;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionStatus;
import vn.sunvibe.brokerage.core.domain.repository.AgreementDomainRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
                agreement.getCreatedAt(), agreement.getUpdatedAt(), rowVersion, agreement.getVersions(),
                agreement.getEndedAtOrNull(), agreement.getTerminationOrNull());
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

    /** The stored agreements of those ids, in the order given, each once; an unknown id is left out. */
    @Override
    public List<BrokerageAgreement> findAllById(Collection<String> ids) {
        calls.add("findAllById");
        return new LinkedHashSet<>(ids).stream().map(agreements::get).filter(Objects::nonNull).toList();
    }

    /** Whether the agreement is stored; nothing is locked in memory. */
    @Override
    public boolean lockForShare(String agreementId) {
        calls.add("lockForShare");
        return agreements.containsKey(agreementId);
    }

    /** The agreements whose OWNER_CONFIRMED version was confirmed at or before the bound, oldest first, then id. */
    @Override
    public List<String> dueActivationLapseAgreementIds(Instant answeredAtOrBefore, int limit) {
        calls.add("dueActivationLapseAgreementIds");
        Map<String, Instant> answeredById = new HashMap<>();
        for (BrokerageAgreement agreement : agreements.values()) {
            agreement.getVersions().stream()
                    .filter(version -> version.getStatus() == VersionStatus.OWNER_CONFIRMED
                            && version.getOwnerAnswerOrNull() != null
                            && version.getOwnerAnswerOrNull().type() == OwnerAnswerType.CONFIRMED
                            && !version.getOwnerAnswerOrNull().answeredAt().isAfter(answeredAtOrBefore))
                    .findFirst()
                    .ifPresent(version -> answeredById.put(agreement.getId(),
                            version.getOwnerAnswerOrNull().answeredAt()));
        }
        return answeredById.entrySet().stream()
                .sorted(Map.Entry.<String, Instant>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .limit(limit)
                .toList();
    }

    /** The agreements whose ACTIVATED version holds its record and starts on or before the bound, oldest first. */
    @Override
    public List<String> dueStartAgreementIds(LocalDate startOnOrBefore, int limit) {
        calls.add("dueStartAgreementIds");
        Map<String, LocalDate> startById = new HashMap<>();
        for (BrokerageAgreement agreement : agreements.values()) {
            agreement.getVersions().stream()
                    .filter(version -> version.getStatus() == VersionStatus.ACTIVATED
                            && version.getActivationOrNull() != null
                            && version.getActivationOrNull().type() == ActivationDecisionType.ACTIVATED
                            && !version.getPeriod().startDate().isAfter(startOnOrBefore))
                    .findFirst()
                    .ifPresent(version -> startById.put(agreement.getId(), version.getPeriod().startDate()));
        }
        return startById.entrySet().stream()
                .sorted(Map.Entry.<String, LocalDate>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .limit(limit)
                .toList();
    }

    /** The stored agreement as the lock and the fresh reads find it; nothing is locked in memory. */
    @Override
    public Optional<BrokerageAgreement> findByIdForUpdate(String id) {
        calls.add("findByIdForUpdate");
        return Optional.ofNullable(agreements.get(id));
    }

    /**
     * The agreements stored ACTIVE or SUSPENDED that hold an EFFECTIVE or ACTIVATED version and none of those ending on
     * or after the bound, by id, at most limit.
     */
    @Override
    public List<String> dueExpiryAgreementIds(LocalDate endBefore, int limit) {
        calls.add("dueExpiryAgreementIds");
        return agreements.values().stream()
                .filter(agreement -> agreement.getStatus() == AgreementStatus.ACTIVE
                        || agreement.getStatus() == AgreementStatus.SUSPENDED)
                .filter(agreement -> {
                    List<AgreementVersion> inEffect = agreement.getVersions().stream()
                            .filter(version -> version.getStatus() == VersionStatus.EFFECTIVE
                                    || version.getStatus() == VersionStatus.ACTIVATED)
                            .toList();
                    return !inEffect.isEmpty() && inEffect.stream()
                            .allMatch(version -> version.getPeriod().endDate().isBefore(endBefore));
                })
                .map(BrokerageAgreement::getId)
                .sorted()
                .limit(limit)
                .toList();
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
