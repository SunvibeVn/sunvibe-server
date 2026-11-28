package vn.sunvibe.listing.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.query.ListingPublicView;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingReviewFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementTermsFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitAvailabilityFacts;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessSummary;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingPublication;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;
import vn.sunvibe.listing.core.domain.policy.listing.FreshnessPolicy;
import vn.sunvibe.listing.core.testsupport.InMemoryListingRepository;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;
import vn.sunvibe.listing.core.testsupport.StubAdvertisingRightPort;
import vn.sunvibe.listing.core.testsupport.StubUnitInventoryPort;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CODE;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CREATED;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.NOW;

/**
 * The anonymous public read (listing-publish E06-S7 spec E06S7-D5; criteria PB-5, PB-6 unit part; US-5 AC-5, US-7 AC-7):
 * the publication row by its code, the listing stored Published, then E06-S6's facts read without a lock and the rule of
 * a shown revision; every hidden case is the one "not found", and a fact listing cannot read is not caught (fail
 * closed). The answer carries the Vietnam date the revision went live and the date of the latest valid confirmation.
 */
class ListingPublicQueryServiceTest {

    private static final String LISTING = "01JFQ9P0000000000000000001";
    private static final String AGREEMENT = "01AGREEMENT000000000000001";
    private static final String UNIT = "01UNIT00000000000000000001";

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final StubAdvertisingRightPort rights = new StubAdvertisingRightPort();
    private final StubUnitInventoryPort units = new StubUnitInventoryPort();
    private final LocalDate confirmedOn = FreshnessPolicy.today(Instant.now()).minusDays(3);

    private ListingPublicQueryService service() {
        return new ListingPublicQueryService(listings, new DisplayConditionsAssembler(rights, units, listings));
    }

    /** PB-1 unit part: every condition holding answers the public data, the live date and the confirmation date. */
    @Test
    void publicListing_everyConditionHolds_publicDataAndDates() {
        ListingPublication publication = world(ListingState.PUBLISHED);

        ListingPublicView view = service().publicListing(CODE.value());

        assertAll(
                () -> assertEquals(List.of(CODE.value(), "LONG_TERM_RENT", "APARTMENT", "48", "20194"), List.of(
                        view.code(), view.transactionType(), view.propertyTypeOrNull(), view.provinceCodeOrNull(),
                        view.wardCodeOrNull())),
                () -> assertEquals(List.of(2, 1), List.of(view.bedrooms(), view.bathrooms())),
                () -> assertEquals(32.5, view.areaSqm()),
                () -> assertEquals(publication.terms(), view.terms()),
                () -> assertEquals(List.of(RevisionFixtures.TITLE, RevisionFixtures.DESCRIPTION),
                        List.of(view.title(), view.description())),
                () -> assertEquals(RevisionFixtures.EVERY_FEATURE, view.features()),
                () -> assertEquals(FreshnessPolicy.today(NOW), view.liveOn()),
                () -> assertEquals(confirmedOn, view.lastConfirmedOn()));
    }

    /** US-5 AC-5: no publication row for the code: the one "not found". */
    @Test
    void publicListing_noPublication_notFound() {
        world(ListingState.PUBLISHED);

        assertThrows(ListingNotFoundException.class, () -> service().publicListing("TD-2222-2222"));
    }

    /** E06S7-D5 step (2): a row whose listing is stored in another state is hidden, and E05 and property are not read. */
    @Test
    void publicListing_storedNotPublished_notFoundReadsNoE05OrProperty() {
        world(ListingState.PAUSED);

        assertThrows(ListingNotFoundException.class, () -> service().publicListing(CODE.value()));
        assertEquals(List.of(List.of(), List.of()), List.of(rights.calls(), units.calls()));
    }

    /** PB-6, listing BR-4 (b): E05 does not allow advertising the unit now (a suspension): hidden. */
    @Test
    void publicListing_e05NotAllowed_notFound() {
        world(ListingState.PUBLISHED);
        rights.review(review(false, List.of("SUSPENDED"), DisplayConditionsAssemblerTest.snapshotTerms()));

        assertThrows(ListingNotFoundException.class, () -> service().publicListing(CODE.value()));
    }

    /** Listing BR-4 (c): the terms in effect differ from the row's: hidden. */
    @Test
    void publicListing_termsInEffectDiffer_notFound() {
        world(ListingState.PUBLISHED);
        AgreementTermsFacts.Terms changed = DisplayConditionsAssemblerTest.snapshotTerms();
        rights.review(review(true, List.of(), new AgreementTermsFacts.Terms(9_000_000L, changed.depositBasisOrNull(),
                changed.depositMonthsOrNull(), changed.depositAmountOrNull(), changed.minimumTermMonthsOrNull(),
                changed.paymentPeriodMonthsOrNull(), changed.fees())));

        assertThrows(ListingNotFoundException.class, () -> service().publicListing(CODE.value()));
    }

