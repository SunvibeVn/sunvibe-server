package vn.sunvibe.listing.core.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import vn.sunvibe.listing.core.application.command.RevisionFeaturesInput;
import vn.sunvibe.listing.core.application.command.RevisionSaveCommand;
import vn.sunvibe.listing.core.application.command.RevisionStartCommand;
import vn.sunvibe.listing.core.application.command.RevisionSubmitCommand;
import vn.sunvibe.listing.core.application.command.RevisionWithdrawCommand;
import vn.sunvibe.listing.core.application.command.TermsCaptureCommand;
import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.exception.ListingRefusedException;
import vn.sunvibe.listing.core.application.query.RevisionCommandResult;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementTermsFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitProfileFacts;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;
import vn.sunvibe.listing.core.domain.exception.ListingChangedException;
import vn.sunvibe.listing.core.domain.exception.RevisionNotSubmittableException;
import vn.sunvibe.listing.core.domain.exception.RevisionStateException;
import vn.sunvibe.listing.core.domain.model.listing.ChargeUnit;
import vn.sunvibe.listing.core.domain.model.listing.DepositBasis;
import vn.sunvibe.listing.core.domain.model.listing.FeeBasis;
import vn.sunvibe.listing.core.domain.model.listing.Furnishing;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryAction;
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryEntry;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.Pets;
import vn.sunvibe.listing.core.domain.model.listing.RevisionContent;
import vn.sunvibe.listing.core.domain.model.listing.RevisionFeatures;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;
import vn.sunvibe.listing.core.domain.model.listing.ShareMode;
import vn.sunvibe.listing.core.domain.model.listing.TermsSource;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;
import vn.sunvibe.listing.core.domain.model.listing.UnitSnapshot;
import vn.sunvibe.listing.core.testsupport.InMemoryListingRepository;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;
import vn.sunvibe.listing.core.testsupport.StubAdvertisingRightPort;
import vn.sunvibe.listing.core.testsupport.StubUnitProfilePort;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.completeDraft;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.draft;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.pending;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.withStatus;

/**
 * The five revision commands (listing-publish US-2 AC-1 to AC-6; E06-S2 spec 3.1 to 3.6, E06S2-D2, E06S2-D3, E06S2-D6,
 * E06S2-D7, E06S2-D10, E06S2-D12; RD-3, RD-6, RD-10, RD-11 at service level), with fakes, in the order of checks of
 * E06S2-D3: shape, scope by the current assignee, archived, data version, revision, status, then the other module.
 */
class ListingRevisionCommandServiceTest {

    private static final String LISTING = "01JLISTING0000000000000001";
    private static final String AGREEMENT = "01AGREEMENT";
    private static final String UNIT = "01UNIT";
    private static final String SALE = "01SALE";
    private static final long VERSION = 3L;
    private static final ListingViewer SALE_VIEWER = new ListingViewer(SALE, Set.of("ROLE_SALE"));
    private static final RevisionFeaturesInput NO_FEATURES = new RevisionFeaturesInput(null, false, false, false, null,
            null, false, false, false, false, false, null, false);
    private static final AgreementTermsFacts.Terms E05_IN_EFFECT = new AgreementTermsFacts.Terms(8_000_000L, "MONTHS",
            2, null, 6, 1, List.of(new AgreementTermsFacts.Fee("Điện", "AMOUNT", 3_500L, "PER_KWH"),
            new AgreementTermsFacts.Fee("Internet", "INCLUDED", null, null)));
    private static final AgreementTermsFacts.Terms E05_PENDING = new AgreementTermsFacts.Terms(9_500_000L, "AMOUNT",
            null, 5_000_000L, 12, 3, List.of());
    private static final LocalDate START = LocalDate.parse("2026-12-01");
    private static final UnitProfileFacts PROFILE = new UnitProfileFacts(UNIT, "APARTMENT", "48", "Đà Nẵng",
            "20242", "Phường Hải Châu", 2, 1, 32.5);

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final StubAdvertisingRightPort rights = new StubAdvertisingRightPort();
    private final StubUnitProfilePort units = new StubUnitProfilePort();
    private final AtomicInteger nextId = new AtomicInteger();
    private final ListingCommandService service = new ListingCommandService(listings, rights, units,
            () -> "01ID" + nextId.incrementAndGet(), RandomGenerator.of("L64X128MixRandom"));

