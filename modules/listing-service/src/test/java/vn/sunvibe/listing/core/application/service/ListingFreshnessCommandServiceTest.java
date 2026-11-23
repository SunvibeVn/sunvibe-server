package vn.sunvibe.listing.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.application.command.FreshnessRecordCommand;
import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.exception.ListingRefusedException;
import vn.sunvibe.listing.core.application.query.FreshnessRecordResult;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingAnswerFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingReviewFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingRightPort;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementTermsFacts;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;
import vn.sunvibe.listing.core.domain.exception.ListingChangedException;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessChannel;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessOutcome;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessRecord;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessSummary;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;
import vn.sunvibe.listing.core.domain.policy.listing.FreshnessPolicy;
import vn.sunvibe.listing.core.testsupport.InMemoryListingRepository;
import vn.sunvibe.listing.core.testsupport.RecordingEventOutboxPort;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;
import vn.sunvibe.listing.core.testsupport.StubAdvertisingRightPort;
import vn.sunvibe.listing.core.testsupport.StubUnitInventoryPort;
import vn.sunvibe.listing.core.testsupport.StubUnitProfilePort;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.draft;

/**
 * The freshness record command (listing-publish US-6 AC-1, AC-2, BR-9, BR-11; E06-S3 spec 3.1, E06S3-D3, E06S3-D4,
 * E06S3-D6, E06S3-D9), with fakes, in the order of checks of E06S3-D3: scope by the current assignee, archived, data
 * version, then the guarded write. Every record date made here is accepted as the Vietnam date read just before or just
 * after the call (E06S3-D14).
 */
class ListingFreshnessCommandServiceTest {

    private static final String LISTING = "01JLISTING0000000000000001";
    private static final String MANAGED_LISTING = "01JLISTING0000000000000002";
    private static final String ORPHAN_LISTING = "01JLISTING0000000000000003";
    private static final String ARCHIVED_LISTING = "01JLISTING0000000000000004";
    private static final String AGREEMENT = "01AGREEMENT";
    private static final String UNIT = "01UNIT";
    private static final String SALE = "01SALE";
    private static final long VERSION = 3L;
    private static final ListingViewer SALE_VIEWER = new ListingViewer(SALE, Set.of("ROLE_SALE"));
    private static final String TYPED_NOTE = "  Chủ xác nhận còn trống.\r\n";
    private static final String STORED_NOTE = "Chủ xác nhận còn trống.";

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final StubAdvertisingRightPort stubRights = new StubAdvertisingRightPort();
    private final CountingRightPort rights = new CountingRightPort(stubRights);
    private final StubUnitProfilePort units = new StubUnitProfilePort();
    private final AtomicInteger nextId = new AtomicInteger();
    private final ListingCommandService service = new ListingCommandService(listings, rights, units,
            () -> "01ID" + nextId.incrementAndGet(), RandomGenerator.of("L64X128MixRandom"),
            new DisplayConditionsAssembler(rights, new StubUnitInventoryPort(), listings), new RecordingEventOutboxPort());

    @Test
    void recordFreshness_assignedSaleValid_recordSummaryHistoryAndVersion() {
        stored(LISTING, AGREEMENT, ListingState.UNPUBLISHED);
        standing(AGREEMENT, false, SALE);
        List<String> callsBefore = listings.calls();

        Instant from = Instant.now();
        LocalDate dayBefore = FreshnessPolicy.today(from);
        FreshnessRecordResult result = service.recordFreshness(command(LISTING, VERSION, FreshnessOutcome.AVAILABLE,
                true, true, TYPED_NOTE), SALE_VIEWER);
        Instant to = Instant.now();
        LocalDate dayAfter = FreshnessPolicy.today(to);
        List<String> added = listings.calls().subList(callsBefore.size(), listings.calls().size());

        List<FreshnessRecord> records = listings.freshnessRecords(LISTING);
        assertEquals(List.of(List.of(LISTING, VERSION + 1, SALE, FreshnessChannel.CALL, FreshnessOutcome.AVAILABLE, true,
                        true, STORED_NOTE, true, true)),
                records.stream().map(record -> List.of(record.listingId(), record.listingRowVersion(),
                        record.recordedBy(), record.channel(), record.outcome(), record.termsStillApplyOrNull(),
                        record.unitUnchangedOrNull(), record.noteOrNull(),
                        List.of(dayBefore, dayAfter).contains(record.contactDate()),
                        !record.recordedAt().isBefore(from.truncatedTo(java.time.temporal.ChronoUnit.MICROS))
                                && !record.recordedAt().isAfter(to))).toList());
        FreshnessRecord record = records.getFirst();
        assertEquals(new FreshnessSummary(record.contactDate(), true), listings.findFreshnessSummary(LISTING));
        assertEquals(List.of("FRESHNESS_RECORDED null " + SALE + " " + record.recordedAt()),
                listings.writtenHistory().stream().map(entry -> entry.action() + " " + entry.revisionNoOrNull() + " "
                        + entry.actorIdOrNull() + " " + entry.occurredAt()).toList());
        assertEquals(VERSION + 1, result.dataVersion());
        assertTrue(result.valid());
        assertEquals(List.of("findRow " + LISTING, "findFreshnessSummary " + LISTING, "findApprovedRevisionNo " + LISTING,
                "recordFreshness " + LISTING), added);
    }

