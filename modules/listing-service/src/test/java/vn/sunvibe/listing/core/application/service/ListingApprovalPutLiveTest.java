package vn.sunvibe.listing.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.application.command.RevisionDecisionCommand;
import vn.sunvibe.listing.core.application.query.RevisionCommandResult;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingAnswerFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementTermsFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitAvailabilityFacts;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;
import vn.sunvibe.listing.core.domain.exception.ListingChangedException;
import vn.sunvibe.listing.core.domain.model.listing.ChangeReason;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessSummary;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryAction;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.RejectionGroup;
import vn.sunvibe.listing.core.domain.model.listing.ReviewCheck;
import vn.sunvibe.listing.core.domain.model.listing.ReviewOutcome;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;
import vn.sunvibe.listing.core.domain.policy.listing.FreshnessPolicy;
import vn.sunvibe.listing.core.testsupport.InMemoryListingAccessLogRepository;
import vn.sunvibe.listing.core.testsupport.InMemoryListingRepository;
import vn.sunvibe.listing.core.testsupport.RecordingEventOutboxPort;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;
import vn.sunvibe.listing.core.testsupport.StubAdvertisingRightPort;
import vn.sunvibe.listing.core.testsupport.StubUnitInventoryPort;
import vn.sunvibe.shared.outbox.ListingChangedEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CODE;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CREATED;

/**
 * The approval's try to put live (listing-publish US-5 AC-1, AC-3 moment 1; E06-S6 spec E06S6-D7; PL-3 service half):
 * only an approval on an Unpublished or Published listing tries; its version compared with the locked row, E05 read
 * under its share lock; went live: status LIVE, the history of the approval then the put live without actor, one event;
 * reasons: APPROVED and no event; an unknown E05 group or availability code fails the whole decision.
 */
class ListingApprovalPutLiveTest {

    /** A ULID: the listing event takes nothing else. */
    private static final String LISTING = "01JFQ9P0000000000000000001";
    private static final String AGREEMENT = "01AGREEMENT000000000000001";
    private static final String UNIT = "01UNIT00000000000000000001";
    private static final String SALE = "01SALE00000000000000000001";
    private static final String REVIEWER = "01REVIEWER0000000000000001";
    private static final long VERSION = 7L;
    private static final ListingViewer REVIEWER_VIEWER = new ListingViewer(REVIEWER, Set.of("ROLE_LISTING_REVIEWER"));

    private InMemoryListingRepository listings = new InMemoryListingRepository();
    private final StubAdvertisingRightPort rights = new StubAdvertisingRightPort();
    private final StubUnitInventoryPort units = new StubUnitInventoryPort();
    private final RecordingEventOutboxPort events = new RecordingEventOutboxPort();
    private final AtomicInteger nextId = new AtomicInteger();

    private ListingReviewCommandService service() {
        return new ListingReviewCommandService(listings, new InMemoryListingAccessLogRepository(), rights, units,
                () -> "01ID" + String.format("%022d", nextId.incrementAndGet()), () -> 7L,
                new DisplayConditionsAssembler(rights, units, listings), events);
    }

    @Test
    void decide_approvalAllHold_liveAnswerEventHistoryInOrder() {
        world(ListingState.PUBLISHED, live(1), approved(2), RevisionFixtures.pending(3));

        RevisionCommandResult result = service().decide(approve(3), REVIEWER_VIEWER);

        Listing stored = listings.stored(LISTING).orElseThrow();
        assertAll(
                () -> assertEquals(new RevisionCommandResult(LISTING, 3, RevisionStatus.LIVE, VERSION + 1), result),
                () -> assertEquals(List.of(RevisionStatus.REPLACED, RevisionStatus.CANCELLED, RevisionStatus.LIVE),
                        stored.revisions().stream().map(ListingRevision::status).toList()),
                () -> assertEquals(ListingState.PUBLISHED, stored.state()),
                () -> assertEquals(List.of(ListingHistoryAction.APPROVAL_CANCELLED + " 2 " + REVIEWER,
                        ListingHistoryAction.REVISION_APPROVED + " 3 " + REVIEWER,
                        ListingHistoryAction.REVISION_REPLACED + " 1 null",
                        ListingHistoryAction.REVISION_PUT_LIVE_ON_APPROVAL + " 3 null"), history()),
                () -> assertEquals(1, listings.decisions().size()),
                () -> assertEquals(List.of(ListingChangedEvent.now(LISTING)), events.events()));
    }

