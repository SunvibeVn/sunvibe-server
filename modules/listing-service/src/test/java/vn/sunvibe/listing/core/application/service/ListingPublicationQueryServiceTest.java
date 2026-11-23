package vn.sunvibe.listing.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.query.ListingPublicationView;
import vn.sunvibe.listing.core.application.query.LiveDifferenceView;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingReviewFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitAvailabilityFacts;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;
import vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup;
import vn.sunvibe.listing.core.domain.model.listing.ComparedPart;
import vn.sunvibe.listing.core.domain.model.listing.DisplayReason;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessSummary;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.RevisionContent;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;
import vn.sunvibe.listing.core.domain.model.listing.TermsSource;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;
import vn.sunvibe.listing.core.domain.policy.listing.FreshnessPolicy;
import vn.sunvibe.listing.core.testsupport.InMemoryListingRepository;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;
import vn.sunvibe.listing.core.testsupport.StubAdvertisingRightPort;
import vn.sunvibe.listing.core.testsupport.StubUnitInventoryPort;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CODE;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CREATED;

/**
 * The publication read and the live difference read (listing-publish US-2 AC-7, US-4 AC-2, US-5 AC-3, BR-11; E06-S6 spec
 * E06S6-D2, D11, D12; PL-11, PL-12 service halves): the live revision answered by the rule of a shown revision, the
 * approved one by the rule of a put live, each with its reasons and E05's groups; the permissions; an archived listing
 * read with nothing beyond its standing; no lock; the difference to the live revision in its three cases.
 */
class ListingPublicationQueryServiceTest {

    private static final String LISTING = "01JFQ9P0000000000000000001";
    private static final String UNKNOWN = "01JFQ9P0000000000000000009";
    private static final String AGREEMENT = "01AGREEMENT000000000000001";
    private static final String UNIT = "01UNIT00000000000000000001";
    private static final String SALE = "01SALE00000000000000000001";
    private static final long VERSION = 7L;
    private static final ListingViewer SALE_VIEWER = new ListingViewer(SALE, Set.of("ROLE_SALE"));
    private static final ListingViewer OTHER_SALE = new ListingViewer("01SALE00000000000000000002", Set.of("ROLE_SALE"));
    private static final ListingViewer LEADER = new ListingViewer("01LEADER000000000000000001",
            Set.of("ROLE_SALE_LEADER", "ROLE_SALE"));
    private static final ListingViewer REVIEWER = new ListingViewer("01REVIEWER0000000000000001",
            Set.of("ROLE_LISTING_REVIEWER"));
    private static final ListingPublicationView.DisplayAnswer HOLDS =
            new ListingPublicationView.DisplayAnswer(true, List.of(), List.of());

    private InMemoryListingRepository listings = new InMemoryListingRepository();
    private StubAdvertisingRightPort rights = new StubAdvertisingRightPort();
    private StubUnitInventoryPort units = new StubUnitInventoryPort();

    private ListingPublicationQueryService service() {
        return new ListingPublicationQueryService(listings, rights, new DisplayConditionsAssembler(rights, units,
                listings));
    }

