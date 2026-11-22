package vn.sunvibe.listing.core.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import vn.sunvibe.listing.core.application.command.PutLiveCommand;
import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.exception.ListingRefusedException;
import vn.sunvibe.listing.core.application.query.RevisionCommandResult;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingAnswerFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementTermsFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitAvailabilityFacts;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;
import vn.sunvibe.listing.core.domain.exception.ListingChangedException;
import vn.sunvibe.listing.core.domain.exception.RevisionStateException;
import vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup;
import vn.sunvibe.listing.core.domain.model.listing.DisplayReason;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessSummary;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryAction;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;
import vn.sunvibe.listing.core.domain.policy.listing.FreshnessPolicy;
import vn.sunvibe.listing.core.domain.repository.ListingRow;
import vn.sunvibe.listing.core.testsupport.InMemoryListingRepository;
import vn.sunvibe.listing.core.testsupport.RecordingEventOutboxPort;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;
import vn.sunvibe.listing.core.testsupport.StubAdvertisingRightPort;
import vn.sunvibe.listing.core.testsupport.StubUnitInventoryPort;
import vn.sunvibe.shared.outbox.ListingChangedEvent;
import vn.sunvibe.shared.outbox.OutboxEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * The assigned Sale's put live over the in-memory listings and E05's and property's stubs (listing-publish US-5 AC-1,
 * AC-3, AC-4; E06-S6 spec E06S6-D5, D2, D8 points 1 and 4; PL-1, PL-2, PL-6 service halves): the checks in order, the
 * first failing one answering; the data version compared with the row, the working set and the locked row; the listing
 * row locked before E05's share lock; any display reason refusing with nothing written; one write and one event when
 * the revision goes live.
 */
class ListingPublicationCommandServiceTest {

    /** A ULID: the listing event takes nothing else. */
    private static final String LISTING = "01JFQ9P0000000000000000001";
    private static final String UNKNOWN = "01JFQ9P0000000000000000009";
    private static final String AGREEMENT = "01AGREEMENT000000000000001";
    private static final String UNIT = "01UNIT00000000000000000001";
    private static final String SALE = "01SALE00000000000000000001";
    private static final String OTHER_SALE = "01SALE00000000000000000002";
    private static final long VERSION = 7L;
    private static final ListingViewer SALE_VIEWER = new ListingViewer(SALE, Set.of("ROLE_SALE"));

    private final List<String> log = new ArrayList<>();
    private InMemoryListingRepository listings = new InMemoryListingRepository();
    private StubAdvertisingRightPort rights = new StubAdvertisingRightPort();
    private StubUnitInventoryPort units = new StubUnitInventoryPort();
    private RecordingEventOutboxPort events = new RecordingEventOutboxPort();
    private final AtomicInteger nextId = new AtomicInteger();

    private ListingPublicationCommandService service() {
        return new ListingPublicationCommandService(listings, rights, new DisplayConditionsAssembler(rights, units,
                listings), events, () -> "01ID" + String.format("%022d", nextId.incrementAndGet()));
    }

    @Test
    void putLive_allHold_liveReplacedPublishedEventOneWrite() {
        world(ListingState.PUBLISHED, live(1), approved(2));

        RevisionCommandResult result = service().putLive(command(2, VERSION), SALE_VIEWER);

        Listing stored = listings.stored(LISTING).orElseThrow();
        assertAll(
                () -> assertEquals(new RevisionCommandResult(LISTING, 2, RevisionStatus.LIVE, VERSION + 1), result),
                () -> assertEquals(ListingState.PUBLISHED, stored.state()),
                () -> assertEquals(VERSION + 1, stored.rowVersion()),
                () -> assertEquals(List.of(RevisionStatus.REPLACED, RevisionStatus.LIVE),
                        stored.revisions().stream().map(ListingRevision::status).toList()),
                () -> assertEquals(List.of(ListingHistoryAction.REVISION_REPLACED + " 1 " + SALE,
                        ListingHistoryAction.REVISION_PUT_LIVE + " 2 " + SALE), history()),
                () -> assertEquals(1, listings.calls().stream().filter(call -> call.startsWith("update ")).count()),
                () -> assertEquals(List.of(ListingChangedEvent.now(LISTING)), events.events()));
    }