    @Test
    void commands_otherSaleUnknownOrMalformed_notFoundNothingWritten() {
        stored(draft(1));
        standing(false, SALE);
        ListingViewer otherSale = new ListingViewer("01OTHERSALE", Set.of("ROLE_SALE"));

        for (String listingId : List.of(LISTING, "01JLISTING0000000000000009", "not-an-id")) {
            ListingViewer viewer = LISTING.equals(listingId) ? otherSale : SALE_VIEWER;
            assertAll(listingId, everyCommand(listingId, 1, VERSION).stream()
                    .map(command -> (Executable) () -> assertThrows(ListingNotFoundException.class,
                            () -> command.run(viewer)))
                    .toList());
        }
        assertNothingWritten();
        assertFalse(listings.calls().stream().anyMatch(call -> call.contains("not-an-id")));
    }

    @Test
    void commands_managerAsAssignee_notFound() {
        stored(draft(1));
        for (String role : List.of("ROLE_SALES_MANAGER", "ROLE_ADMIN", "ROLE_LISTING_REVIEWER")) {
            standing(false, "01MANAGER");
            ListingViewer manager = new ListingViewer("01MANAGER", Set.of(role));
            for (Command command : everyCommand(LISTING, 1, VERSION)) {
                assertThrows(ListingNotFoundException.class, () -> command.run(manager), role);
            }
        }
        assertNothingWritten();
    }

    @Test
    void commands_standingGone_notFound() {
        stored(draft(1));

        for (Command command : everyCommand(LISTING, 1, VERSION)) {
            assertThrows(ListingNotFoundException.class, () -> command.run(SALE_VIEWER));
        }
        assertNothingWritten();
    }

    @Test
    void commands_agreementEnded_listingArchived() {
        stored(draft(1));
        standing(true, SALE);

        for (Command command : everyCommand(LISTING, 1, VERSION)) {
            ListingRefusedException refused = assertThrows(ListingRefusedException.class,
                    () -> command.run(SALE_VIEWER));
            assertEquals(ListingRefusedException.Reason.LISTING_ARCHIVED, refused.reason());
            assertEquals(409, refused.reason().status());
        }
        listings.put(Listing.load("01JLISTING0000000000000002", RevisionFixtures.CODE, UNIT,
                TransactionType.LONG_TERM_RENT, AGREEMENT, ListingState.ARCHIVED, SALE, RevisionFixtures.CREATED,
                RevisionFixtures.CREATED, VERSION, List.of(draft(1))));
        standing(false, SALE);
        ListingRefusedException storedArchive = assertThrows(ListingRefusedException.class,
                () -> service.saveContent(save("01JLISTING0000000000000002", 1, VERSION), SALE_VIEWER));
        assertEquals(ListingRefusedException.Reason.LISTING_ARCHIVED, storedArchive.reason());
        assertNothingWritten();
    }

    @Test
    void commands_archivedAndStale_archivedFirst() {
        stored(draft(1));
        standing(true, SALE);

        for (Command command : everyCommand(LISTING, 9, VERSION - 1)) {
            ListingRefusedException refused = assertThrows(ListingRefusedException.class,
                    () -> command.run(SALE_VIEWER));
            assertEquals(ListingRefusedException.Reason.LISTING_ARCHIVED, refused.reason());
        }
    }

    @Test
    void commands_staleDataVersion_concurrentNothingWritten() {
        stored(draft(1));
        standing(false, SALE);

        for (Command command : everyCommand(LISTING, 1, VERSION - 1)) {
            assertThrows(ListingChangedException.class, () -> command.run(SALE_VIEWER));
        }
        assertNothingWritten();
    }

    @Test
    void commands_unknownOrNullRevisionNo_notFound() {
        stored(withStatus(1, RevisionStatus.CANCELLED), pending(2));
        standing(false, SALE);

        for (Integer revisionNo : java.util.Arrays.asList(9, null)) {
            // the four commands that name a revision: an unknown number and no well-formed number answer one 404
            for (Command command : everyCommand(LISTING, revisionNo, VERSION).subList(1, 5)) {
                assertThrows(ListingNotFoundException.class, () -> command.run(SALE_VIEWER), String.valueOf(revisionNo));
            }
        }
        assertNothingWritten();
    }

