package vn.sunvibe.listing.core.testsupport;

import vn.sunvibe.listing.core.domain.exception.ListingChangedException;
import vn.sunvibe.listing.core.domain.exception.OpenListingExistsException;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessRecord;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessSummary;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryEntry;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.repository.ListingDomainRepository;
import vn.sunvibe.listing.core.domain.repository.ListingRevisionPage;
import vn.sunvibe.listing.core.domain.repository.ListingRow;
import vn.sunvibe.listing.core.domain.repository.ListingRowPage;
import vn.sunvibe.listing.core.domain.repository.ReconfirmationCandidate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * In-memory listings for the service tests: keeps what was inserted or put, records each call, and can be told to
 * refuse the next insert as the open-listing index does or to report codes as taken.
 */
public class InMemoryListingRepository implements ListingDomainRepository {

    private final List<Listing> stored = new ArrayList<>();
    private final List<Listing> inserted = new ArrayList<>();
    private final Set<String> takenCodes = new HashSet<>();
    private final List<String> calls = new ArrayList<>();
    private final List<ListingHistoryEntry> writtenHistory = new ArrayList<>();
    private final Map<String, FreshnessSummary> summaries = new HashMap<>();
    private final Map<String, FreshnessRecord> latestRecords = new HashMap<>();
    private final List<FreshnessRecord> writtenRecords = new ArrayList<>();
    private final List<ReconfirmationCandidate> candidates = new ArrayList<>();
    private boolean refuseNextInsertAsOpen;

    /** Stores a listing as if an earlier command had, without recording an insert. */
    public void put(Listing listing) {
        stored.add(listing);
    }

    public void takeCodes(String... codes) {
        takenCodes.addAll(List.of(codes));
    }

    public void refuseNextInsertAsOpen() {
        refuseNextInsertAsOpen = true;
    }

    /** The listings this repository was asked to insert, in order. */
    public List<Listing> inserted() {
        return List.copyOf(inserted);
    }

    public List<String> calls() {
        return List.copyOf(calls);
    }

    @Override
    public void insert(Listing listing) {
        calls.add("insert " + listing.id());
        if (refuseNextInsertAsOpen) {
            refuseNextInsertAsOpen = false;
            throw new OpenListingExistsException();
        }
        inserted.add(listing);
        stored.add(listing);
    }

    @Override
    public Optional<ListingRow> findRow(String listingId) {
        calls.add("findRow " + listingId);
        return stored.stream().filter(listing -> listing.id().equals(listingId)).findFirst()
                .map(InMemoryListingRepository::toRow);
    }

    @Override
    public ListingRevisionPage findNewestRevisions(String listingId, int limit) {
        calls.add("findNewestRevisions " + listingId + " " + limit);
        List<ListingRevision> newest = stored.stream().filter(listing -> listing.id().equals(listingId))
                .flatMap(listing -> listing.revisions().stream())
                .sorted(Comparator.comparingInt(ListingRevision::revisionNo).reversed())
                .toList();
        return new ListingRevisionPage(newest.stream().limit(limit).toList(), newest.size() > limit);
    }

    @Override
    public ListingRowPage findNewestByAgreement(String agreementId, int limit) {
        calls.add("findNewestByAgreement " + agreementId + " " + limit);
        List<ListingRow> newest = stored.stream().filter(listing -> listing.agreementId().equals(agreementId))
                .map(InMemoryListingRepository::toRow)
                .sorted(Comparator.comparing(ListingRow::createdAt).thenComparing(ListingRow::listingId).reversed())
                .toList();
        return new ListingRowPage(newest.stream().limit(limit).toList(), newest.size() > limit);
    }

    @Override
    public boolean hasOpen(String agreementId) {
        calls.add("hasOpen " + agreementId);
        return stored.stream().anyMatch(listing -> listing.agreementId().equals(agreementId)
                && listing.state() != ListingState.ARCHIVED);
    }

