package vn.sunvibe.listing.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.query.AgreementListingsView;
import vn.sunvibe.listing.core.application.query.ListingView;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingAnswerFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingCode;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;
import vn.sunvibe.listing.core.testsupport.InMemoryListingRepository;
import vn.sunvibe.listing.core.testsupport.StubAdvertisingRightPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup.ENDED;
import static vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup.SUSPENDED;

/**
 * The listing read and the agreement's listing section read (listing BR-5, BR-16; listing-publish E06-S1 spec 3.3,
 * E06S1-D5, E06S1-D8, E06S1-D17, E06S1-P11; LC-8 to LC-10, LC-16, LC-18 at service level), with fakes.
 */
class ListingQueryServiceTest {

    private static final String LISTING = id("01JGL");
    private static final String AGREEMENT = id("01JFA");
    private static final String UNIT = id("01JEU");
    private static final String SALE = id("01JDS");
    private static final String OTHER_SALE = id("01JDO");
    private static final Instant CREATED = Instant.parse("2026-11-20T03:15:00.123456Z");
    private static final ListingViewer SALE_VIEWER = new ListingViewer(SALE, Set.of("ROLE_SALE"));

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final StubAdvertisingRightPort rights = new StubAdvertisingRightPort();
    private final ListingQueryService service = new ListingQueryService(listings, rights);

    @Test
    void listing_assignedSale_viewAndAssignedFlag() {
        storeOpenListing();
        standing(false, SALE);

        ListingView view = service.listing(LISTING, SALE_VIEWER);

        assertEquals(new ListingView(LISTING, "TD-7K3M-Q9TX", ListingState.UNPUBLISHED, TransactionType.LONG_TERM_RENT,
                UNIT, AGREEMENT, CREATED, 0L, true, List.of(new ListingView.Revision(1, RevisionStatus.DRAFT, CREATED)),
                false), view);
        assertEquals(List.of("standings [" + AGREEMENT + "]"), rights.calls());
    }

    @Test
    void listing_everyListingRole_viewNotAssigned() {
        storeOpenListing();
        standing(false, SALE);

        for (String role : List.of("ROLE_SALE_LEADER", "ROLE_SALES_MANAGER", "ROLE_ADMIN", "ROLE_LISTING_REVIEWER")) {
            ListingView view = service.listing(LISTING, new ListingViewer(OTHER_SALE, Set.of(role)));

            assertEquals(LISTING, view.listingId(), role);
            assertFalse(view.viewerIsAssignedSale(), role);
        }
    }

    @Test
    void listing_otherSale_notFound() {
        storeOpenListing();
        standing(false, SALE);

        assertThrows(ListingNotFoundException.class,
                () -> service.listing(LISTING, new ListingViewer(OTHER_SALE, Set.of("ROLE_SALE"))));
        assertFalse(listings.calls().stream().anyMatch(call -> call.startsWith("findNewestRevisions")));
    }

    @Test
    void listing_malformedId_notFoundNoRead() {
        storeOpenListing();
        standing(false, SALE);
        ListingViewer admin = new ListingViewer(OTHER_SALE, Set.of("ROLE_ADMIN"));

        for (String malformed : List.of(LISTING.toLowerCase(), LISTING.substring(1), LISTING + "0", " " + LISTING.substring(1))) {
            assertThrows(ListingNotFoundException.class, () -> service.listing(malformed, admin), malformed);
        }
        assertTrue(listings.calls().isEmpty());
        assertTrue(rights.calls().isEmpty());
    }

    @Test
    void listing_agreementEnded_archived() {
        storeOpenListing();
        standing(true, SALE);

        assertEquals(ListingState.ARCHIVED, service.listing(LISTING, SALE_VIEWER).state());
    }

    @Test
    void listing_standingGone_archivedNoAssignee() {
        storeOpenListing();

        assertThrows(ListingNotFoundException.class, () -> service.listing(LISTING, SALE_VIEWER));
        ListingView view = service.listing(LISTING, new ListingViewer(SALE, Set.of("ROLE_SALES_MANAGER")));
        assertEquals(ListingState.ARCHIVED, view.state());
        assertFalse(view.viewerIsAssignedSale());
    }

