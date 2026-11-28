package vn.sunvibe.listing.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingCode;
import vn.sunvibe.listing.core.domain.model.listing.ListingPublication;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;
import vn.sunvibe.listing.core.testsupport.InMemoryListingRepository;
import vn.sunvibe.listing.core.testsupport.RecordingEventOutboxPort;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;
import vn.sunvibe.shared.outbox.ListingChangedEvent;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Listing's answer to E05's event (listing-publish E06-S7 spec E06S7-D13; criterion PB-16, Review Focus 5): every
 * listing of the unit that has a publication row is signalled, one listing event each, in id order; a unit without
 * one is signalled nothing. The fake holds a published listing of another unit in every case, so a read that ignores
 * the unit signals it.
 */
class ListingSignalServiceTest {

    private static final String UNIT = "01J000000000000000000000N1";
    private static final String OTHER_UNIT = "01J000000000000000000000N2";
    private static final String FIRST = "01J0000000000000000000000A";
    private static final String SECOND = "01J0000000000000000000000C";
    private static final String OF_OTHER_UNIT = "01J0000000000000000000000B";
    private static final Instant CREATED = Instant.parse("2026-11-20T03:00:00Z");
    private static final Instant LIVE_AT = Instant.parse("2026-11-27T03:00:00Z");

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final RecordingEventOutboxPort outbox = new RecordingEventOutboxPort();
    private final ListingSignalService service = new ListingSignalService(listings, outbox);

    /** Review Focus 5: two published listings of the unit, both signalled, in id order. */
    @Test
    void signalUnit_twoPublishedListings_bothSignalledInIdOrder() {
        published(SECOND, UNIT, "TD-2345-678C");
        published(OF_OTHER_UNIT, OTHER_UNIT, "TD-2345-678B");
        published(FIRST, UNIT, "TD-2345-678A");

        service.signalUnit(UNIT);

        assertEquals(List.of(ListingChangedEvent.now(FIRST), ListingChangedEvent.now(SECOND)), outbox.events());
    }

    /** PB-16: a unit without a published listing: nothing is appended. */
    @Test
    void signalUnit_unitWithoutPublished_none() {
        published(OF_OTHER_UNIT, OTHER_UNIT, "TD-2345-678B");

        service.signalUnit(UNIT);

        assertEquals(List.of(), outbox.events());
    }

    /** A listing of the unit, stored Published with its live revision 1 and its publication row. */
    private void published(String listingId, String unitId, String code) {
        ListingRevision live = ListingRevision.load("R" + listingId, 1, RevisionStatus.LIVE, "S1", CREATED, CREATED,
                RevisionFixtures.completeContent(), RevisionFixtures.inEffectTerms(), RevisionFixtures.unitSnapshot(),
                CREATED, "S1", null, LIVE_AT, null, true);
        Listing listing = Listing.load(listingId, ListingCode.of(code), unitId, TransactionType.LONG_TERM_RENT,
                "AGREEMENT-" + listingId, ListingState.PUBLISHED, "S1", CREATED, CREATED, 3L, List.of(live));
        listings.put(listing);
        listings.putPublication(ListingPublication.of(listing, live).orElseThrow());
    }
}