    @Test
    void publication_liveAndWaiting_answersAndPermissions() {
        // the live revision holds; the waiting one was approved on the version waiting to start, so its terms differ
        world(ListingState.PUBLISHED, false, live(1), approved(2, pendingStartTerms()));

        ListingPublicationView view = service().publication(LISTING, SALE_VIEWER);

        assertEquals(new ListingPublicationView(LISTING, ListingState.PUBLISHED, VERSION,
                new ListingPublicationView.Live(1, CREATED, HOLDS),
                new ListingPublicationView.Approved(2, TermsSource.PENDING_START,
                        new ListingPublicationView.DisplayAnswer(false, List.of(DisplayReason.TERMS_NOT_IN_EFFECT),
                                List.of())),
                true, true), view);

        // E05 suspends the advertising right: both answers name it with E05's group
        rights.review(review(false, List.of(AgreementReasonGroup.SUSPENDED.name())));
        ListingPublicationView suspended = service().publication(LISTING, SALE_VIEWER);
        assertAll(
                () -> assertEquals(new ListingPublicationView.DisplayAnswer(false,
                        List.of(DisplayReason.ADVERTISING_RIGHT), List.of(AgreementReasonGroup.SUSPENDED)),
                        suspended.liveOrNull().display()),
                () -> assertEquals(new ListingPublicationView.DisplayAnswer(false,
                        List.of(DisplayReason.ADVERTISING_RIGHT, DisplayReason.TERMS_NOT_IN_EFFECT),
                        List.of(AgreementReasonGroup.SUSPENDED)), suspended.approvedOrNull().display()));

        // the live revision is shown only on a Published listing; an approved one may go live on an Unpublished one
        world(ListingState.UNPUBLISHED, false, live(1), approved(2, RevisionFixtures.inEffectTerms()));
        ListingPublicationView unpublished = service().publication(LISTING, SALE_VIEWER);
        assertAll(
                () -> assertEquals(new ListingPublicationView.DisplayAnswer(false, List.of(DisplayReason.LISTING_STATE),
                        List.of()), unpublished.liveOrNull().display()),
                () -> assertEquals(HOLDS, unpublished.approvedOrNull().display()),
                () -> assertEquals(TermsSource.IN_EFFECT, unpublished.approvedOrNull().termsSource()));

        // a Paused listing: neither may show, no put live; the copy stays the drafter's
        world(ListingState.PAUSED, false, live(1), approved(2, RevisionFixtures.inEffectTerms()));
        ListingPublicationView paused = service().publication(LISTING, SALE_VIEWER);
        assertAll(
                () -> assertEquals(List.of(DisplayReason.LISTING_STATE), paused.liveOrNull().display().reasons()),
                () -> assertEquals(List.of(DisplayReason.LISTING_STATE), paused.approvedOrNull().display().reasons()),
                () -> assertFalse(paused.viewerMayPutLive()),
                () -> assertTrue(paused.viewerMayCopy()));

        // a leader who is not the assignee reads every listing but neither puts live nor copies
        world(ListingState.PUBLISHED, false, live(1), approved(2, RevisionFixtures.inEffectTerms()));
        ListingPublicationView leader = service().publication(LISTING, LEADER);
        assertAll(
                () -> assertFalse(leader.viewerMayPutLive()),
                () -> assertFalse(leader.viewerMayCopy()));

        // an open revision beside them: no copy, the put live stays
        world(ListingState.PUBLISHED, false, live(1), approved(2, RevisionFixtures.inEffectTerms()),
                RevisionFixtures.draft(3));
        ListingPublicationView open = service().publication(LISTING, SALE_VIEWER);
        assertAll(
                () -> assertTrue(open.viewerMayPutLive()),
                () -> assertFalse(open.viewerMayCopy()));
    }

    @Test
    void publication_noLiveNoWaiting_nulls() {
        world(ListingState.UNPUBLISHED, false, RevisionFixtures.withStatus(1, RevisionStatus.NEEDS_CHANGES));

        ListingPublicationView view = service().publication(LISTING, SALE_VIEWER);

        assertEquals(new ListingPublicationView(LISTING, ListingState.UNPUBLISHED, VERSION, null, null, false, true),
                view);
    }

    @Test
    void publication_archivedStoredOrEnded_archivedNoE05TermsOrUnitRead() {
        for (boolean storedArchived : List.of(true, false)) {
            world(storedArchived ? ListingState.ARCHIVED : ListingState.PUBLISHED, !storedArchived, live(1),
                    approved(2, RevisionFixtures.inEffectTerms()));

            ListingPublicationView view = service().publication(LISTING, SALE_VIEWER);

            assertAll("stored archived " + storedArchived,
                    () -> assertEquals(new ListingPublicationView(LISTING, ListingState.ARCHIVED, VERSION, null, null,
                            false, false), view),
                    () -> assertEquals(List.of("findRow " + LISTING), listings.calls()),
                    () -> assertEquals(List.of("standings [" + AGREEMENT + "]"), rights.calls()),
                    () -> assertEquals(List.of(), units.calls()));
        }
    }

