package vn.sunvibe.listing.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.application.command.FreshnessRecordCommand;
import vn.sunvibe.listing.core.application.query.FreshnessRecordResult;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingAnswerFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementTermsFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitAvailabilityFacts;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;
import vn.sunvibe.listing.core.domain.exception.ListingChangedException;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessChannel;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessOutcome;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessRecord;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessSummary;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryAction;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;
import vn.sunvibe.listing.core.domain.policy.listing.FreshnessPolicy;
import vn.sunvibe.listing.core.testsupport.InMemoryListingRepository;
import vn.sunvibe.listing.core.testsupport.RecordingEventOutboxPort;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;
import vn.sunvibe.listing.core.testsupport.StubAdvertisingRightPort;
import vn.sunvibe.listing.core.testsupport.StubUnitInventoryPort;
import vn.sunvibe.listing.core.testsupport.StubUnitProfilePort;
import vn.sunvibe.shared.outbox.ListingChangedEvent;
import vn.sunvibe.shared.outbox.OutboxEvent;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CODE;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CREATED;

/**
 * The valid confirmation's try to put live (listing-publish US-6 AC-1, US-5 AC-3 moment 2; E06-S6 spec E06S6-D7; PL-4
 * service half): only a valid record on an Unpublished or Published listing with an Approved revision tries, with the
 * summary the record moves to; its version compared with the working set and the locked row, E05 read under its share
 * lock; went live: the record, the summary, the listing's state and revisions in one write, the record's history then the
 * put live's without actor, one event; reasons: today's record and no event; an unknown E05 group or availability code
 * fails the whole record.
 */
class ListingConfirmationPutLiveTest {

    /** A ULID: the listing event takes nothing else. */
    private static final String LISTING = "01JFQ9P0000000000000000001";
    private static final String AGREEMENT = "01AGREEMENT000000000000001";
    private static final String UNIT = "01UNIT00000000000000000001";
    private static final String SALE = "01SALE00000000000000000001";
    private static final long VERSION = 7L;
    private static final ListingViewer SALE_VIEWER = new ListingViewer(SALE, Set.of("ROLE_SALE"));

    private InMemoryListingRepository listings = new InMemoryListingRepository();
    private final StubAdvertisingRightPort rights = new StubAdvertisingRightPort();
    private final StubUnitInventoryPort units = new StubUnitInventoryPort();
    private final RecordingEventOutboxPort events = new RecordingEventOutboxPort();
    private final AtomicInteger nextId = new AtomicInteger();

    private ListingCommandService service() {
        return new ListingCommandService(listings, rights, new StubUnitProfilePort(),
                () -> "01ID" + String.format("%022d", nextId.incrementAndGet()), RandomGenerator.of("L64X128MixRandom"),
                new DisplayConditionsAssembler(rights, units, listings), events);
    }

    @Test
    void record_validWithApprovedAllHold_liveRecordVersionEventHistoryInOrder() {
        // no confirmation before this one: only the summary the record moves to lets the listing go live
        world(ListingState.PUBLISHED, live(1), approved(2));

        FreshnessRecordResult result = service().recordFreshness(valid(VERSION), SALE_VIEWER);

        Listing stored = listings.stored(LISTING).orElseThrow();
        List<FreshnessRecord> records = listings.freshnessRecords(LISTING);
        assertAll(
                () -> assertEquals(new FreshnessRecordResult(LISTING, VERSION + 1, true), result),
                () -> assertEquals(1, records.size()),
                () -> assertEquals(VERSION + 1, records.getFirst().listingRowVersion()),
                () -> assertEquals(FreshnessSummary.none().after(records.getFirst()),
                        listings.findFreshnessSummary(LISTING)),
                () -> assertEquals(List.of(RevisionStatus.REPLACED, RevisionStatus.LIVE),
                        stored.revisions().stream().map(ListingRevision::status).toList()),
                () -> assertEquals(ListingState.PUBLISHED, stored.state()),
                () -> assertEquals(VERSION + 1, stored.rowVersion()),
                () -> assertEquals(List.of(ListingHistoryAction.FRESHNESS_RECORDED + " null " + SALE,
                        ListingHistoryAction.REVISION_REPLACED + " 1 null",
                        ListingHistoryAction.REVISION_PUT_LIVE_ON_CONFIRMATION + " 2 null"), history()),
                () -> assertTrue(listings.calls().contains("recordFreshnessWithPutLive " + LISTING)),
                () -> assertFalse(listings.calls().contains("recordFreshness " + LISTING)),
                () -> assertEquals(nowAndAtDeadline(), events.events()));
    }

