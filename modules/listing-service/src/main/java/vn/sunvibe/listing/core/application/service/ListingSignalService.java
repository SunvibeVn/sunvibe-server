package vn.sunvibe.listing.core.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.listing.core.application.usecase.ListingSignalUseCase;
import vn.sunvibe.listing.core.domain.repository.ListingDomainRepository;
import vn.sunvibe.shared.outbox.EventOutboxPort;
import vn.sunvibe.shared.outbox.ListingChangedEvent;

/**
 * Use Case Implementation - listing's answer to E05's event (listing-publish E06-S7 spec E06S7-D13; ADR-012 decision 6,
 * ADR-014 decision 6): one statement reads the ids of the unit's listings that have a publication row, in id order,
 * and each gets the listing event, due now, in this one read-write transaction. It records no archive (E06-S9) and keeps
 * no fingerprint (E06-S7c): a repeated delivery appends repeated signals, which the listing event's consumers read as
 * "read again". No log line names a listing or a unit.
 */
@Service
public class ListingSignalService implements ListingSignalUseCase {

    private final ListingDomainRepository listings;
    private final EventOutboxPort outbox;

    public ListingSignalService(ListingDomainRepository listings, EventOutboxPort outbox) {
        this.listings = listings;
        this.outbox = outbox;
    }

    @Override
    @Transactional
    public void signalUnit(String unitId) {
        for (String listingId : listings.findPublishedListingIds(unitId)) {
            outbox.append(ListingChangedEvent.now(listingId));
        }
    }
}
