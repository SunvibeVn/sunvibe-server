package vn.sunvibe.listing.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.query.ReviewQueueView;
import vn.sunvibe.listing.core.application.query.RevisionDecisionView;
import vn.sunvibe.listing.core.application.query.RevisionReviewView;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingReviewFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementTermsFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitAvailabilityFacts;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;
import vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup;
import vn.sunvibe.listing.core.domain.model.listing.ChangeReason;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessSummary;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.RejectionGroup;
import vn.sunvibe.listing.core.domain.model.listing.ReviewCheck;
import vn.sunvibe.listing.core.domain.model.listing.ReviewIneligibility;
import vn.sunvibe.listing.core.domain.model.listing.ReviewNote;
import vn.sunvibe.listing.core.domain.model.listing.ReviewOutcome;
import vn.sunvibe.listing.core.domain.model.listing.RevisionDecision;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;
import vn.sunvibe.listing.core.domain.model.listing.TermsMatch;
import vn.sunvibe.listing.core.domain.model.listing.TermsSource;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;
import vn.sunvibe.listing.core.domain.model.listing.UnitAvailability;
import vn.sunvibe.listing.core.domain.policy.listing.FreshnessPolicy;
import vn.sunvibe.listing.core.domain.repository.ReviewCandidate;
import vn.sunvibe.listing.core.testsupport.InMemoryListingRepository;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;
import vn.sunvibe.listing.core.testsupport.StubAdvertisingRightPort;
import vn.sunvibe.listing.core.testsupport.StubUnitInventoryPort;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CODE;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CREATED;

/**
 * The review's reads over the in-memory listings and E05's and property's stubs (listing-publish US-4 AC-1, AC-2, AC-4 to
 * AC-6; E06-S4 spec sections 3.1, 3.2, 3.5, E06S4-D2, D10, D11, D15; plan E06S4-P3, P5, P6, P7; RV-1, RV-4, RV-9 service
 * half): the queue's chunks, order, cap and reasons; the review read's every field and never the location; the decision
 * read for the listing's readers only; nothing written.
 */
class ListingReviewQueryServiceTest {

    private static final String LISTING = "01LISTING00000000000000001";
    private static final String UNKNOWN = "01LISTING00000000000000009";
    private static final String AGREEMENT = "01AGREEMENT000000000000001";
    private static final String UNIT = "01UNIT00000000000000000001";
    private static final String SALE = "01SALE00000000000000000001";
    private static final String OTHER_SALE = "01SALE00000000000000000002";
    private static final String REVIEWER = "01REVIEWER0000000000000001";
    private static final String MANAGER = "01MANAGER00000000000000001";
    private static final String A_OPEN = "01AGREEMENT000000000000011";
    private static final String A_ENDED = "01AGREEMENT000000000000012";
    private static final String A_GONE = "01AGREEMENT000000000000013";
    private static final String A_MINE = "01AGREEMENT000000000000014";
    private static final long VERSION = 7L;
    private static final Instant SUBMITTED = Instant.parse("2026-11-20T01:00:00.000001Z");
    private static final Instant DECIDED_AT = Instant.parse("2026-11-21T04:30:00.000001Z");
    private static final LocalDate END_DATE = LocalDate.parse("2027-11-30");
    private static final LocalDate PENDING_START = LocalDate.parse("2026-12-01");
    private static final LocalDate CONFIRMED_ON = LocalDate.parse("2020-01-01");

    private static final ListingViewer REVIEWER_VIEWER = new ListingViewer(REVIEWER, Set.of("ROLE_LISTING_REVIEWER"));
    private static final ListingViewer MANAGER_VIEWER = new ListingViewer(MANAGER, Set.of("ROLE_SALES_MANAGER",
            "ROLE_SALE_LEADER"));
    private static final ListingViewer SALE_VIEWER = new ListingViewer(SALE, Set.of("ROLE_SALE"));
    private static final ListingViewer OTHER_SALE_VIEWER = new ListingViewer(OTHER_SALE, Set.of("ROLE_SALE"));
    private static final ListingViewer CUSTOMER_VIEWER = new ListingViewer(SALE, Set.of("ROLE_CUSTOMER"));