    @Test
    void recordFreshness_invalidOutcomes_keepDateLatestInvalid() {
        stored(LISTING, AGREEMENT, ListingState.UNPUBLISHED);
        standing(AGREEMENT, false, SALE);
        LocalDate confirmedOn = FreshnessPolicy.today(Instant.now()).minusDays(8);
        listings.putFreshness(LISTING, new FreshnessSummary(confirmedOn, true), null);

        List<FreshnessSummary> summaries = new ArrayList<>();
        service.recordFreshness(command(LISTING, VERSION, FreshnessOutcome.NOT_AVAILABLE, null, null, null),
                SALE_VIEWER);
        summaries.add(listings.findFreshnessSummary(LISTING));
        service.recordFreshness(command(LISTING, VERSION + 1, FreshnessOutcome.UNREACHABLE, null, null, null),
                SALE_VIEWER);
        summaries.add(listings.findFreshnessSummary(LISTING));
        service.recordFreshness(command(LISTING, VERSION + 2, FreshnessOutcome.AVAILABLE, true, false, null),
                SALE_VIEWER);
        summaries.add(listings.findFreshnessSummary(LISTING));

        FreshnessSummary keptInvalid = new FreshnessSummary(confirmedOn, false);
        assertEquals(List.of(keptInvalid, keptInvalid, keptInvalid), summaries);
    }

    @Test
    void recordFreshness_notAssigneeUnknownOrMalformed_notFoundNothingWritten() {
        stored(LISTING, AGREEMENT, ListingState.UNPUBLISHED);
        standing(AGREEMENT, false, SALE);
        stored(MANAGED_LISTING, "01MANAGEDAGREEMENT", ListingState.UNPUBLISHED);
        standing("01MANAGEDAGREEMENT", false, "01MANAGER");
        stored(ORPHAN_LISTING, "01ORPHANAGREEMENT", ListingState.UNPUBLISHED);
        List<Object> before = writtenState();

        List<Class<?>> thrown = Arrays.asList(
                thrown(() -> service.recordFreshness(valid(LISTING),
                        new ListingViewer("01OTHERSALE", Set.of("ROLE_SALE")))),
                thrown(() -> service.recordFreshness(valid(MANAGED_LISTING),
                        new ListingViewer("01MANAGER", Set.of("ROLE_SALES_MANAGER")))),
                thrown(() -> service.recordFreshness(valid("01JLISTING0000000000000009"), SALE_VIEWER)),
                thrown(() -> service.recordFreshness(valid("01"), SALE_VIEWER)),
                thrown(() -> service.recordFreshness(valid(ORPHAN_LISTING), SALE_VIEWER)));

        assertEquals(List.of(ListingNotFoundException.class, ListingNotFoundException.class,
                ListingNotFoundException.class, ListingNotFoundException.class, ListingNotFoundException.class), thrown);
        assertEquals(before, writtenState());
    }

    @Test
    void recordFreshness_agreementEndedOrStoredArchived_listingArchived() {
        stored(LISTING, AGREEMENT, ListingState.UNPUBLISHED);
        standing(AGREEMENT, true, SALE);
        stored(ARCHIVED_LISTING, "01ARCHIVEDAGREEMENT", ListingState.ARCHIVED);
        standing("01ARCHIVEDAGREEMENT", false, SALE);

        assertEquals(List.of(ListingRefusedException.Reason.LISTING_ARCHIVED,
                        ListingRefusedException.Reason.LISTING_ARCHIVED),
                Arrays.asList(refusal(() -> service.recordFreshness(valid(LISTING), SALE_VIEWER)),
                        refusal(() -> service.recordFreshness(valid(ARCHIVED_LISTING), SALE_VIEWER))));
    }

    @Test
    void recordFreshness_staleDataVersion_concurrentNothingWritten() {
        stored(LISTING, AGREEMENT, ListingState.UNPUBLISHED);
        standing(AGREEMENT, false, SALE);
        List<Object> before = writtenState();

        assertThrows(ListingChangedException.class, () -> service.recordFreshness(command(LISTING, VERSION - 1,
                FreshnessOutcome.AVAILABLE, true, true, null), SALE_VIEWER));
        assertEquals(before, writtenState());
    }