    @Test
    void putLive_unpublishedNoLive_published() {
        world(ListingState.UNPUBLISHED, approved(1));

        service().putLive(command(1, VERSION), SALE_VIEWER);

        assertEquals(ListingState.PUBLISHED, listings.stored(LISTING).orElseThrow().state());
        assertEquals(List.of(ListingHistoryAction.REVISION_PUT_LIVE + " 1 " + SALE), history());
    }

    @Test
    void putLive_reasons_putLiveRefusedNothingWritten() {
        world(ListingState.UNPUBLISHED, approved(1));
        rights.answer(new AdvertisingAnswerFacts(UNIT, AGREEMENT, false, false, List.of("SUSPENDED")));
        units.availability(new UnitAvailabilityFacts(UNIT, "P101", "OCCUPIED", false));

        ListingRefusedException refused = assertThrows(ListingRefusedException.class,
                () -> service().putLive(command(1, VERSION), SALE_VIEWER));

        assertAll(
                () -> assertEquals(ListingRefusedException.Reason.PUT_LIVE_REFUSED, refused.reason()),
                () -> assertEquals(List.of(DisplayReason.ADVERTISING_RIGHT, DisplayReason.UNIT_NOT_AVAILABLE),
                        refused.displayReasons()),
                () -> assertEquals(List.of(AgreementReasonGroup.SUSPENDED), refused.groups()),
                () -> assertNothingWritten());
    }

    @Test
    void putLive_reasonsWithoutAdvertising_noGroups() {
        world(ListingState.UNPUBLISHED, approved(1));
        units.availability(new UnitAvailabilityFacts(UNIT, "P101", "OCCUPIED", false));

        ListingRefusedException refused = assertThrows(ListingRefusedException.class,
                () -> service().putLive(command(1, VERSION), SALE_VIEWER));

        assertEquals(List.of(DisplayReason.UNIT_NOT_AVAILABLE), refused.displayReasons());
        assertEquals(List.of(), refused.groups());
    }

    @Test
    void putLive_checksInOrder_firstFailingAnswers() {
        Map<String, Executable> cases = new LinkedHashMap<>();
        cases.put("unknown listing before a stale version", () -> {
            world(ListingState.UNPUBLISHED, approved(1));
            assertThrows(ListingNotFoundException.class, () -> service().putLive(new PutLiveCommand(UNKNOWN, 1,
                    VERSION - 1), SALE_VIEWER));
        });
        cases.put("not the assignee before archived", () -> {
            world(ListingState.ARCHIVED, approved(1));
            assertThrows(ListingNotFoundException.class, () -> service().putLive(command(1, VERSION),
                    new ListingViewer(OTHER_SALE, Set.of("ROLE_SALE"))));
        });
        cases.put("archived before a stale version", () -> {
            world(ListingState.ARCHIVED, approved(1));
            assertEquals(ListingRefusedException.Reason.LISTING_ARCHIVED, assertThrows(ListingRefusedException.class,
                    () -> service().putLive(command(1, VERSION - 1), SALE_VIEWER)).reason());
        });
        cases.put("stale version before an unknown revision", () -> {
            world(ListingState.UNPUBLISHED, approved(1));
            assertThrows(ListingChangedException.class, () -> service().putLive(command(9, VERSION - 1),
                    SALE_VIEWER));
        });
        cases.put("unknown revision before not approved", () -> {
            world(ListingState.UNPUBLISHED, approved(1));
            assertThrows(ListingNotFoundException.class, () -> service().putLive(command(9, VERSION), SALE_VIEWER));
            assertThrows(ListingNotFoundException.class, () -> service().putLive(command(null, VERSION),
                    SALE_VIEWER));
        });
        cases.put("not approved before the display reasons", () -> {
            world(ListingState.PAUSED, RevisionFixtures.pending(1));
            assertEquals(RevisionStateException.Reason.REVISION_NOT_APPROVED,
                    assertThrows(RevisionStateException.class, () -> service().putLive(command(1, VERSION),
                            SALE_VIEWER)).reason());
        });

        for (Map.Entry<String, Executable> check : cases.entrySet()) {
            assertAll(check.getKey(), check.getValue(), this::assertNothingWritten);
        }
    }

    @Test
    void putLive_versionMovedBeforeWorkingSet_concurrentNothingWritten() {
        world(ListingState.UNPUBLISHED, approved(1));
        Listing seeded = listings.stored(LISTING).orElseThrow();
        listings = new InMemoryListingRepository() {
            @Override
            public Optional<Listing> findForPutLive(String listingId, Integer targetRevisionNoOrNull) {
                moveVersion(listingId);
                return super.findForPutLive(listingId, targetRevisionNoOrNull);
            }
        };
        listings.put(seeded);
        listings.putFreshness(LISTING, confirmedToday(), null);

        assertThrows(ListingChangedException.class, () -> service().putLive(command(1, VERSION), SALE_VIEWER));

        assertFalse(listings.calls().stream().anyMatch(call -> call.startsWith("lockRow")), listings.calls().toString());
        assertNothingWritten();
    }