    private InMemoryListingRepository listings = new InMemoryListingRepository();
    private StubAdvertisingRightPort rights = new StubAdvertisingRightPort();
    private StubUnitInventoryPort units = new StubUnitInventoryPort();

    private ListingReviewQueryService service() {
        return new ListingReviewQueryService(listings, rights, units);
    }

    @Test
    void queue_pendingOldestFirst_endedLeftOutReasonsPerRow() {
        standings();
        // stored out of order; two share a submission time and order by revision id
        putCandidate(5, A_OPEN, SUBMITTED.plusSeconds(5), Set.of(SALE));
        putCandidate(2, A_ENDED, SUBMITTED.plusSeconds(1), Set.of(SALE));
        putCandidate(6, A_OPEN, SUBMITTED.plusSeconds(3), Set.of(SALE));
        putCandidate(4, A_GONE, SUBMITTED.plusSeconds(2), Set.of(SALE));
        putCandidate(3, A_OPEN, SUBMITTED.plusSeconds(3), Set.of(SALE, REVIEWER));
        putCandidate(1, A_MINE, SUBMITTED.plusSeconds(4), Set.of(SALE));

        ReviewQueueView view = service().queue(REVIEWER_VIEWER);

        assertEquals(List.of(listingId(3), listingId(6), listingId(1), listingId(5)),
                view.items().stream().map(ReviewQueueView.Item::listingId).toList());
        assertEquals(List.of(List.of(ReviewIneligibility.AUTHOR), List.of(), List.of(ReviewIneligibility.ASSIGNED_SALE),
                List.of()), view.items().stream().map(ReviewQueueView.Item::reasons).toList());
        assertEquals(List.of(false, true, false, true),
                view.items().stream().map(ReviewQueueView.Item::viewerMayReview).toList());
        assertFalse(view.hasMore());
        ReviewQueueView.Item first = view.items().getFirst();
        assertAll(
                () -> assertEquals(CODE.value(), first.code()),
                () -> assertEquals(1, first.revisionNo()),
                () -> assertEquals(SUBMITTED.plusSeconds(3), first.submittedAt()),
                () -> assertEquals("Title 3", first.title()),
                () -> assertEquals(RevisionFixtures.unitSnapshot(), first.unitOrNull()),
                () -> assertEquals("Item[" + listingId(3) + ", 1]", first.toString()));
    }

    @Test
    void queue_fiftyKept_hasMoreOnFiftyFirst() {
        standings();
        for (int index = 1; index <= 51; index++) {
            putCandidate(index, A_OPEN, SUBMITTED.plusSeconds(index), Set.of(SALE));
        }

        ReviewQueueView view = service().queue(REVIEWER_VIEWER);

        assertEquals(50, view.items().size());
        assertEquals(listingId(50), view.items().getLast().listingId());
        assertTrue(view.hasMore());

        listings = new InMemoryListingRepository();
        for (int index = 1; index <= 50; index++) {
            putCandidate(index, A_OPEN, SUBMITTED.plusSeconds(index), Set.of(SALE));
        }

        ReviewQueueView exactlyFifty = service().queue(REVIEWER_VIEWER);

        assertEquals(50, exactlyFifty.items().size());
        assertFalse(exactlyFifty.hasMore());
    }

    @Test
    void queue_fourFullChunks_hasMoreStops() {
        standings();
        for (int index = 1; index <= 1_000; index++) {
            putCandidate(index, A_ENDED, SUBMITTED.plusSeconds(index), Set.of(SALE));
        }

        ReviewQueueView view = service().queue(REVIEWER_VIEWER);

        assertEquals(List.of(), view.items());
        assertTrue(view.hasMore());
        assertEquals(4, calls("findReviewCandidates"));
        assertTrue(listings.calls().stream().filter(call -> call.startsWith("findReviewCandidates"))
                .allMatch(call -> call.endsWith(" 250")), listings.calls().toString());
    }

