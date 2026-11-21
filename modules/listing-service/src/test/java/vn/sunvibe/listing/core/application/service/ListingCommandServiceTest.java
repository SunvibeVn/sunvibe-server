package vn.sunvibe.listing.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.application.command.ListingCreateCommand;
import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.exception.ListingRefusedException;
import vn.sunvibe.listing.core.application.query.ListingCreateResult;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingAnswerFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;
import vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingCode;
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryAction;
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryEntry;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;
import vn.sunvibe.listing.core.testsupport.InMemoryListingRepository;
import vn.sunvibe.listing.core.testsupport.StubAdvertisingRightPort;
import vn.sunvibe.listing.core.testsupport.StubUnitProfilePort;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup.ENDED;
import static vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup.NOT_STARTED;
import static vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup.NO_AGREEMENT;
import static vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup.OWNER;
import static vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup.SUSPENDED;

/**
 * The listing create (listing-publish US-1 AC-1 to AC-4, BR-1, LP-1; E06-S1 spec 3.1, E06S1-D2, E06S1-D3, E06S1-D4,
 * E06S1-D6, E06S1-D16; LC-1 to LC-7, LC-11, LC-16 at service level), with fakes and a fixed random source.
 */
class ListingCommandServiceTest {

    private static final String AGREEMENT = "01AGREEMENT";
    private static final String UNIT = "01UNIT";
    private static final String SALE = "01SALE";
    private static final ListingViewer SALE_VIEWER = new ListingViewer(SALE, Set.of("ROLE_SALE"));

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final StubAdvertisingRightPort rights = new StubAdvertisingRightPort();
    private final AtomicInteger nextId = new AtomicInteger();
    private final ListingCommandService service = new ListingCommandService(listings, rights, new StubUnitProfilePort(),
            () -> "01ID" + nextId.incrementAndGet(), new CountingRandom());

    @Test
    void create_assigneeAllowed_threeRowsAndResult() {
        standing(false, SALE, "LONG_TERM_RENT");
        answer(false, true, List.of());

        ListingCreateResult result = service.create(new ListingCreateCommand(AGREEMENT), SALE_VIEWER);

        assertEquals(1, listings.inserted().size());
        Listing listing = listings.inserted().getFirst();
        assertEquals(new ListingCreateResult(listing.id(), "TD-2345-6789"), result);
        assertEquals(ListingCode.of("TD-2345-6789"), listing.code());
        assertEquals(UNIT, listing.unitId());
        assertEquals(AGREEMENT, listing.agreementId());
        assertEquals(TransactionType.LONG_TERM_RENT, listing.transactionType());
        assertEquals(ListingState.UNPUBLISHED, listing.state());
        assertEquals(SALE, listing.createdBy());
        assertEquals(0L, listing.rowVersion());
        Instant now = listing.createdAt();
        assertEquals(now.truncatedTo(ChronoUnit.MICROS), now);
        ListingRevision revision = listing.revisions().getFirst();
        assertEquals(1, listing.revisions().size());
        assertEquals(1, revision.revisionNo());
        assertEquals(RevisionStatus.DRAFT, revision.status());
        assertEquals(SALE, revision.createdBy());
        assertEquals(now, revision.createdAt());
        assertEquals(List.of(new ListingHistoryEntry(listing.pendingHistory().getFirst().id(),
                ListingHistoryAction.CREATED, 1, SALE, now)), listing.pendingHistory());
        assertEquals(List.of("standings [" + AGREEMENT + "]", "answerUnderShareLock " + UNIT + " " + AGREEMENT),
                rights.calls());
    }

    @Test
    void create_notStartedAlone_created() {
        standing(false, SALE, "LONG_TERM_RENT");
        answer(false, false, List.of("NOT_STARTED"));

        service.create(new ListingCreateCommand(AGREEMENT), SALE_VIEWER);

        assertEquals(1, listings.inserted().size());
    }

    @Test
    void create_unknownNotAssigneeOrOtherType_notFoundNothingWritten() {
        assertThrows(ListingNotFoundException.class,
                () -> service.create(new ListingCreateCommand(AGREEMENT), SALE_VIEWER));

        standing(false, "01OTHERSALE", "LONG_TERM_RENT");
        answer(false, true, List.of());
        assertThrows(ListingNotFoundException.class,
                () -> service.create(new ListingCreateCommand(AGREEMENT), SALE_VIEWER));

        standing(false, SALE, "SALE");
        assertThrows(ListingNotFoundException.class,
                () -> service.create(new ListingCreateCommand(AGREEMENT), SALE_VIEWER));

        assertTrue(listings.inserted().isEmpty());
        assertNoAnswerRead();
    }

    @Test
    void create_managerAssignee_notFound() {
        standing(false, "01MANAGER", "LONG_TERM_RENT");
        answer(false, true, List.of());

        assertThrows(ListingNotFoundException.class, () -> service.create(new ListingCreateCommand(AGREEMENT),
                new ListingViewer("01MANAGER", Set.of("ROLE_SALES_MANAGER"))));

        assertTrue(listings.inserted().isEmpty());
        assertNoAnswerRead();
    }

    @Test
    void create_standingEnded_endedNoAnswerRead() {
        standing(true, SALE, "LONG_TERM_RENT");
        answer(false, true, List.of());

        ListingRefusedException refusal = assertThrows(ListingRefusedException.class,
                () -> service.create(new ListingCreateCommand(AGREEMENT), SALE_VIEWER));

        assertEquals(ListingRefusedException.Reason.LISTING_NOT_ALLOWED, refusal.reason());
        assertEquals(List.of(ENDED), refusal.groups());
        assertTrue(listings.inserted().isEmpty());
        assertNoAnswerRead();
    }