    @Test
    void publication_outOfScope_notFound() {
        world(ListingState.PUBLISHED, false, live(1));

        assertAll(
                () -> assertThrows(ListingNotFoundException.class, () -> service().publication(LISTING, OTHER_SALE)),
                () -> assertThrows(ListingNotFoundException.class, () -> service().publication(UNKNOWN, SALE_VIEWER)),
                () -> assertThrows(ListingNotFoundException.class, () -> service().publication("01", SALE_VIEWER)),
                () -> assertThrows(ListingNotFoundException.class, () -> service().publication(null, SALE_VIEWER)));
        // E05 no longer returns the standing: nobody is the assignee
        rights = new StubAdvertisingRightPort();
        assertThrows(ListingNotFoundException.class, () -> service().publication(LISTING, SALE_VIEWER));
        assertFalse(rights.calls().stream().anyMatch(call -> call.startsWith("review")));
    }

    @Test
    void publication_readsWithoutLock() {
        world(ListingState.PUBLISHED, false, live(1), approved(2, RevisionFixtures.inEffectTerms()));

        service().publication(LISTING, SALE_VIEWER);

        assertAll(
                () -> assertFalse(listings.calls().stream().anyMatch(call -> call.startsWith("lockRow")),
                        listings.calls().toString()),
                () -> assertFalse(rights.calls().stream().anyMatch(call -> call.startsWith("answerUnderShareLock")
                        || call.startsWith("terms")), rights.calls().toString()),
                () -> assertTrue(rights.calls().contains("review " + UNIT + " " + AGREEMENT)));
    }

    @Test
    void liveDifference_compared_changedPartsOnly() {
        RevisionContent live = RevisionFixtures.completeContent();
        RevisionContent retitled = new RevisionContent("Phòng trọ mới, gần biển", live.descriptionOrNull(),
                live.features());
        // the same terms values captured later and from the version waiting to start: no change of the terms
        RevisionTerms recaptured = new RevisionTerms(TermsSource.PENDING_START, LocalDate.parse("2026-12-01"),
                Instant.parse("2026-11-21T02:00:00Z"), 8_000_000L, RevisionFixtures.inEffectTerms().deposit(), 6, 1,
                RevisionFixtures.inEffectTerms().fees());
        world(ListingState.PUBLISHED, false, live(1), ListingRevision.load("R2", 2, RevisionStatus.PENDING_REVIEW, SALE,
                CREATED, CREATED, retitled, recaptured, RevisionFixtures.unitSnapshot(), CREATED, SALE));

        LiveDifferenceView view = service().liveDifference(LISTING, 2, REVIEWER);

        assertEquals(new LiveDifferenceView(2, LiveDifferenceView.Comparison.COMPARED,
                new LiveDifferenceView.LiveRevision(1, CREATED, live, RevisionFixtures.inEffectTerms()),
                List.of(ComparedPart.TITLE)), view);
    }

    @Test
    void liveDifference_noLive_noLiveRevision() {
        world(ListingState.UNPUBLISHED, false, approved(1, RevisionFixtures.inEffectTerms()));

        assertEquals(new LiveDifferenceView(1, LiveDifferenceView.Comparison.NO_LIVE_REVISION, null, List.of()),
                service().liveDifference(LISTING, 1, REVIEWER));
    }

    @Test
    void liveDifference_isLive_isLiveRevision() {
        world(ListingState.PUBLISHED, false, live(1));

        assertEquals(new LiveDifferenceView(1, LiveDifferenceView.Comparison.IS_LIVE_REVISION, null, List.of()),
                service().liveDifference(LISTING, 1, REVIEWER));
    }