    @Test
    void queue_shortChunk_stopsWithoutMore() {
        standings();
        for (int index = 1; index <= 10; index++) {
            putCandidate(index, A_OPEN, SUBMITTED.plusSeconds(index), Set.of(SALE));
        }

        ReviewQueueView view = service().queue(REVIEWER_VIEWER);

        assertEquals(10, view.items().size());
        assertFalse(view.hasMore());
        assertEquals(1, calls("findReviewCandidates"));

        // a full chunk of ended rows, then a short one: two reads, no more
        listings = new InMemoryListingRepository();
        for (int index = 1; index <= 260; index++) {
            putCandidate(index, index <= 250 ? A_ENDED : A_OPEN, SUBMITTED.plusSeconds(index), Set.of(SALE));
        }

        ReviewQueueView second = service().queue(REVIEWER_VIEWER);

        assertEquals(10, second.items().size());
        assertEquals(listingId(251), second.items().getFirst().listingId());
        assertFalse(second.hasMore());
        assertEquals(2, calls("findReviewCandidates"));
    }

    @Test
    void queue_oneStandingsAndOneAuthorsReadPerChunk() {
        standings();
        for (int index = 1; index <= 260; index++) {
            String agreement = index <= 10 ? A_OPEN : index <= 250 ? A_ENDED : A_MINE;
            putCandidate(index, agreement, SUBMITTED.plusSeconds(index), Set.of(SALE));
        }

        ReviewQueueView view = service().queue(REVIEWER_VIEWER);

        assertEquals(20, view.items().size());
        assertEquals(List.of("standings [" + A_OPEN + ", " + A_ENDED + "]", "standings [" + A_MINE + "]"),
                rights.calls());
        assertEquals(2, calls("findAuthors"));
        assertEquals(2, calls("findReviewCandidates"));
        assertEquals(List.of(), units.calls());
    }

    @Test
    void queue_managerWithoutReviewerRole_everyRowNotAReviewer() {
        standings();
        putCandidate(1, A_OPEN, SUBMITTED.plusSeconds(1), Set.of(SALE));
        putCandidate(2, A_OPEN, SUBMITTED.plusSeconds(2), Set.of(SALE, MANAGER));

        ReviewQueueView view = service().queue(MANAGER_VIEWER);

        assertEquals(List.of(List.of(ReviewIneligibility.NOT_A_REVIEWER),
                        List.of(ReviewIneligibility.NOT_A_REVIEWER, ReviewIneligibility.AUTHOR)),
                view.items().stream().map(ReviewQueueView.Item::reasons).toList());
        assertTrue(view.items().stream().noneMatch(ReviewQueueView.Item::viewerMayReview));
    }

    @Test
    void queue_viewerOutsideReviewReaders_emptyNothingRead() {
        standings();
        putCandidate(1, A_OPEN, SUBMITTED.plusSeconds(1), Set.of(OTHER_SALE));

        for (ListingViewer viewer : List.of(SALE_VIEWER, CUSTOMER_VIEWER)) {
            ReviewQueueView view = service().queue(viewer);

            assertEquals(List.of(), view.items(), viewer.expandedAuthorities().toString());
            assertFalse(view.hasMore());
        }
        assertEquals(List.of(), rights.calls());
        assertEquals(List.of(), units.calls());
        assertEquals(List.of(), listings.calls());
    }