    @Test
    void create_openListing_existsNoAnswerRead() {
        standing(false, SALE, "LONG_TERM_RENT");
        answer(false, true, List.of());
        listings.put(Listing.create("01OPEN", ListingCode.of("TD-ZZZZ-ZZZZ"), UNIT, AGREEMENT, SALE, Instant.now(),
                "01OPENREV", "01OPENHIS"));

        ListingRefusedException refusal = assertThrows(ListingRefusedException.class,
                () -> service.create(new ListingCreateCommand(AGREEMENT), SALE_VIEWER));

        assertEquals(ListingRefusedException.Reason.LISTING_EXISTS, refusal.reason());
        assertEquals(List.of(), refusal.groups());
        assertTrue(listings.inserted().isEmpty());
        assertNoAnswerRead();
    }

    @Test
    void create_answerAbsent_noAgreement() {
        standing(false, SALE, "LONG_TERM_RENT");

        ListingRefusedException refusal = assertThrows(ListingRefusedException.class,
                () -> service.create(new ListingCreateCommand(AGREEMENT), SALE_VIEWER));

        assertEquals(ListingRefusedException.Reason.LISTING_NOT_ALLOWED, refusal.reason());
        assertEquals(List.of(NO_AGREEMENT), refusal.groups());
        assertTrue(listings.inserted().isEmpty());
    }

    @Test
    void create_refusedGroups_inE05OrderNothingWritten() {
        standing(false, SALE, "LONG_TERM_RENT");
        answer(false, false, List.of("NOT_STARTED", "OWNER"));

        ListingRefusedException twoGroups = assertThrows(ListingRefusedException.class,
                () -> service.create(new ListingCreateCommand(AGREEMENT), SALE_VIEWER));

        answer(false, false, List.of("SUSPENDED"));
        ListingRefusedException suspended = assertThrows(ListingRefusedException.class,
                () -> service.create(new ListingCreateCommand(AGREEMENT), SALE_VIEWER));

        assertEquals(List.of(NOT_STARTED, OWNER), twoGroups.groups());
        assertEquals(List.of(SUSPENDED), suspended.groups());
        assertEquals(ListingRefusedException.Reason.LISTING_NOT_ALLOWED, suspended.reason());
        assertTrue(listings.inserted().isEmpty());
    }

    @Test
    void create_unknownReasonCode_illegalStateNothingWritten() {
        standing(false, SALE, "LONG_TERM_RENT");
        answer(false, false, List.of("FUTURE_GROUP"));

        assertThrows(IllegalStateException.class,
                () -> service.create(new ListingCreateCommand(AGREEMENT), SALE_VIEWER));

        assertTrue(listings.inserted().isEmpty());
    }

    @Test
    void create_takenCodes_skippedThenFifthFails() {
        standing(false, SALE, "LONG_TERM_RENT");
        answer(false, true, List.of());
        listings.takeCodes("TD-2345-6789", "TD-ABCD-EFGH");

        ListingCreateResult result = service.create(new ListingCreateCommand(AGREEMENT), SALE_VIEWER);

        assertEquals("TD-JKLM-NPQR", result.code());
        assertEquals(ListingCode.of("TD-JKLM-NPQR"), listings.inserted().getFirst().code());

        InMemoryListingRepository full = new InMemoryListingRepository();
        full.takeCodes("TD-2345-6789", "TD-ABCD-EFGH", "TD-JKLM-NPQR", "TD-STUV-WXYZ");
        ListingCommandService exhausted = new ListingCommandService(full, rights, new StubUnitProfilePort(),
                () -> "01ID" + nextId.incrementAndGet(), new CountingRandom());

        assertThrows(IllegalStateException.class,
                () -> exhausted.create(new ListingCreateCommand(AGREEMENT), SALE_VIEWER));
        assertEquals(5, full.calls().stream().filter(call -> call.startsWith("existsByCode ")).count());
        assertTrue(full.inserted().isEmpty());
    }

    @Test
    void create_openListingFoundByTheIndex_exists() {
        standing(false, SALE, "LONG_TERM_RENT");
        answer(false, true, List.of());
        listings.refuseNextInsertAsOpen();

        ListingRefusedException refusal = assertThrows(ListingRefusedException.class,
                () -> service.create(new ListingCreateCommand(AGREEMENT), SALE_VIEWER));

        assertAll(
                () -> assertEquals(ListingRefusedException.Reason.LISTING_EXISTS, refusal.reason()),
                () -> assertEquals(List.<AgreementReasonGroup>of(), refusal.groups()),
                () -> assertTrue(listings.inserted().isEmpty()));
    }

    private void standing(boolean ended, String assignee, String transactionType) {
        rights.standing(new AgreementStandingFacts(AGREEMENT, UNIT, transactionType, ended, assignee));
    }

    private void answer(boolean ended, boolean allowed, List<String> groups) {
        rights.answer(new AdvertisingAnswerFacts(UNIT, AGREEMENT, ended, allowed, groups));
    }

    private void assertNoAnswerRead() {
        assertFalse(rights.calls().stream().anyMatch(call -> call.startsWith("answer")), String.valueOf(rights.calls()));
    }

    /** Answers 0, 1, 2 ... modulo the bound: the codes TD-2345-6789, TD-ABCD-EFGH, TD-JKLM-NPQR, TD-STUV-WXYZ, again. */
    private static final class CountingRandom implements RandomGenerator {

        private int next;

        @Override
        public int nextInt(int bound) {
            return next++ % bound;
        }

        @Override
        public long nextLong() {
            throw new UnsupportedOperationException("the code draws with nextInt(int) only");
        }
    }
}