    @Test
    void decide_approvalWithReasons_approvedNoEvent() {
        world(ListingState.UNPUBLISHED, RevisionFixtures.pending(1));
        listings.putFreshness(LISTING, FreshnessSummary.none(), null);

        RevisionCommandResult result = service().decide(approve(1), REVIEWER_VIEWER);

        assertAll(
                () -> assertEquals(new RevisionCommandResult(LISTING, 1, RevisionStatus.APPROVED, VERSION + 1), result),
                () -> assertEquals(ListingState.UNPUBLISHED, listings.stored(LISTING).orElseThrow().state()),
                () -> assertEquals(RevisionStatus.APPROVED,
                        listings.stored(LISTING).orElseThrow().revision(1).orElseThrow().status()),
                () -> assertEquals(List.of(ListingHistoryAction.REVISION_APPROVED + " 1 " + REVIEWER), history()),
                () -> assertEquals(1, listings.decisions().size()),
                () -> assertEquals(List.of(), events.events()));
    }

    @Test
    void decide_returnOrReject_noLockNoShareLockNoUnitRead() {
        for (RevisionDecisionCommand command : List.of(
                new RevisionDecisionCommand(LISTING, 1, VERSION, ReviewOutcome.NEEDS_CHANGES, List.of(),
                        List.of(ChangeReason.OTHER), null, "Mô tả thiếu diện tích."),
                new RevisionDecisionCommand(LISTING, 1, VERSION, ReviewOutcome.REJECTED, List.of(), List.of(),
                        RejectionGroup.WRONG_UNIT, "Sai căn."))) {
            world(ListingState.UNPUBLISHED, RevisionFixtures.pending(1));

            service().decide(command, REVIEWER_VIEWER);

            assertAll(command.outcome().name(),
                    () -> assertFalse(listings.calls().stream().anyMatch(call -> call.startsWith("lockRow"))),
                    () -> assertFalse(rights.calls().stream().anyMatch(call -> call.startsWith("answerUnderShareLock"))),
                    () -> assertEquals(List.of(), units.calls()),
                    () -> assertEquals(List.of(), events.events()));
        }
    }

    @Test
    void decide_storedStatePaused_noTryNoOtherModuleRead() {
        world(ListingState.PAUSED, RevisionFixtures.pending(1));

        RevisionCommandResult result = service().decide(approve(1), REVIEWER_VIEWER);

        assertAll(
                () -> assertEquals(RevisionStatus.APPROVED, result.status()),
                () -> assertFalse(listings.calls().stream().anyMatch(call -> call.startsWith("lockRow"))),
                () -> assertFalse(rights.calls().stream().anyMatch(call -> call.startsWith("answerUnderShareLock"))),
                () -> assertEquals(List.of(), units.calls()),
                () -> assertEquals(ListingState.PAUSED, listings.stored(LISTING).orElseThrow().state()),
                () -> assertEquals(List.of(), events.events()));
    }

    @Test
    void decide_lockedVersionMoved_concurrentNoDecision() {
        world(ListingState.UNPUBLISHED, RevisionFixtures.pending(1));
        Listing seeded = listings.stored(LISTING).orElseThrow();
        listings = new InMemoryListingRepository() {
            @Override
            protected Optional<Long> lockedVersion(String listingId) {
                return Optional.of(VERSION + 1);
            }
        };
        listings.put(seeded);
        listings.putAuthors("R1", Set.of(SALE));
        listings.putFreshness(LISTING, confirmedToday(), null);

        assertThrows(ListingChangedException.class, () -> service().decide(approve(1), REVIEWER_VIEWER));

        assertNoDecision();
        assertFalse(rights.calls().stream().anyMatch(call -> call.startsWith("answerUnderShareLock")));
    }