    @Test
    void commands_success_versionPlusOneOneHistoryRow() {
        stored(draft(1));
        standing(false, SALE);

        RevisionCommandResult result = service.saveContent(save(LISTING, 1, VERSION), SALE_VIEWER);

        assertEquals(new RevisionCommandResult(LISTING, 1, RevisionStatus.DRAFT, VERSION + 1), result);
        assertEquals(VERSION + 1, listings.stored(LISTING).orElseThrow().rowVersion());
        assertEquals(1, listings.writtenHistory().size());
        ListingHistoryEntry entry = listings.writtenHistory().getFirst();
        assertEquals(ListingHistoryAction.REVISION_SAVED, entry.action());
        assertEquals(1, entry.revisionNoOrNull());
        assertEquals(SALE, entry.actorIdOrNull());
        assertEquals(entry.occurredAt().truncatedTo(ChronoUnit.MICROS), entry.occurredAt());
    }

    @Test
    void startRevision_afterWithdrawal_nextDraftByCaller() {
        stored(withStatus(1, RevisionStatus.CANCELLED));
        ListingViewer leader = new ListingViewer("01LEADER", Set.of("ROLE_SALE_LEADER"));
        standing(false, "01LEADER");

        RevisionCommandResult result = service.startRevision(new RevisionStartCommand(LISTING, VERSION), leader);

        assertEquals(new RevisionCommandResult(LISTING, 2, RevisionStatus.DRAFT, VERSION + 1), result);
        ListingRevision started = listings.stored(LISTING).orElseThrow().revisions().get(1);
        assertEquals(2, started.revisionNo());
        assertEquals("01LEADER", started.createdBy());
        assertEquals(RevisionContent.EMPTY, started.content());
        assertEquals(List.of(ListingHistoryAction.REVISION_STARTED),
                listings.writtenHistory().stream().map(ListingHistoryEntry::action).toList());
        assertTrue(rights.calls().stream().noneMatch(call -> call.startsWith("answer")));
    }

    @Test
    void startRevision_openRevision_openRevisionExists() {
        stored(withStatus(1, RevisionStatus.CANCELLED), pending(2));
        standing(false, SALE);

        RevisionStateException refused = assertThrows(RevisionStateException.class,
                () -> service.startRevision(new RevisionStartCommand(LISTING, VERSION), SALE_VIEWER));

        assertEquals(RevisionStateException.Reason.OPEN_REVISION_EXISTS, refused.reason());
        assertNothingWritten();
    }

    @Test
    void saveContent_draft_storedAsGivenWithFeatures() {
        stored(draft(1));
        standing(false, SALE);
        RevisionFeaturesInput every = new RevisionFeaturesInput("BASIC", true, false, true, "SHARED", "PRIVATE", true,
                false, true, false, true, "NOT_ALLOWED", true);

        service.saveContent(new RevisionSaveCommand(LISTING, 1, VERSION, RevisionFixtures.TITLE, null, every),
                SALE_VIEWER);

        assertEquals(new RevisionContent(RevisionFixtures.TITLE, null, new RevisionFeatures(Furnishing.BASIC, true,
                        false, true, ShareMode.SHARED, ShareMode.PRIVATE, true, false, true, false, true,
                        Pets.NOT_ALLOWED, true)),
                listings.stored(LISTING).orElseThrow().revisions().getFirst().content());
    }

    @Test
    void captureTerms_inEffect_copiedFieldForField() {
        stored(draft(1));
        standing(false, SALE);
        rights.terms(UNIT, AGREEMENT, new AgreementTermsFacts(false, E05_IN_EFFECT, START, E05_PENDING));

        service.captureTerms(new TermsCaptureCommand(LISTING, 1, VERSION, "IN_EFFECT"), SALE_VIEWER);

        RevisionTerms stored = listings.stored(LISTING).orElseThrow().revisions().getFirst().termsOrNull();
        assertEquals(new RevisionTerms(TermsSource.IN_EFFECT, null, stored.capturedAt(), 8_000_000L,
                new RevisionTerms.Deposit(DepositBasis.MONTHS, 2, null), 6, 1, List.of(
                new RevisionTerms.Fee("Điện", FeeBasis.AMOUNT, 3_500L, ChargeUnit.PER_KWH),
                new RevisionTerms.Fee("Internet", FeeBasis.INCLUDED, null, null))), stored);
        assertEquals(stored.capturedAt().truncatedTo(ChronoUnit.MICROS), stored.capturedAt());
        assertEquals(List.of(ListingHistoryAction.TERMS_CAPTURED),
                listings.writtenHistory().stream().map(ListingHistoryEntry::action).toList());
    }