    @Test
    void record_validWithReasons_recordedApprovedNoEvent() {
        world(ListingState.UNPUBLISHED, approved(1));
        units.availability(new UnitAvailabilityFacts(UNIT, "P101", "OCCUPIED", false));

        FreshnessRecordResult result = service().recordFreshness(valid(VERSION), SALE_VIEWER);

        Listing stored = listings.stored(LISTING).orElseThrow();
        assertAll(
                () -> assertEquals(new FreshnessRecordResult(LISTING, VERSION + 1, true), result),
                () -> assertEquals(1, listings.freshnessRecords(LISTING).size()),
                () -> assertEquals(RevisionStatus.APPROVED, stored.revision(1).orElseThrow().status()),
                () -> assertEquals(ListingState.UNPUBLISHED, stored.state()),
                () -> assertEquals(VERSION + 1, stored.rowVersion()),
                () -> assertEquals(List.of(ListingHistoryAction.FRESHNESS_RECORDED + " null " + SALE), history()),
                () -> assertTrue(listings.calls().contains("recordFreshness " + LISTING)),
                () -> assertFalse(listings.calls().contains("recordFreshnessWithPutLive " + LISTING)),
                () -> assertEquals(nowAndAtDeadline(), events.events()));
    }

    @Test
    void record_invalid_noTryNoShareLock() {
        world(ListingState.UNPUBLISHED, approved(1));

        FreshnessRecordResult result = service().recordFreshness(new FreshnessRecordCommand(LISTING, VERSION,
                FreshnessChannel.CALL, FreshnessOutcome.NOT_AVAILABLE, null, null, null), SALE_VIEWER);

        assertAll(
                () -> assertFalse(result.valid()),
                () -> assertEquals(List.of("findRow " + LISTING, "findFreshnessSummary " + LISTING,
                        "recordFreshness " + LISTING), listings.calls()),
                () -> assertEquals(List.of("standings [" + AGREEMENT + "]"), rights.calls()),
                () -> assertEquals(List.of(), units.calls()),
                () -> assertEquals(RevisionStatus.APPROVED,
                        listings.stored(LISTING).orElseThrow().revision(1).orElseThrow().status()),
                () -> assertEquals(List.of(ListingChangedEvent.now(LISTING)), events.events()));
    }

    @Test
    void record_validNothingWaiting_fourCalls() {
        world(ListingState.PUBLISHED, live(1));

        FreshnessRecordResult result = service().recordFreshness(valid(VERSION), SALE_VIEWER);

        assertAll(
                () -> assertEquals(new FreshnessRecordResult(LISTING, VERSION + 1, true), result),
                () -> assertEquals(List.of("findRow " + LISTING, "findFreshnessSummary " + LISTING,
                        "findApprovedRevisionNo " + LISTING, "recordFreshness " + LISTING), listings.calls()),
                () -> assertEquals(List.of("standings [" + AGREEMENT + "]"), rights.calls()),
                () -> assertEquals(List.of(), units.calls()),
                () -> assertEquals(nowAndAtDeadline(), events.events()));
    }

    @Test
    void record_workingSetVersionMoved_concurrentNothingWritten() {
        Listing seeded = seeded(ListingState.UNPUBLISHED, approved(1));
        listings = new InMemoryListingRepository() {
            @Override
            public Optional<Listing> findForPutLive(String listingId, Integer targetRevisionNoOrNull) {
                // another command commits between the row read and the working set read
                moveVersion(listingId);
                return super.findForPutLive(listingId, targetRevisionNoOrNull);
            }
        };
        listings.put(seeded);

        assertThrows(ListingChangedException.class, () -> service().recordFreshness(valid(VERSION), SALE_VIEWER));

        assertNothingWritten();
        assertFalse(listings.calls().stream().anyMatch(call -> call.startsWith("lockRow")));
        assertFalse(rights.calls().stream().anyMatch(call -> call.startsWith("answerUnderShareLock")));
    }

    @Test
    void record_lockedVersionMoved_concurrentNothingWritten() {
        Listing seeded = seeded(ListingState.UNPUBLISHED, approved(1));
        listings = new InMemoryListingRepository() {
            @Override
            protected Optional<Long> lockedVersion(String listingId) {
                // another command committed after the reads and before the lock
                return Optional.of(VERSION + 1);
            }
        };
        listings.put(seeded);

        assertThrows(ListingChangedException.class, () -> service().recordFreshness(valid(VERSION), SALE_VIEWER));

        assertNothingWritten();
        assertFalse(rights.calls().stream().anyMatch(call -> call.startsWith("answerUnderShareLock")));
    }