    @Test
    void putLive_lockedVersionMoved_concurrentNothingWritten() {
        world(ListingState.UNPUBLISHED, approved(1));
        Listing seeded = listings.stored(LISTING).orElseThrow();
        listings = new InMemoryListingRepository() {
            @Override
            protected Optional<Long> lockedVersion(String listingId) {
                return Optional.of(VERSION + 1);
            }
        };
        listings.put(seeded);
        listings.putFreshness(LISTING, confirmedToday(), null);

        assertThrows(ListingChangedException.class, () -> service().putLive(command(1, VERSION), SALE_VIEWER));

        assertFalse(rights.calls().stream().anyMatch(call -> call.startsWith("answerUnderShareLock")),
                rights.calls().toString());
        assertNothingWritten();
    }

    @Test
    void putLive_targetAlreadyLive_revisionNotApproved() {
        world(ListingState.PUBLISHED, live(1));

        assertEquals(RevisionStateException.Reason.REVISION_NOT_APPROVED, assertThrows(RevisionStateException.class,
                () -> service().putLive(command(1, VERSION), SALE_VIEWER)).reason());

        assertNothingWritten();
    }

    @Test
    void putLive_pausedListing_listingStateWithOtherReasons() {
        world(ListingState.PAUSED, approved(1));
        units.availability(new UnitAvailabilityFacts(UNIT, "P101", "OCCUPIED", false));

        ListingRefusedException refused = assertThrows(ListingRefusedException.class,
                () -> service().putLive(command(1, VERSION), SALE_VIEWER));

        assertEquals(List.of(DisplayReason.LISTING_STATE, DisplayReason.UNIT_NOT_AVAILABLE), refused.displayReasons());
        assertNothingWritten();
    }

    @Test
    void putLive_callOrder_rowWorkingSetLockThenShareLock() {
        world(ListingState.UNPUBLISHED, approved(1));
        Listing seeded = listings.stored(LISTING).orElseThrow();
        StubAdvertisingRightPort seededRights = rights;
        StubUnitInventoryPort seededUnits = units;
        listings = new InMemoryListingRepository() {
            @Override
            public Optional<ListingRow> findRow(String listingId) {
                log.add("findRow");
                return super.findRow(listingId);
            }

            @Override
            public Optional<Listing> findForPutLive(String listingId, Integer targetRevisionNoOrNull) {
                log.add("findForPutLive");
                return super.findForPutLive(listingId, targetRevisionNoOrNull);
            }

            @Override
            public Optional<Long> lockRow(String listingId) {
                log.add("lockRow");
                return super.lockRow(listingId);
            }

            @Override
            public FreshnessSummary findFreshnessSummary(String listingId) {
                log.add("findFreshnessSummary");
                return super.findFreshnessSummary(listingId);
            }

            @Override
            public void update(Listing listing) {
                log.add("update");
                super.update(listing);
            }
        };
        listings.put(seeded);
        listings.putFreshness(LISTING, confirmedToday(), null);
        rights = new StubAdvertisingRightPort() {
            @Override
            public List<AgreementStandingFacts> standings(Collection<String> agreementIds) {
                log.add("standings");
                return seededRights.standings(agreementIds);
            }

            @Override
            public Optional<AdvertisingAnswerFacts> answerUnderShareLock(String unitId, String agreementId) {
                log.add("answerUnderShareLock");
                return seededRights.answerUnderShareLock(unitId, agreementId);
            }

            @Override
            public Optional<AgreementTermsFacts> terms(String unitId, String agreementId) {
                log.add("terms");
                return seededRights.terms(unitId, agreementId);
            }
        };
        units = new StubUnitInventoryPort() {
            @Override
            public List<UnitAvailabilityFacts> availability(Collection<String> unitIds) {
                log.add("availability");
                return seededUnits.availability(unitIds);
            }
        };
        events = new RecordingEventOutboxPort() {
            @Override
            public void append(OutboxEvent event) {
                log.add("append");
                super.append(event);
            }
        };

        service().putLive(command(1, VERSION), SALE_VIEWER);

        assertEquals(List.of("findRow", "standings", "findForPutLive", "lockRow", "answerUnderShareLock", "terms",
                "availability", "findFreshnessSummary", "update", "append"), log);
    }