    @Test
    void decide_unknownE05Group_illegalStateNoDecision() {
        world(ListingState.UNPUBLISHED, RevisionFixtures.pending(1));
        rights.answer(new AdvertisingAnswerFacts(UNIT, AGREEMENT, false, false, List.of("SOMETHING_NEW")));

        assertThrows(IllegalStateException.class, () -> service().decide(approve(1), REVIEWER_VIEWER));

        assertNoDecision();
    }

    @Test
    void decide_unknownAvailability_illegalStateNoDecision() {
        world(ListingState.UNPUBLISHED, RevisionFixtures.pending(1));
        units.availability(new UnitAvailabilityFacts(UNIT, "P101", "RESERVED", false));

        assertThrows(IllegalStateException.class, () -> service().decide(approve(1), REVIEWER_VIEWER));

        assertNoDecision();
    }

    /**
     * The listing at version 7 in the state given with the revisions given; E05's standing names {@code SALE}, allows
     * with terms equal to the snapshot's; the unit available; a valid confirmation today.
     */
    private void world(ListingState state, ListingRevision... revisions) {
        listings = new InMemoryListingRepository();
        listings.put(Listing.load(LISTING, CODE, UNIT, TransactionType.LONG_TERM_RENT, AGREEMENT, state, SALE, CREATED,
                CREATED, VERSION, List.of(revisions)));
        for (ListingRevision revision : revisions) {
            listings.putAuthors(revision.id(), Set.of(SALE));
        }
        listings.putFreshness(LISTING, confirmedToday(), null);
        rights.standing(new AgreementStandingFacts(AGREEMENT, UNIT, "LONG_TERM_RENT", false, SALE));
        rights.answer(new AdvertisingAnswerFacts(UNIT, AGREEMENT, false, true, List.of()));
        rights.terms(UNIT, AGREEMENT, new AgreementTermsFacts(false, DisplayConditionsAssemblerTest.snapshotTerms(),
                null, null));
        units.availability(new UnitAvailabilityFacts(UNIT, "P101", "AVAILABLE", false));
    }

    private void assertNoDecision() {
        assertAll(
                () -> assertFalse(listings.calls().stream().anyMatch(call -> call.startsWith("update ")),
                        listings.calls().toString()),
                () -> assertTrue(listings.decisions().isEmpty()),
                () -> assertTrue(listings.writtenHistory().isEmpty()),
                () -> assertEquals(RevisionStatus.PENDING_REVIEW,
                        listings.stored(LISTING).orElseThrow().revision(1).orElseThrow().status()),
                () -> assertEquals(List.of(), events.events()));
    }

    private List<String> history() {
        return listings.writtenHistory().stream()
                .map(entry -> entry.action() + " " + entry.revisionNoOrNull() + " " + entry.actorIdOrNull())
                .toList();
    }

    private static RevisionDecisionCommand approve(int revisionNo) {
        return new RevisionDecisionCommand(LISTING, revisionNo, VERSION, ReviewOutcome.APPROVED, ReviewCheck.VERSION_1,
                List.of(), null, null);
    }

    private static FreshnessSummary confirmedToday() {
        return new FreshnessSummary(FreshnessPolicy.today(Instant.now()), true);
    }

    private static ListingRevision approved(int revisionNo) {
        return ListingRevision.load("R" + revisionNo, revisionNo, RevisionStatus.APPROVED, SALE, CREATED, CREATED,
                RevisionFixtures.completeContent(), RevisionFixtures.inEffectTerms(), RevisionFixtures.unitSnapshot(),
                CREATED, SALE);
    }

    private static ListingRevision live(int revisionNo) {
        return ListingRevision.load("R" + revisionNo, revisionNo, RevisionStatus.LIVE, SALE, CREATED, CREATED,
                RevisionFixtures.completeContent(), RevisionFixtures.inEffectTerms(), RevisionFixtures.unitSnapshot(),
                CREATED, SALE, null, CREATED, null);
    }
}