    @Test
    void record_unknownAvailability_illegalStateNoRecord() {
        world(ListingState.UNPUBLISHED, approved(1));
        units.availability(new UnitAvailabilityFacts(UNIT, "P101", "RESERVED", false));

        assertThrows(IllegalStateException.class, () -> service().recordFreshness(valid(VERSION), SALE_VIEWER));

        assertNothingWritten();
    }

    @Test
    void record_unknownE05Group_illegalStateNoRecord() {
        world(ListingState.UNPUBLISHED, approved(1));
        rights.answer(new AdvertisingAnswerFacts(UNIT, AGREEMENT, false, false, List.of("SOMETHING_NEW")));

        assertThrows(IllegalStateException.class, () -> service().recordFreshness(valid(VERSION), SALE_VIEWER));

        assertNothingWritten();
    }

    @Test
    void record_storedStatePaused_noTry() {
        world(ListingState.PAUSED, approved(1));

        FreshnessRecordResult result = service().recordFreshness(valid(VERSION), SALE_VIEWER);

        assertAll(
                () -> assertEquals(new FreshnessRecordResult(LISTING, VERSION + 1, true), result),
                () -> assertFalse(listings.calls().stream().anyMatch(call -> call.startsWith("findApprovedRevisionNo")
                        || call.startsWith("lockRow"))),
                () -> assertFalse(rights.calls().stream().anyMatch(call -> call.startsWith("answerUnderShareLock"))),
                () -> assertEquals(List.of(), units.calls()),
                () -> assertEquals(RevisionStatus.APPROVED,
                        listings.stored(LISTING).orElseThrow().revision(1).orElseThrow().status()),
                () -> assertEquals(ListingState.PAUSED, listings.stored(LISTING).orElseThrow().state()),
                () -> assertEquals(nowAndAtDeadline(), events.events()));
    }

    /**
     * listing-publish E06-S7 spec E06S7-D12 (S-7): a valid record's two signals, due now and at the deadline of its
     * contact date; a put live of the record has no third.
     */
    private List<OutboxEvent> nowAndAtDeadline() {
        return List.of(ListingChangedEvent.now(LISTING), ListingChangedEvent.dueAt(LISTING,
                FreshnessPolicy.deadline(listings.freshnessRecords(LISTING).getLast().contactDate())));
    }

    /**
     * The listing at version 7 in the state given with the revisions given and no confirmation yet; E05's standing names
     * {@code SALE}, allows with terms equal to the snapshot's; the unit available.
     */
    private void world(ListingState state, ListingRevision... revisions) {
        listings = new InMemoryListingRepository();
        listings.put(seeded(state, revisions));
    }

    private Listing seeded(ListingState state, ListingRevision... revisions) {
        rights.standing(new AgreementStandingFacts(AGREEMENT, UNIT, "LONG_TERM_RENT", false, SALE));
        rights.answer(new AdvertisingAnswerFacts(UNIT, AGREEMENT, false, true, List.of()));
        rights.terms(UNIT, AGREEMENT, new AgreementTermsFacts(false, DisplayConditionsAssemblerTest.snapshotTerms(),
                null, null));
        units.availability(new UnitAvailabilityFacts(UNIT, "P101", "AVAILABLE", false));
        return Listing.load(LISTING, CODE, UNIT, TransactionType.LONG_TERM_RENT, AGREEMENT, state, SALE, CREATED,
                CREATED, VERSION, List.of(revisions));
    }

    private void assertNothingWritten() {
        assertAll(
                () -> assertFalse(listings.calls().stream().anyMatch(call -> call.startsWith("recordFreshness")),
                        listings.calls().toString()),
                () -> assertEquals(List.of(), listings.freshnessRecords(LISTING)),
                () -> assertEquals(List.of(), listings.writtenHistory()),
                () -> assertEquals(RevisionStatus.APPROVED,
                        listings.stored(LISTING).orElseThrow().revision(1).orElseThrow().status()),
                () -> assertEquals(List.of(), events.events()));
    }

    private List<String> history() {
        return listings.writtenHistory().stream()
                .map(entry -> entry.action() + " " + entry.revisionNoOrNull() + " " + entry.actorIdOrNull())
                .toList();
    }

    private static FreshnessRecordCommand valid(long dataVersion) {
        return new FreshnessRecordCommand(LISTING, dataVersion, FreshnessChannel.CALL, FreshnessOutcome.AVAILABLE, true,
                true, null);
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