    @Test
    void captureTerms_pendingStart_termsAndStartDate() {
        stored(draft(1));
        standing(false, SALE);
        rights.terms(UNIT, AGREEMENT, new AgreementTermsFacts(false, null, START, E05_PENDING));

        service.captureTerms(new TermsCaptureCommand(LISTING, 1, VERSION, "PENDING_START"), SALE_VIEWER);

        RevisionTerms stored = listings.stored(LISTING).orElseThrow().revisions().getFirst().termsOrNull();
        assertEquals(new RevisionTerms(TermsSource.PENDING_START, START, stored.capturedAt(), 9_500_000L,
                new RevisionTerms.Deposit(DepositBasis.AMOUNT, null, 5_000_000L), 12, 3, List.of()), stored);
    }

    @Test
    void captureTerms_absentEndedOrMissingSource_termsSourceAbsent() {
        stored(draft(1));
        standing(false, SALE);
        List<Runnable> answers = List.of(
                () -> { },
                () -> rights.terms(UNIT, AGREEMENT, new AgreementTermsFacts(true, E05_IN_EFFECT, null, null)),
                () -> rights.terms(UNIT, AGREEMENT, new AgreementTermsFacts(false, E05_IN_EFFECT, null, null)));
        List<String> sources = List.of("IN_EFFECT", "IN_EFFECT", "PENDING_START");

        for (int index = 0; index < answers.size(); index++) {
            answers.get(index).run();
            String source = sources.get(index);
            ListingRefusedException refused = assertThrows(ListingRefusedException.class,
                    () -> service.captureTerms(new TermsCaptureCommand(LISTING, 1, VERSION, source), SALE_VIEWER));
            assertEquals(ListingRefusedException.Reason.TERMS_SOURCE_ABSENT, refused.reason());
            assertEquals(409, refused.reason().status());
        }
        assertNothingWritten();
    }

    @Test
    void captureTerms_notDraft_refusedBeforeReadingE05() {
        stored(pending(1));
        standing(false, SALE);
        rights.terms(UNIT, AGREEMENT, new AgreementTermsFacts(false, E05_IN_EFFECT, null, null));

        RevisionStateException refused = assertThrows(RevisionStateException.class,
                () -> service.captureTerms(new TermsCaptureCommand(LISTING, 1, VERSION, "IN_EFFECT"), SALE_VIEWER));

        assertEquals(RevisionStateException.Reason.REVISION_NOT_DRAFT, refused.reason());
        assertTrue(rights.calls().stream().noneMatch(call -> call.startsWith("terms")), rights.calls().toString());
        assertNothingWritten();
    }

    @Test
    void captureTerms_brokenContract_illegalStateNothingWritten() {
        stored(draft(1));
        standing(false, SALE);
        for (AgreementTermsFacts.Terms broken : List.of(
                new AgreementTermsFacts.Terms(8_000_000L, "MONTHS", 2, null, 6, 1,
                        List.of(new AgreementTermsFacts.Fee("Fee", "FREE", null, null))),
                new AgreementTermsFacts.Terms(0L, "MONTHS", 2, null, 6, 1, List.of()),
                new AgreementTermsFacts.Terms(8_000_000L, "MONTHS", 2, null, null, 1, List.of()))) {
            rights.terms(UNIT, AGREEMENT, new AgreementTermsFacts(false, broken, null, null));

            assertThrows(IllegalStateException.class,
                    () -> service.captureTerms(new TermsCaptureCommand(LISTING, 1, VERSION, "IN_EFFECT"), SALE_VIEWER));
        }
        assertNothingWritten();
    }

    @Test
    void submit_complete_pendingWithUnitSnapshot() {
        stored(completeDraft(1));
        standing(false, SALE);
        units.profile(PROFILE);

        RevisionCommandResult result = service.submit(new RevisionSubmitCommand(LISTING, 1, VERSION), SALE_VIEWER);

        assertEquals(new RevisionCommandResult(LISTING, 1, RevisionStatus.PENDING_REVIEW, VERSION + 1), result);
        Listing stored = listings.stored(LISTING).orElseThrow();
        ListingRevision submitted = stored.revisions().getFirst();
        assertEquals(new UnitSnapshot("APARTMENT", "48", "Đà Nẵng", "20242",
                "Phường Hải Châu", 2, 1, 32.5), submitted.unitSnapshotOrNull());
        assertEquals(SALE, submitted.submittedByOrNull());
        assertEquals(ListingState.UNPUBLISHED, stored.state());
        assertEquals(List.of("profile " + UNIT), units.calls());
    }

    @Test
    void submit_violations_refusedBeforeProfileRead() {
        stored(draft(1));
        standing(false, SALE);
        units.profile(PROFILE);

        RevisionNotSubmittableException refused = assertThrows(RevisionNotSubmittableException.class,
                () -> service.submit(new RevisionSubmitCommand(LISTING, 1, VERSION), SALE_VIEWER));

        assertEquals(3, refused.violations().size());
        assertEquals(List.of(), units.calls());
        assertNothingWritten();
    }