    @Test
    void review_pendingRevision_everyFieldNoLocation() {
        world(RevisionFixtures.pending(1), ListingState.UNPUBLISHED, standing(false, SALE), Set.of(SALE));
        rights.review(new AdvertisingReviewFacts(UNIT, AGREEMENT, false, false, List.of("SUSPENDED", "OWNER"), END_DATE,
                true, false, snapshotTerms(), PENDING_START, otherTerms()));
        units.availability(new UnitAvailabilityFacts(UNIT, "P101", "OCCUPIED", true));
        listings.putFreshness(LISTING, new FreshnessSummary(CONFIRMED_ON, true), null);
        Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);

        RevisionReviewView view = service().review(LISTING, 1, REVIEWER_VIEWER);

        Instant after = Instant.now();
        assertAll(
                () -> assertEquals(LISTING, view.listingId()),
                () -> assertEquals(CODE.value(), view.code()),
                () -> assertEquals(1, view.revisionNo()),
                () -> assertEquals(RevisionStatus.PENDING_REVIEW, view.status()),
                () -> assertEquals(VERSION, view.dataVersion()),
                () -> assertTrue(view.viewerMayReview()),
                () -> assertEquals(List.of(), view.reasons()),
                () -> assertEquals(TermsMatch.IN_EFFECT, view.termsMatch()),
                () -> assertEquals(new RevisionReviewView.Unit("P101", UnitAvailability.OCCUPIED, true), view.unit()),
                () -> assertEquals(new RevisionReviewView.Freshness(CONFIRMED_ON,
                        FreshnessPolicy.validThrough(CONFIRMED_ON), true, true), view.freshness()),
                () -> assertEquals(ReviewCheck.VERSION_1, view.checklist()),
                () -> assertEquals("RevisionReviewView[" + LISTING + ", 1]", view.toString()));
        RevisionReviewView.Advertising advertising = view.advertisingOrNull();
        assertNotNull(advertising);
        RevisionTerms inEffect = advertising.termsInEffectOrNull();
        RevisionTerms pending = advertising.pendingOrNull();
        assertAll(
                () -> assertFalse(advertising.allowed()),
                () -> assertFalse(advertising.ended()),
                () -> assertEquals(List.of(AgreementReasonGroup.SUSPENDED, AgreementReasonGroup.OWNER),
                        advertising.reasonGroups()),
                () -> assertEquals(END_DATE, advertising.endDateOrNull()),
                () -> assertTrue(advertising.interiorPhotos()),
                () -> assertFalse(advertising.exteriorPhotos()),
                () -> assertEquals(PENDING_START, advertising.pendingStartDateOrNull()),
                () -> assertEquals(TermsSource.IN_EFFECT, inEffect.source()),
                () -> assertNull(inEffect.pendingStartDateOrNull()),
                () -> assertEquals(8_000_000L, inEffect.monthlyRent()),
                () -> assertEquals(RevisionFixtures.inEffectTerms().fees(), inEffect.fees()),
                () -> assertFalse(inEffect.capturedAt().isBefore(before)),
                () -> assertFalse(inEffect.capturedAt().isAfter(after)),
                () -> assertEquals(TermsSource.PENDING_START, pending.source()),
                () -> assertEquals(PENDING_START, pending.pendingStartDateOrNull()),
                () -> assertEquals(9_000_000L, pending.monthlyRent()),
                () -> assertEquals("Advertising[hidden]", advertising.toString()));
        // never the real location: no address read
        assertEquals(List.of("availability [" + UNIT + "]"), units.calls());
        assertTrue(rights.calls().contains("review " + UNIT + " " + AGREEMENT), rights.calls().toString());
    }

    @Test
    void review_decidedOrWithdrawn_notPendingReason() {
        for (RevisionStatus status : List.of(RevisionStatus.NEEDS_CHANGES, RevisionStatus.REJECTED,
                RevisionStatus.APPROVED, RevisionStatus.DRAFT, RevisionStatus.LIVE, RevisionStatus.REPLACED)) {
            world(RevisionFixtures.withStatus(1, status), ListingState.UNPUBLISHED, standing(false, SALE),
                    Set.of(SALE));

            RevisionReviewView view = service().review(LISTING, 1, REVIEWER_VIEWER);

            assertEquals(status, view.status());
            assertEquals(List.of(ReviewIneligibility.NOT_PENDING), view.reasons(), status.name());
            assertFalse(view.viewerMayReview(), status.name());
        }
    }

    @Test
    void review_endedOrAbsentStanding_listingArchivedReason() {
        world(RevisionFixtures.pending(1), ListingState.UNPUBLISHED, standing(true, SALE), Set.of(SALE));
        assertEquals(List.of(ReviewIneligibility.LISTING_ARCHIVED),
                service().review(LISTING, 1, REVIEWER_VIEWER).reasons(), "ended");

        // a standing E05 no longer returns reads as ended, with no assigned Sale (plan E06S4-P3)
        world(RevisionFixtures.pending(1), ListingState.UNPUBLISHED, null, Set.of(SALE));
        RevisionReviewView absent = service().review(LISTING, 1, REVIEWER_VIEWER);
        assertEquals(List.of(ReviewIneligibility.LISTING_ARCHIVED), absent.reasons(), "absent");
        assertFalse(absent.viewerMayReview());

        world(RevisionFixtures.pending(1), ListingState.ARCHIVED, standing(false, SALE), Set.of(SALE));
        assertEquals(List.of(ReviewIneligibility.LISTING_ARCHIVED),
                service().review(LISTING, 1, REVIEWER_VIEWER).reasons(), "stored");
    }

    @Test
    void review_reasonsBeyondQueue_contractOrder() {
        // a decided revision of an ended agreement read by its author who is the assignee
        world(RevisionFixtures.withStatus(1, RevisionStatus.REJECTED), ListingState.UNPUBLISHED,
                standing(true, REVIEWER), Set.of(REVIEWER));

        assertEquals(List.of(ReviewIneligibility.AUTHOR, ReviewIneligibility.ASSIGNED_SALE,
                        ReviewIneligibility.NOT_PENDING, ReviewIneligibility.LISTING_ARCHIVED),
                service().review(LISTING, 1, REVIEWER_VIEWER).reasons());
        assertEquals(List.of(ReviewIneligibility.NOT_A_REVIEWER, ReviewIneligibility.AUTHOR,
                        ReviewIneligibility.ASSIGNED_SALE, ReviewIneligibility.NOT_PENDING,
                        ReviewIneligibility.LISTING_ARCHIVED),
                service().review(LISTING, 1, new ListingViewer(REVIEWER, Set.of("ROLE_SALE_LEADER"))).reasons());
    }

    @Test
    void review_unknownReasonGroupOrAvailability_illegalState() {
        world(RevisionFixtures.pending(1), ListingState.UNPUBLISHED, standing(false, SALE), Set.of(SALE));
        rights.review(new AdvertisingReviewFacts(UNIT, AGREEMENT, false, false, List.of("SOMETHING_NEW"), null, true,
                true, snapshotTerms(), null, null));
        units.availability(new UnitAvailabilityFacts(UNIT, "P101", "AVAILABLE", false));

        assertThrows(IllegalStateException.class, () -> service().review(LISTING, 1, REVIEWER_VIEWER), "group");

        world(RevisionFixtures.pending(1), ListingState.UNPUBLISHED, standing(false, SALE), Set.of(SALE));
        rights.review(new AdvertisingReviewFacts(UNIT, AGREEMENT, false, true, List.of(), null, true, true,
                snapshotTerms(), null, null));
        units.availability(new UnitAvailabilityFacts(UNIT, "P101", "RESERVED", false));

        assertThrows(IllegalStateException.class, () -> service().review(LISTING, 1, REVIEWER_VIEWER),
                "availability");
    }

    @Test
    void review_noE05Answer_advertisingNullTermsNone() {
        world(RevisionFixtures.pending(1), ListingState.UNPUBLISHED, standing(false, SALE), Set.of(SALE));

        RevisionReviewView view = service().review(LISTING, 1, REVIEWER_VIEWER);

        assertNull(view.advertisingOrNull());
        assertEquals(TermsMatch.NONE, view.termsMatch());
        // property answers no unit: no availability
        assertEquals(new RevisionReviewView.Unit(null, null, false), view.unit());
        assertEquals(new RevisionReviewView.Freshness(null, null, false, null), view.freshness());
    }

    @Test
    void review_noSnapshot_noSnapshotMatch() {
        world(RevisionFixtures.draft(1), ListingState.UNPUBLISHED, standing(false, SALE), Set.of(SALE));
        rights.review(new AdvertisingReviewFacts(UNIT, AGREEMENT, false, true, List.of(), null, true, true,
                snapshotTerms(), null, null));

        assertEquals(TermsMatch.NO_SNAPSHOT, service().review(LISTING, 1, REVIEWER_VIEWER).termsMatch());
    }

    @Test
    void review_unknownListingOrRevision_notFound() {
        world(RevisionFixtures.pending(1), ListingState.UNPUBLISHED, standing(false, SALE), Set.of(SALE));

        assertAll(
                () -> assertThrows(ListingNotFoundException.class,
                        () -> service().review("not-an-id", 1, REVIEWER_VIEWER)),
                () -> assertThrows(ListingNotFoundException.class, () -> service().review(null, 1, REVIEWER_VIEWER)),
                () -> assertThrows(ListingNotFoundException.class, () -> service().review(UNKNOWN, 1, REVIEWER_VIEWER)),
                () -> assertThrows(ListingNotFoundException.class,
                        () -> service().review(LISTING, null, REVIEWER_VIEWER)),
                () -> assertThrows(ListingNotFoundException.class,
                        () -> service().review(LISTING, 9, REVIEWER_VIEWER)));
        assertEquals(List.of(), units.calls());
    }

    @Test
    void review_viewerOutsideReviewReaders_notFoundNothingRead() {
        world(RevisionFixtures.pending(1), ListingState.UNPUBLISHED, standing(false, SALE), Set.of(OTHER_SALE));
        rights.review(new AdvertisingReviewFacts(UNIT, AGREEMENT, false, true, List.of(), null, true, true,
                snapshotTerms(), null, null));
        units.availability(new UnitAvailabilityFacts(UNIT, "P101", "AVAILABLE", false));

        for (ListingViewer viewer : List.of(SALE_VIEWER, CUSTOMER_VIEWER)) {
            assertThrows(ListingNotFoundException.class, () -> service().review(LISTING, 1, viewer),
                    viewer.expandedAuthorities().toString());
        }
        assertEquals(List.of(), rights.calls());
        assertEquals(List.of(), units.calls());
        assertEquals(List.of(), listings.calls());
    }

    @Test
    void decision_readersInScope_answered() {
        world(RevisionFixtures.pending(1), ListingState.UNPUBLISHED, standing(false, SALE), Set.of(SALE));
        decided(RevisionDecision.needsChanges("R1", 1, List.of(ChangeReason.TERMS_MISMATCH,
                ChangeReason.MISSING_INFORMATION), ReviewNote.ofOrNull("Thiếu ảnh phòng tắm."), REVIEWER, DECIDED_AT));
        RevisionDecisionView expected = new RevisionDecisionView(ReviewOutcome.NEEDS_CHANGES, DECIDED_AT, null,
                List.of(ChangeReason.MISSING_INFORMATION, ChangeReason.TERMS_MISMATCH), null, "Thiếu ảnh phòng tắm.");

        for (ListingViewer reader : List.of(REVIEWER_VIEWER, MANAGER_VIEWER, SALE_VIEWER)) {
            assertEquals(expected, service().decision(LISTING, 1, reader), reader.userId());
        }
        assertEquals("RevisionDecisionView[NEEDS_CHANGES]", expected.toString());

        world(RevisionFixtures.pending(1), ListingState.UNPUBLISHED, standing(false, SALE), Set.of(SALE));
        decided(RevisionDecision.approved("R1", 1, ReviewCheck.VERSION_1, REVIEWER, DECIDED_AT));

        assertEquals(new RevisionDecisionView(ReviewOutcome.APPROVED, DECIDED_AT, 1, List.of(), null, null),
                service().decision(LISTING, 1, SALE_VIEWER));
    }

    @Test
    void decision_saleNotAssigneeUnknownOrUndecided_notFound() {
        world(RevisionFixtures.pending(1), ListingState.UNPUBLISHED, standing(false, SALE), Set.of(SALE));

        // an undecided revision answers the one "not found" to every reader
        assertThrows(ListingNotFoundException.class, () -> service().decision(LISTING, 1, REVIEWER_VIEWER));

        decided(RevisionDecision.rejected("R1", 1, RejectionGroup.WRONG_UNIT, ReviewNote.ofOrNull("Sai căn."), REVIEWER,
                DECIDED_AT));
        assertAll(
                () -> assertThrows(ListingNotFoundException.class,
                        () -> service().decision(LISTING, 1, OTHER_SALE_VIEWER)),
                () -> assertThrows(ListingNotFoundException.class,
                        () -> service().decision(UNKNOWN, 1, REVIEWER_VIEWER)),
                () -> assertThrows(ListingNotFoundException.class,
                        () -> service().decision("not-an-id", 1, REVIEWER_VIEWER)),
                () -> assertThrows(ListingNotFoundException.class,
                        () -> service().decision(LISTING, 2, REVIEWER_VIEWER)),
                () -> assertThrows(ListingNotFoundException.class,
                        () -> service().decision(LISTING, null, REVIEWER_VIEWER)));

        // a standing E05 no longer returns has no assignee: the former assignee reads nothing
        rights = new StubAdvertisingRightPort();
        assertThrows(ListingNotFoundException.class, () -> service().decision(LISTING, 1, SALE_VIEWER));
        assertEquals(ReviewOutcome.REJECTED, service().decision(LISTING, 1, MANAGER_VIEWER).outcome());
    }

    @Test
    void queries_writeNothing() {
        world(RevisionFixtures.pending(1), ListingState.UNPUBLISHED, standing(false, SALE), Set.of(SALE));
        rights.review(new AdvertisingReviewFacts(UNIT, AGREEMENT, false, true, List.of(), END_DATE, true, true,
                snapshotTerms(), null, null));
        units.availability(new UnitAvailabilityFacts(UNIT, "P101", "AVAILABLE", false));
        rights.standing(new AgreementStandingFacts(A_OPEN, UNIT, "LONG_TERM_RENT", false, SALE));
        putCandidate(2, A_OPEN, SUBMITTED, Set.of(SALE));
        decided(RevisionDecision.approved("R1", 1, ReviewCheck.VERSION_1, REVIEWER, DECIDED_AT));
        int callsBefore = listings.calls().size();
        Listing storedBefore = listings.stored(LISTING).orElseThrow();

        service().queue(REVIEWER_VIEWER);
        service().review(LISTING, 1, REVIEWER_VIEWER);
        service().decision(LISTING, 1, REVIEWER_VIEWER);

        List<String> calls = listings.calls().subList(callsBefore, listings.calls().size());
        assertTrue(calls.stream().noneMatch(call -> call.startsWith("update") || call.startsWith("insert")
                || call.startsWith("recordFreshness")), calls.toString());
        assertEquals(1, listings.decisions().size());
        assertEquals(storedBefore.rowVersion(), listings.stored(LISTING).orElseThrow().rowVersion());
        assertTrue(rights.calls().stream().allMatch(call -> call.startsWith("standings") || call.startsWith("review")),
                rights.calls().toString());
        assertTrue(units.calls().stream().allMatch(call -> call.startsWith("availability")), units.calls().toString());
    }

    /** E05's standings of the queue tests: open (assignee SALE), ended, mine (assignee REVIEWER); A_GONE has none. */
    private void standings() {
        rights.standing(new AgreementStandingFacts(A_OPEN, UNIT, "LONG_TERM_RENT", false, SALE));
        rights.standing(new AgreementStandingFacts(A_ENDED, UNIT, "LONG_TERM_RENT", true, SALE));
        rights.standing(new AgreementStandingFacts(A_MINE, UNIT, "LONG_TERM_RENT", false, REVIEWER));
    }

    /** A Pending review candidate of its own listing, revision 1, with its authors. */
    private void putCandidate(int index, String agreementId, Instant submittedAt, Set<String> authors) {
        listings.putReviewCandidate(new ReviewCandidate(listingId(index), CODE.value(), agreementId,
                revisionId(index), 1, submittedAt, "Title " + index, RevisionFixtures.unitSnapshot()));
        listings.putAuthors(revisionId(index), authors);
    }

    private static String listingId(int index) {
        return "01LISTING" + String.format("%017d", index);
    }

    private static String revisionId(int index) {
        return "01REVISION" + String.format("%016d", index);
    }

    private long calls(String method) {
        return listings.calls().stream().filter(call -> call.startsWith(method + " ")).count();
    }

    /** A fresh world: the listing at row version 7 in the state given, E05's standing or none, the revision's authors. */
    private void world(ListingRevision revision, ListingState state, AgreementStandingFacts standingOrNull,
                       Set<String> authors) {
        listings = new InMemoryListingRepository();
        rights = new StubAdvertisingRightPort();
        units = new StubUnitInventoryPort();
        List<ListingRevision> revisions = new ArrayList<>();
        for (int number = 1; number < revision.revisionNo(); number++) {
            revisions.add(RevisionFixtures.withStatus(number, RevisionStatus.CANCELLED));
        }
        revisions.add(revision);
        listings.put(Listing.load(LISTING, CODE, UNIT, TransactionType.LONG_TERM_RENT, AGREEMENT, state, SALE, CREATED,
                CREATED, VERSION, revisions));
        listings.putAuthors(revision.id(), authors);
        if (standingOrNull != null) {
            rights.standing(standingOrNull);
        }
    }

    private static AgreementStandingFacts standing(boolean ended, String assignee) {
        return new AgreementStandingFacts(AGREEMENT, UNIT, "LONG_TERM_RENT", ended, assignee);
    }

    /** The decision stored as the decide command stores it, through the aggregate and the repository's update. */
    private void decided(RevisionDecision decision) {
        Listing working = listings.findForReview(LISTING, decision.revisionNo()).orElseThrow();
        working.decide(decision.revisionNo(), decision, "01HISTORY00000000000000001", "01HISTORY00000000000000002",
                decision.decidedAt());
        listings.update(working);
    }

    /** E05's terms equal to RevisionFixtures.inEffectTerms(), as E05 sends them. */
    private static AgreementTermsFacts.Terms snapshotTerms() {
        return new AgreementTermsFacts.Terms(8_000_000L, "MONTHS", 2, null, 6, 1, List.of(
                new AgreementTermsFacts.Fee("Điện", "AMOUNT", 3_500L, "PER_KWH"),
                new AgreementTermsFacts.Fee("Internet", "INCLUDED", null, null),
                new AgreementTermsFacts.Fee("Nước", "PROVIDER_TARIFF", null, null)));
    }

    /** Terms whose rent differs from the snapshot's. */
    private static AgreementTermsFacts.Terms otherTerms() {
        return new AgreementTermsFacts.Terms(9_000_000L, "MONTHS", 2, null, 6, 1, List.of(
                new AgreementTermsFacts.Fee("Điện", "AMOUNT", 3_500L, "PER_KWH")));
    }
}