    @Test
    void liveDifference_unknownRevision_notFound() {
        world(ListingState.PUBLISHED, false, live(1));

        assertAll(
                () -> assertThrows(ListingNotFoundException.class, () -> service().liveDifference(LISTING, 9, REVIEWER)),
                () -> assertThrows(ListingNotFoundException.class,
                        () -> service().liveDifference(LISTING, null, REVIEWER)),
                () -> assertThrows(ListingNotFoundException.class,
                        () -> service().liveDifference(UNKNOWN, 1, REVIEWER)),
                () -> assertThrows(ListingNotFoundException.class, () -> service().liveDifference("01", 1, REVIEWER)));
        // a SALE reads no review, even of the listing it holds: the one "not found" before any read
        world(ListingState.PUBLISHED, false, live(1));
        assertThrows(ListingNotFoundException.class, () -> service().liveDifference(LISTING, 1, SALE_VIEWER));
        assertEquals(List.of(), listings.calls());
    }

    @Test
    void liveDifference_liveWithoutTerms_illegalState() {
        world(ListingState.PUBLISHED, false, ListingRevision.load("R1", 1, RevisionStatus.LIVE, SALE, CREATED, CREATED,
                RevisionFixtures.completeContent(), null, RevisionFixtures.unitSnapshot(), CREATED, SALE, null, CREATED,
                null), RevisionFixtures.pending(2));

        assertThrows(IllegalStateException.class, () -> service().liveDifference(LISTING, 2, REVIEWER));
    }

    /**
     * A fresh world: the listing at version 7 in the stored state given with the revisions given; E05's standing ended
     * or not naming {@code SALE}; E05's review answer allowing with terms equal to the snapshot's; the unit available; a
     * valid confirmation today.
     */
    private void world(ListingState state, boolean ended, ListingRevision... revisions) {
        listings = new InMemoryListingRepository();
        rights = new StubAdvertisingRightPort();
        units = new StubUnitInventoryPort();
        listings.put(Listing.load(LISTING, CODE, UNIT, TransactionType.LONG_TERM_RENT, AGREEMENT, state, SALE, CREATED,
                CREATED, VERSION, List.of(revisions)));
        listings.putFreshness(LISTING, new FreshnessSummary(FreshnessPolicy.today(Instant.now()), true), null);
        rights.standing(new AgreementStandingFacts(AGREEMENT, UNIT, "LONG_TERM_RENT", ended, SALE));
        rights.review(review(true, List.of()));
        units.availability(new UnitAvailabilityFacts(UNIT, "P101", "AVAILABLE", false));
    }

    private static AdvertisingReviewFacts review(boolean allowed, List<String> groups) {
        return new AdvertisingReviewFacts(UNIT, AGREEMENT, false, allowed, groups, null, true, true,
                DisplayConditionsAssemblerTest.snapshotTerms(), null, null);
    }

    /** Terms approved on the version waiting to start, whose rent differs from the version in effect. */
    private static RevisionTerms pendingStartTerms() {
        RevisionTerms inEffect = RevisionFixtures.inEffectTerms();
        return new RevisionTerms(TermsSource.PENDING_START, LocalDate.parse("2026-12-01"), inEffect.capturedAt(),
                9_000_000L, inEffect.deposit(), inEffect.minimumTermMonths(), inEffect.paymentPeriodMonths(),
                inEffect.fees());
    }

    private static ListingRevision approved(int revisionNo, RevisionTerms terms) {
        return ListingRevision.load("R" + revisionNo, revisionNo, RevisionStatus.APPROVED, SALE, CREATED, CREATED,
                RevisionFixtures.completeContent(), terms, RevisionFixtures.unitSnapshot(), CREATED, SALE);
    }

    private static ListingRevision live(int revisionNo) {
        return ListingRevision.load("R" + revisionNo, revisionNo, RevisionStatus.LIVE, SALE, CREATED, CREATED,
                RevisionFixtures.completeContent(), RevisionFixtures.inEffectTerms(), RevisionFixtures.unitSnapshot(),
                CREATED, SALE, null, CREATED, null);
    }
}