    @Test
    void putLive_notAssigneeOrUnknown_notFound() {
        Map<String, Executable> cases = new LinkedHashMap<>();
        cases.put("another SALE", () -> assertThrows(ListingNotFoundException.class, () -> service().putLive(
                command(1, VERSION), new ListingViewer(OTHER_SALE, Set.of("ROLE_SALE")))));
        cases.put("a manager", () -> assertThrows(ListingNotFoundException.class, () -> service().putLive(
                command(1, VERSION), new ListingViewer(SALE, Set.of("ROLE_SALES_MANAGER")))));
        cases.put("unknown listing", () -> assertThrows(ListingNotFoundException.class, () -> service().putLive(
                new PutLiveCommand(UNKNOWN, 1, VERSION), SALE_VIEWER)));
        cases.put("malformed listing id", () -> assertThrows(ListingNotFoundException.class, () -> service().putLive(
                new PutLiveCommand("not-an-id", 1, VERSION), SALE_VIEWER)));

        for (Map.Entry<String, Executable> check : cases.entrySet()) {
            world(ListingState.UNPUBLISHED, approved(1));
            assertAll(check.getKey(), check.getValue(), this::assertNothingWritten);
        }
        world(ListingState.UNPUBLISHED, approved(1));
        rights.standing(new AgreementStandingFacts(AGREEMENT, UNIT, "LONG_TERM_RENT", false, null));
        assertThrows(ListingNotFoundException.class, () -> service().putLive(command(1, VERSION), SALE_VIEWER),
                "no assignee");
    }

    @Test
    void putLive_archivedStoredOrEnded_listingArchived() {
        world(ListingState.ARCHIVED, approved(1));
        assertEquals(ListingRefusedException.Reason.LISTING_ARCHIVED, assertThrows(ListingRefusedException.class,
                () -> service().putLive(command(1, VERSION), SALE_VIEWER)).reason());
        assertNothingWritten();

        world(ListingState.UNPUBLISHED, approved(1));
        rights.standing(new AgreementStandingFacts(AGREEMENT, UNIT, "LONG_TERM_RENT", true, SALE));
        assertEquals(ListingRefusedException.Reason.LISTING_ARCHIVED, assertThrows(ListingRefusedException.class,
                () -> service().putLive(command(1, VERSION), SALE_VIEWER)).reason());
        assertNothingWritten();
    }

    /**
     * A fresh world: the listing at version 7 in the state given with the revisions given; E05's standing names
     * {@code SALE}, allows with terms equal to the snapshot's; the unit available; a valid confirmation today.
     */
    private void world(ListingState state, ListingRevision... revisions) {
        listings = new InMemoryListingRepository();
        rights = new StubAdvertisingRightPort();
        units = new StubUnitInventoryPort();
        events = new RecordingEventOutboxPort();
        listings.put(Listing.load(LISTING, CODE, UNIT, TransactionType.LONG_TERM_RENT, AGREEMENT, state, SALE, CREATED,
                CREATED, VERSION, List.of(revisions)));
        listings.putFreshness(LISTING, confirmedToday(), null);
        rights.standing(new AgreementStandingFacts(AGREEMENT, UNIT, "LONG_TERM_RENT", false, SALE));
        rights.answer(new AdvertisingAnswerFacts(UNIT, AGREEMENT, false, true, List.of()));
        rights.terms(UNIT, AGREEMENT, new AgreementTermsFacts(false, DisplayConditionsAssemblerTest.snapshotTerms(),
                null, null));
        units.availability(new UnitAvailabilityFacts(UNIT, "P101", "AVAILABLE", false));
    }

    private void assertNothingWritten() {
        assertFalse(listings.calls().stream().anyMatch(call -> call.startsWith("update ")), listings.calls().toString());
        assertTrue(listings.writtenHistory().isEmpty());
        assertEquals(List.of(), events.events());
    }

    private List<String> history() {
        return listings.writtenHistory().stream()
                .map(entry -> entry.action() + " " + entry.revisionNoOrNull() + " " + entry.actorIdOrNull())
                .toList();
    }

    private static PutLiveCommand command(Integer revisionNoOrNull, long dataVersion) {
        return new PutLiveCommand(LISTING, revisionNoOrNull, dataVersion);
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