    @Test
    void submit_noUnitProfile_illegalStateNothingWritten() {
        stored(completeDraft(1));
        standing(false, SALE);

        assertThrows(IllegalStateException.class,
                () -> service.submit(new RevisionSubmitCommand(LISTING, 1, VERSION), SALE_VIEWER));

        assertNothingWritten();
    }

    @Test
    void submit_unknownAssetType_illegalStateNothingWritten() {
        stored(completeDraft(1));
        standing(false, SALE);
        units.profile(new UnitProfileFacts(UNIT, "CASTLE", null, null, null, null, 2, 1, 32.5));

        assertThrows(IllegalStateException.class,
                () -> service.submit(new RevisionSubmitCommand(LISTING, 1, VERSION), SALE_VIEWER));

        assertNothingWritten();
    }

    @Test
    void withdraw_pending_cancelled() {
        stored(pending(1));
        standing(false, SALE);

        RevisionCommandResult result = service.withdraw(new RevisionWithdrawCommand(LISTING, 1, VERSION), SALE_VIEWER);

        assertEquals(new RevisionCommandResult(LISTING, 1, RevisionStatus.CANCELLED, VERSION + 1), result);
        ListingRevision withdrawn = listings.stored(LISTING).orElseThrow().revisions().getFirst();
        assertEquals(RevisionStatus.CANCELLED, withdrawn.status());
        assertEquals(RevisionFixtures.completeContent(), withdrawn.content());
        assertEquals(List.of(ListingHistoryAction.REVISION_WITHDRAWN),
                listings.writtenHistory().stream().map(ListingHistoryEntry::action).toList());
    }

    @Test
    void withdraw_draft_revisionNotPending() {
        stored(draft(1));
        standing(false, SALE);

        RevisionStateException refused = assertThrows(RevisionStateException.class,
                () -> service.withdraw(new RevisionWithdrawCommand(LISTING, 1, VERSION), SALE_VIEWER));

        assertEquals(RevisionStateException.Reason.REVISION_NOT_PENDING, refused.reason());
        assertNull(listings.stored(LISTING).orElseThrow().revisions().getFirst().unitSnapshotOrNull());
        assertNothingWritten();
    }

    /** One of the five commands, run as a viewer. */
    private interface Command {
        void run(ListingViewer viewer);
    }

    /** The five commands on the listing, the four targeted ones on {@code revisionNoOrNull}, in a fixed order. */
    private List<Command> everyCommand(String listingId, Integer revisionNoOrNull, long dataVersion) {
        return List.of(
                viewer -> service.startRevision(new RevisionStartCommand(listingId, dataVersion), viewer),
                viewer -> service.saveContent(save(listingId, revisionNoOrNull, dataVersion), viewer),
                viewer -> service.captureTerms(new TermsCaptureCommand(listingId, revisionNoOrNull, dataVersion,
                        "IN_EFFECT"), viewer),
                viewer -> service.submit(new RevisionSubmitCommand(listingId, revisionNoOrNull, dataVersion), viewer),
                viewer -> service.withdraw(new RevisionWithdrawCommand(listingId, revisionNoOrNull, dataVersion),
                        viewer));
    }

    private static RevisionSaveCommand save(String listingId, Integer revisionNoOrNull, long dataVersion) {
        return new RevisionSaveCommand(listingId, revisionNoOrNull, dataVersion, RevisionFixtures.TITLE, null,
                NO_FEATURES);
    }

    private void stored(ListingRevision... revisions) {
        listings.put(Listing.load(LISTING, RevisionFixtures.CODE, UNIT, TransactionType.LONG_TERM_RENT, AGREEMENT,
                ListingState.UNPUBLISHED, SALE, RevisionFixtures.CREATED, RevisionFixtures.CREATED, VERSION,
                List.of(revisions)));
    }

    private void standing(boolean ended, String assignee) {
        rights.standing(new AgreementStandingFacts(AGREEMENT, UNIT, "LONG_TERM_RENT", ended, assignee));
    }

    private void assertNothingWritten() {
        assertTrue(listings.calls().stream().noneMatch(call -> call.startsWith("update")), listings.calls().toString());
        assertTrue(listings.writtenHistory().isEmpty());
        assertTrue(rights.calls().stream().noneMatch(call -> call.startsWith("answer")), rights.calls().toString());
    }
}