    @Override
    public boolean existsByCode(String code) {
        calls.add("existsByCode " + code);
        return takenCodes.contains(code) || stored.stream().anyMatch(listing -> listing.code().value().equals(code));
    }

    /** The working set as the adapter loads it: the open revision and the target, and the greatest number. */
    @Override
    public Optional<Listing> findForCommand(String listingId, Integer targetRevisionNoOrNull) {
        calls.add("findForCommand " + listingId + " " + targetRevisionNoOrNull);
        return stored(listingId).map(listing -> Listing.loadForCommand(listing.id(), listing.code(), listing.unitId(),
                listing.transactionType(), listing.agreementId(), listing.state(), listing.createdBy(),
                listing.createdAt(), listing.updatedAt(), listing.rowVersion(),
                listing.revisions().stream().mapToInt(ListingRevision::revisionNo).max().orElse(0),
                listing.revisions().stream()
                        .filter(revision -> revision.status() == RevisionStatus.DRAFT
                                || revision.status() == RevisionStatus.PENDING_REVIEW
                                || targetRevisionNoOrNull != null && revision.revisionNo() == targetRevisionNoOrNull)
                        .toList()));
    }

    @Override
    public Optional<ListingRevision> findRevision(String listingId, int revisionNo) {
        calls.add("findRevision " + listingId + " " + revisionNo);
        return stored(listingId).flatMap(listing -> listing.revisions().stream()
                .filter(revision -> revision.revisionNo() == revisionNo).findFirst());
    }

    @Override
    public Optional<Integer> findOpenRevisionNo(String listingId) {
        calls.add("findOpenRevisionNo " + listingId);
        return stored(listingId).flatMap(listing -> listing.revisions().stream()
                .filter(revision -> revision.status() == RevisionStatus.DRAFT
                        || revision.status() == RevisionStatus.PENDING_REVIEW)
                .map(ListingRevision::revisionNo).findFirst());
    }

    /**
     * Enforces the row version and the one-open rule as the adapter and the unique index do, then stores the listing
     * with its changed revisions, the version plus one and the pending history.
     */
    @Override
    public void update(Listing listing) {
        calls.add("update " + listing.id());
        Listing current = stored(listing.id()).orElseThrow(ListingChangedException::new);
        if (current.rowVersion() != listing.rowVersion()) {
            throw new ListingChangedException();
        }
        Map<Integer, ListingRevision> byNumber = new TreeMap<>();
        current.revisions().forEach(revision -> byNumber.put(revision.revisionNo(), revision));
        listing.pendingRevisionChanges().forEach(change ->
                byNumber.put(change.revision().revisionNo(), change.revision()));
        long open = byNumber.values().stream().filter(revision -> revision.status() == RevisionStatus.DRAFT
                || revision.status() == RevisionStatus.PENDING_REVIEW).count();
        if (open > 1) {
            throw new ListingChangedException();
        }
        Listing next = Listing.load(current.id(), current.code(), current.unitId(), current.transactionType(),
                current.agreementId(), current.state(), current.createdBy(), current.createdAt(), listing.updatedAt(),
                current.rowVersion() + 1, List.copyOf(byNumber.values()));
        stored.replaceAll(candidate -> candidate.id().equals(listing.id()) ? next : candidate);
        writtenHistory.addAll(listing.pendingHistory());
    }

    /** The history rows the updates wrote, in order. */
    public List<ListingHistoryEntry> writtenHistory() {
        return List.copyOf(writtenHistory);
    }

    /** The stored listing, as the next read would find it. */
    public Optional<Listing> stored(String listingId) {
        return stored.stream().filter(listing -> listing.id().equals(listingId)).findFirst();
    }

    /** Another command committed first: the stored row version moves. */
    public void moveVersion(String listingId) {
        Listing current = stored(listingId).orElseThrow();
        Listing moved = Listing.load(current.id(), current.code(), current.unitId(), current.transactionType(),
                current.agreementId(), current.state(), current.createdBy(), current.createdAt(), current.updatedAt(),
                current.rowVersion() + 1, current.revisions());
        stored.replaceAll(candidate -> candidate.id().equals(listingId) ? moved : candidate);
    }

