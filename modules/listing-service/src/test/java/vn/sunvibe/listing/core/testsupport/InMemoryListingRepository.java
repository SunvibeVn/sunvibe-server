package vn.sunvibe.listing.core.testsupport;

import vn.sunvibe.listing.core.domain.exception.OpenListingExistsException;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.repository.ListingDomainRepository;
import vn.sunvibe.listing.core.domain.repository.ListingRevisionPage;
import vn.sunvibe.listing.core.domain.repository.ListingRow;
import vn.sunvibe.listing.core.domain.repository.ListingRowPage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * In-memory listings for the service tests: keeps what was inserted or put, records each call, and can be told to
 * refuse the next insert as the open-listing index does or to report codes as taken.
 */
public class InMemoryListingRepository implements ListingDomainRepository {

    private final List<Listing> stored = new ArrayList<>();
    private final List<Listing> inserted = new ArrayList<>();
    private final Set<String> takenCodes = new HashSet<>();
    private final List<String> calls = new ArrayList<>();
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

    private static ListingRow toRow(Listing listing) {
        return new ListingRow(listing.id(), listing.code(), listing.unitId(), listing.transactionType(),
                listing.agreementId(), listing.state(), listing.createdBy(), listing.createdAt(), listing.rowVersion());
    }
}