    /** PB-5, listing BR-4 (d), listing-publish BR-15: an intake entry says the availability differs: hidden. */
    @Test
    void publicListing_availabilityDiffers_notFound() {
        world(ListingState.PUBLISHED);
        units.availability(new UnitAvailabilityFacts(UNIT, "P101", "AVAILABLE", true));

        assertThrows(ListingNotFoundException.class, () -> service().publicListing(CODE.value()));
    }

    /** Listing BR-4 (e), US-6 AC-4: the deadline of the latest valid confirmation has passed: hidden. */
    @Test
    void publicListing_freshnessDeadlinePassed_notFound() {
        world(ListingState.PUBLISHED);
        listings.putFreshness(LISTING, new FreshnessSummary(FreshnessPolicy.today(Instant.now()).minusDays(15), true),
                null);

        assertThrows(ListingNotFoundException.class, () -> service().publicListing(CODE.value()));
    }

    /** PB-26 unit part (E06S1-D9): an E05 group listing does not know is not caught; never a view, never the 404. */
    @Test
    void publicListing_unknownE05Group_passesThrough() {
        world(ListingState.PUBLISHED);
        rights.review(review(false, List.of("SOMETHING_NEW"), DisplayConditionsAssemblerTest.snapshotTerms()));

        assertThrows(IllegalStateException.class, () -> service().publicListing(CODE.value()));
    }

    /** PB-26: an availability code listing does not know is not caught. */
    @Test
    void publicListing_unknownAvailability_passesThrough() {
        world(ListingState.PUBLISHED);
        units.availability(new UnitAvailabilityFacts(UNIT, "P101", "RESERVED", false));

        assertThrows(IllegalStateException.class, () -> service().publicListing(CODE.value()));
    }

    /** PB-26: a terms code of E05's in effect that listing does not know is not caught. */
    @Test
    void publicListing_unknownTermsCode_passesThrough() {
        world(ListingState.PUBLISHED);
        AgreementTermsFacts.Terms known = DisplayConditionsAssemblerTest.snapshotTerms();
        rights.review(review(true, List.of(), new AgreementTermsFacts.Terms(known.monthlyRentOrNull(), "WEEKS",
                known.depositMonthsOrNull(), known.depositAmountOrNull(), known.minimumTermMonthsOrNull(),
                known.paymentPeriodMonthsOrNull(), known.fees())));

        assertThrows(IllegalStateException.class, () -> service().publicListing(CODE.value()));
    }

    /** PB-26: a port that throws is not caught; the same exception reaches the caller. */
    @Test
    void publicListing_portThrows_passesThrough() {
        world(ListingState.PUBLISHED);
        IllegalArgumentException down = new IllegalArgumentException("E05 down");
        StubAdvertisingRightPort failing = new StubAdvertisingRightPort() {
            @Override
            public Optional<AdvertisingReviewFacts> review(String unitId, String agreementId) {
                throw down;
            }
        };
        ListingPublicQueryService service = new ListingPublicQueryService(listings,
                new DisplayConditionsAssembler(failing, units, listings));

        assertSame(down, assertThrows(IllegalArgumentException.class, () -> service.publicListing(CODE.value())));
    }

    /**
     * The listing stored in the state given with its live revision 1 (put live at {@code NOW}), its publication row, E05
     * allowing with the snapshot's terms in effect, the unit available, a valid confirmation three days ago.
     */
    private ListingPublication world(ListingState state) {
        ListingRevision live = ListingRevision.load("R1", 1, RevisionStatus.LIVE, "S1", CREATED, CREATED,
                RevisionFixtures.completeContent(), RevisionFixtures.inEffectTerms(), RevisionFixtures.unitSnapshot(),
                CREATED, "S1", null, NOW, null, true);
        Listing listing = Listing.load(LISTING, CODE, UNIT, TransactionType.LONG_TERM_RENT, AGREEMENT, state, "S1",
                CREATED, CREATED, 7L, List.of(live));
        listings.put(listing);
        ListingPublication publication = ListingPublication.of(listing, live).orElseThrow();
        listings.putPublication(publication);
        rights.review(review(true, List.of(), DisplayConditionsAssemblerTest.snapshotTerms()));
        units.availability(new UnitAvailabilityFacts(UNIT, "P101", "AVAILABLE", false));
        listings.putFreshness(LISTING, new FreshnessSummary(confirmedOn, true), null);
        return publication;
    }

    private static AdvertisingReviewFacts review(boolean allowed, List<String> groups, AgreementTermsFacts.Terms terms) {
        return new AdvertisingReviewFacts(UNIT, AGREEMENT, false, allowed, groups, null, true, true, terms, null, null);
    }
}