    @Test
    void recordFreshness_onlyStandingsRead_noOtherPort() {
        stored(LISTING, AGREEMENT, ListingState.UNPUBLISHED);
        standing(AGREEMENT, false, SALE);

        service.recordFreshness(valid(LISTING), SALE_VIEWER);

        assertEquals(1, rights.count("standings"));
        assertEquals(List.of(0, 0, 0), List.of(rights.count("answer"), rights.count("answerUnderShareLock"),
                rights.count("terms")));
        assertEquals(List.of(), units.calls());
    }

    @Test
    void recordFreshness_twoSameDay_latestIsSecond() {
        stored(LISTING, AGREEMENT, ListingState.UNPUBLISHED);
        standing(AGREEMENT, false, SALE);

        service.recordFreshness(command(LISTING, VERSION, FreshnessOutcome.UNREACHABLE, null, null, null),
                SALE_VIEWER);
        service.recordFreshness(command(LISTING, VERSION + 1, FreshnessOutcome.AVAILABLE, true, true, null),
                SALE_VIEWER);

        assertEquals(List.of(VERSION + 1, VERSION + 2),
                listings.freshnessRecords(LISTING).stream().map(FreshnessRecord::listingRowVersion).toList());
    }

    /** What a record could write: the records and history entries, and the row version of each stored listing. */
    private List<Object> writtenState() {
        List<Object> state = new ArrayList<>();
        for (String listingId : List.of(LISTING, MANAGED_LISTING, ORPHAN_LISTING, ARCHIVED_LISTING)) {
            state.add(listings.freshnessRecords(listingId));
            state.add(listings.stored(listingId).map(Listing::rowVersion));
        }
        state.add(listings.writtenHistory());
        return state;
    }

    private static FreshnessRecordCommand valid(String listingId) {
        return command(listingId, VERSION, FreshnessOutcome.AVAILABLE, true, true, null);
    }

    private static FreshnessRecordCommand command(String listingId, long dataVersion, FreshnessOutcome outcome,
                                                  Boolean termsStillApply, Boolean unitUnchanged, String note) {
        return new FreshnessRecordCommand(listingId, dataVersion, FreshnessChannel.CALL, outcome, termsStillApply,
                unitUnchanged, note);
    }

    private void stored(String listingId, String agreementId, ListingState state) {
        listings.put(Listing.load(listingId, RevisionFixtures.CODE, UNIT, TransactionType.LONG_TERM_RENT, agreementId,
                state, SALE, RevisionFixtures.CREATED, RevisionFixtures.CREATED, VERSION, List.of(draft(1))));
    }

    private void standing(String agreementId, boolean ended, String assignee) {
        stubRights.standing(new AgreementStandingFacts(agreementId, UNIT, "LONG_TERM_RENT", ended, assignee));
    }

    /** The class of the exception a call throws, or null when it throws none. */
    private static Class<?> thrown(Runnable call) {
        try {
            call.run();
            return null;
        } catch (RuntimeException e) {
            return e.getClass();
        }
    }

    /** The reason of the refusal a call throws, or null when it throws none. */
    private static ListingRefusedException.Reason refusal(Runnable call) {
        try {
            call.run();
            return null;
        } catch (ListingRefusedException e) {
            return e.reason();
        }
    }

    /** E05's port counting each method called, around the stub. */
    private static final class CountingRightPort implements AdvertisingRightPort {

        private final AdvertisingRightPort delegate;
        private final List<String> methods = new ArrayList<>();

        private CountingRightPort(AdvertisingRightPort delegate) {
            this.delegate = delegate;
        }

        int count(String method) {
            return (int) methods.stream().filter(method::equals).count();
        }

        @Override
        public List<AgreementStandingFacts> standings(Collection<String> agreementIds) {
            methods.add("standings");
            return delegate.standings(agreementIds);
        }

        @Override
        public Optional<AdvertisingAnswerFacts> answer(String unitId, String agreementId) {
            methods.add("answer");
            return delegate.answer(unitId, agreementId);
        }

        @Override
        public Optional<AdvertisingAnswerFacts> answerUnderShareLock(String unitId, String agreementId) {
            methods.add("answerUnderShareLock");
            return delegate.answerUnderShareLock(unitId, agreementId);
        }

        @Override
        public Optional<AgreementTermsFacts> terms(String unitId, String agreementId) {
            methods.add("terms");
            return delegate.terms(unitId, agreementId);
        }

        @Override
        public Optional<AdvertisingReviewFacts> review(String unitId, String agreementId) {
            methods.add("review");
            return delegate.review(unitId, agreementId);
        }
    }
}