    @Test
    void listing_twentyOneRevisions_twentyNewestAndOlder() {
        List<ListingRevision> revisions = new ArrayList<>();
        IntStream.rangeClosed(1, 21).forEach(number -> revisions.add(ListingRevision.load(id("01JR" + (100 + number)), number,
                number == 21 ? RevisionStatus.DRAFT : RevisionStatus.REPLACED, SALE, CREATED, CREATED)));
        listings.put(Listing.load(LISTING, ListingCode.of("TD-7K3M-Q9TX"), UNIT, TransactionType.LONG_TERM_RENT,
                AGREEMENT, ListingState.UNPUBLISHED, SALE, CREATED, CREATED, 4L, revisions));
        standing(false, SALE);

        ListingView view = service.listing(LISTING, SALE_VIEWER);

        assertEquals(IntStream.iterate(21, number -> number - 1).limit(20).boxed().toList(),
                view.revisions().stream().map(ListingView.Revision::revisionNo).toList());
        assertTrue(view.hasOlderRevisions());
        assertEquals(4L, view.dataVersion());
        assertTrue(listings.calls().contains("findNewestRevisions " + LISTING + " 20"));
    }

    @Test
    void ofAgreement_assigneeNoListing_mayCreate() {
        standing(false, SALE);
        answer(true, List.of());

        AgreementListingsView view = service.ofAgreement(AGREEMENT, SALE_VIEWER);

        assertEquals(new AgreementListingsView(AGREEMENT, List.of(), false,
                new AgreementListingsView.Creation(true, false, List.of())), view);
        assertEquals(List.of("standings [" + AGREEMENT + "]", "answer " + UNIT + " " + AGREEMENT), rights.calls());
    }

    @Test
    void ofAgreement_notStartedAlone_mayCreateNotStarted() {
        standing(false, SALE);
        answer(false, List.of("NOT_STARTED"));

        AgreementListingsView.Creation creation = service.ofAgreement(AGREEMENT, SALE_VIEWER).creation();

        assertEquals(new AgreementListingsView.Creation(true, true, List.of()), creation);
    }

    @Test
    void ofAgreement_openListing_mayNotCreate() {
        storeOpenListing();
        standing(false, SALE);
        answer(true, List.of());

        AgreementListingsView view = service.ofAgreement(AGREEMENT, SALE_VIEWER);

        assertEquals(List.of(new AgreementListingsView.Item(LISTING, "TD-7K3M-Q9TX", ListingState.UNPUBLISHED, CREATED)),
                view.listings());
        assertEquals(new AgreementListingsView.Creation(false, false, List.of()), view.creation());
    }

    @Test
    void ofAgreement_manager_readsMayNotCreate() {
        standing(false, SALE);
        answer(true, List.of());

        for (String role : List.of("ROLE_SALE_LEADER", "ROLE_SALES_MANAGER", "ROLE_ADMIN")) {
            AgreementListingsView view = service.ofAgreement(AGREEMENT, new ListingViewer(OTHER_SALE, Set.of(role)));

            assertEquals(new AgreementListingsView.Creation(false, false, List.of()), view.creation(), role);
        }
        AgreementListingsView suspended = withAnswer(false, List.of("SUSPENDED"),
                new ListingViewer(OTHER_SALE, Set.of("ROLE_SALES_MANAGER")));
        assertEquals(new AgreementListingsView.Creation(false, false, List.of(SUSPENDED)), suspended.creation());
    }

    @Test
    void ofAgreement_otherSaleOrReviewerOnly_notFound() {
        standing(false, SALE);
        answer(true, List.of());

        assertThrows(ListingNotFoundException.class,
                () -> service.ofAgreement(AGREEMENT, new ListingViewer(OTHER_SALE, Set.of("ROLE_SALE"))));
        assertThrows(ListingNotFoundException.class,
                () -> service.ofAgreement(AGREEMENT, new ListingViewer(SALE, Set.of("ROLE_LISTING_REVIEWER"))));
        assertFalse(rights.calls().stream().anyMatch(call -> call.startsWith("answer")));
        assertTrue(listings.calls().isEmpty());
    }