    /** A listing's stored summary and latest record, as if earlier records had made them; nothing is recorded. */
    public void putFreshness(String listingId, FreshnessSummary summary, FreshnessRecord latestOrNull) {
        summaries.put(listingId, summary);
        if (latestOrNull == null) {
            latestRecords.remove(listingId);
        } else {
            latestRecords.put(listingId, latestOrNull);
        }
    }

    /** A row the candidate query reads; the candidates are these rows only, whatever is stored. */
    public void putCandidate(ReconfirmationCandidate candidate) {
        candidates.add(candidate);
    }

    /** The records {@link #recordFreshness} wrote for the listing, in order. */
    public List<FreshnessRecord> freshnessRecords(String listingId) {
        return writtenRecords.stream().filter(record -> record.listingId().equals(listingId)).toList();
    }

    @Override
    public FreshnessSummary findFreshnessSummary(String listingId) {
        calls.add("findFreshnessSummary " + listingId);
        return summaries.getOrDefault(listingId, FreshnessSummary.none());
    }

    @Override
    public Optional<FreshnessRecord> findLatestFreshnessRecord(String listingId) {
        calls.add("findLatestFreshnessRecord " + listingId);
        return Optional.ofNullable(latestRecords.get(listingId));
    }

    /**
     * Enforces the row version as {@link #update} does, then stores the listing with the version plus one and the time,
     * keeps the next summary and the record as the latest, and adds the history entry.
     */
    @Override
    public void recordFreshness(String listingId, long loadedRowVersion, Instant now, FreshnessSummary next,
                                FreshnessRecord record, ListingHistoryEntry entry) {
        calls.add("recordFreshness " + listingId);
        Listing current = stored(listingId).orElseThrow(ListingChangedException::new);
        if (current.rowVersion() != loadedRowVersion) {
            throw new ListingChangedException();
        }
        Listing moved = Listing.load(current.id(), current.code(), current.unitId(), current.transactionType(),
                current.agreementId(), current.state(), current.createdBy(), current.createdAt(), now,
                current.rowVersion() + 1, current.revisions());
        stored.replaceAll(candidate -> candidate.id().equals(listingId) ? moved : candidate);
        summaries.put(listingId, next);
        latestRecords.put(listingId, record);
        writtenRecords.add(record);
        writtenHistory.add(entry);
    }

    /** The rows of {@link #putCandidate} with the SQL's filter, order, cursor and limit. */
    @Override
    public List<ReconfirmationCandidate> findReconfirmationCandidates(LocalDate confirmedOnOrBefore,
                                                                      ReconfirmationCandidate afterOrNull, int limit) {
        calls.add("findReconfirmationCandidates " + confirmedOnOrBefore + " " + limit);
        Comparator<ReconfirmationCandidate> keyOrder = Comparator
                .comparing((ReconfirmationCandidate candidate) -> candidate.summary().confirmedOnOrNull())
                .thenComparing(ReconfirmationCandidate::listingId);
        return candidates.stream()
                .filter(candidate -> candidate.storedState() != ListingState.ARCHIVED)
                .filter(candidate -> candidate.summary().confirmedOnOrNull() != null
                        && !candidate.summary().confirmedOnOrNull().isAfter(confirmedOnOrBefore))
                .filter(candidate -> afterOrNull == null || keyOrder.compare(candidate, afterOrNull) > 0)
                .sorted(keyOrder)
                .limit(limit)
                .toList();
    }

    private static ListingRow toRow(Listing listing) {
        return new ListingRow(listing.id(), listing.code(), listing.unitId(), listing.transactionType(),
                listing.agreementId(), listing.state(), listing.createdBy(), listing.createdAt(), listing.rowVersion());
    }
}