    @Test
    void ofAgreement_malformedOrUnknown_notFound() {
        ListingViewer admin = new ListingViewer(OTHER_SALE, Set.of("ROLE_ADMIN"));

        assertThrows(ListingNotFoundException.class, () -> service.ofAgreement(AGREEMENT.toLowerCase(), admin));
        assertThrows(ListingNotFoundException.class, () -> service.ofAgreement(AGREEMENT.substring(1), admin));
        assertTrue(rights.calls().isEmpty());

        assertThrows(ListingNotFoundException.class, () -> service.ofAgreement(AGREEMENT, admin));
        assertEquals(List.of("standings [" + AGREEMENT + "]"), rights.calls());
        assertTrue(listings.calls().isEmpty());
    }

    @Test
    void ofAgreement_endedAgreement_archivedAndEnded() {
        storeOpenListing();
        standing(true, SALE);
        answer(false, List.of("ENDED"));

        AgreementListingsView view = service.ofAgreement(AGREEMENT, SALE_VIEWER);

        assertEquals(ListingState.ARCHIVED, view.listings().getFirst().state());
        assertEquals(new AgreementListingsView.Creation(false, false, List.of(ENDED)), view.creation());
    }

    @Test
    void ofAgreement_unknownReasonCode_illegalState() {
        standing(false, SALE);
        answer(false, List.of("FUTURE_GROUP"));

        assertThrows(IllegalStateException.class, () -> service.ofAgreement(AGREEMENT, SALE_VIEWER));
    }

    @Test
    void ofAgreement_twentyOneListings_twentyNewestAndOlder() {
        List<String> inOrder = new ArrayList<>();
        for (int index = 0; index < 21; index++) {
            String listingId = id("01JGL" + (10 + index));
            listings.put(Listing.load(listingId, ListingCode.of("TD-2345-67" + ListingCode.ALPHABET.charAt(index)
                            + "9"), UNIT, TransactionType.LONG_TERM_RENT, AGREEMENT,
                    index == 20 ? ListingState.UNPUBLISHED : ListingState.ARCHIVED, SALE, CREATED.plusSeconds(index),
                    CREATED.plusSeconds(index), 0L, List.of(ListingRevision.load(id("01JR" + (10 + index)), 1,
                            RevisionStatus.DRAFT, SALE, CREATED, CREATED))));
            inOrder.add(listingId);
        }
        standing(false, SALE);
        answer(true, List.of());

        AgreementListingsView view = service.ofAgreement(AGREEMENT, SALE_VIEWER);

        assertEquals(inOrder.reversed().subList(0, 20),
                view.listings().stream().map(AgreementListingsView.Item::listingId).toList());
        assertTrue(view.hasOlder());
        assertEquals(ListingState.UNPUBLISHED, view.listings().getFirst().state());
        assertEquals(ListingState.ARCHIVED, view.listings().get(1).state());
        assertFalse(view.creation().viewerMayCreate());
        assertTrue(listings.calls().contains("findNewestByAgreement " + AGREEMENT + " 20"));
    }

    private AgreementListingsView withAnswer(boolean allowed, List<String> groups, ListingViewer viewer) {
        answer(allowed, groups);
        return service.ofAgreement(AGREEMENT, viewer);
    }

    private void storeOpenListing() {
        listings.put(Listing.create(LISTING, ListingCode.of("TD-7K3M-Q9TX"), UNIT, AGREEMENT, SALE, CREATED,
                id("01JRV"), id("01JHS")));
    }

    private void standing(boolean ended, String assignee) {
        rights.standing(new AgreementStandingFacts(AGREEMENT, UNIT, "LONG_TERM_RENT", ended, assignee));
    }

    private void answer(boolean allowed, List<String> groups) {
        rights.answer(new AdvertisingAnswerFacts(UNIT, AGREEMENT, groups.contains("ENDED"), allowed, groups));
    }

    /** A well-formed id: the head, then zeros to 26 characters. */
    private static String id(String head) {
        return (head + "0".repeat(26)).substring(0, 26);
    }
}
