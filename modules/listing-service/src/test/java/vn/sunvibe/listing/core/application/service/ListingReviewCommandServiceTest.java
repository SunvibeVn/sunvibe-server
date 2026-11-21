package vn.sunvibe.listing.core.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import vn.sunvibe.listing.core.application.command.RevisionDecisionCommand;
import vn.sunvibe.listing.core.application.command.UnitLocationOpenCommand;
import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.exception.ListingRefusedException;
import vn.sunvibe.listing.core.application.exception.ReviewerNotIndependentException;
import vn.sunvibe.listing.core.application.query.RevisionCommandResult;
import vn.sunvibe.listing.core.application.query.UnitLocationResult;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementTermsFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitAddressFacts;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;
import vn.sunvibe.listing.core.domain.exception.ListingChangedException;
import vn.sunvibe.listing.core.domain.exception.RevisionStateException;
import vn.sunvibe.listing.core.domain.model.listing.ChangeReason;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingAccessLog;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.RejectionGroup;
import vn.sunvibe.listing.core.domain.model.listing.ReviewCheck;
import vn.sunvibe.listing.core.domain.model.listing.ReviewIneligibility;
import vn.sunvibe.listing.core.domain.model.listing.ReviewOutcome;
import vn.sunvibe.listing.core.domain.model.listing.RevisionDecision;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;
import vn.sunvibe.listing.core.testsupport.InMemoryListingAccessLogRepository;
import vn.sunvibe.listing.core.testsupport.InMemoryListingRepository;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;
import vn.sunvibe.listing.core.testsupport.StubAdvertisingRightPort;
import vn.sunvibe.listing.core.testsupport.StubUnitInventoryPort;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CODE;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CREATED;

/**
 * The review's commands over the in-memory listings and E05's and property's stubs (listing-publish US-4 AC-2 to AC-5;
 * E06-S4 spec sections 3.3, 3.4, E06S4-D3 to D9, D12, D20; plan E06S4-P1, P3, P6, P8; RV-2, RV-5, RV-7, RV-11 service
 * half): the checks of E06S4-D5 in order, the first failing one answering; the authors and the assigned Sale refusing a
 * reviewer whatever roles; the terms check of an approval only; the location answered and logged only to an eligible
 * reviewer; nothing written outside listing.
 */
class ListingReviewCommandServiceTest {

    private static final String LISTING = "01LISTING00000000000000001";
    private static final String UNKNOWN = "01LISTING00000000000000009";
    private static final String AGREEMENT = "01AGREEMENT000000000000001";
    private static final String UNIT = "01UNIT00000000000000000001";
    private static final String SALE = "01SALE00000000000000000001";
    private static final String REVIEWER = "01REVIEWER0000000000000001";
    private static final long VERSION = 7L;
    private static final long LOG_ID = 7_301_234_567_890L;
    private static final ListingViewer REVIEWER_VIEWER = new ListingViewer(REVIEWER, Set.of("ROLE_LISTING_REVIEWER"));
    private static final ListingViewer ADMIN_VIEWER = new ListingViewer(REVIEWER, Set.of("ROLE_ADMIN",
            "ROLE_SALE_LEADER", "ROLE_SALES_MANAGER"));
    private static final String NOTE = "  Mô tả thiếu diện tích phòng.\r\n";

    private InMemoryListingRepository listings = new InMemoryListingRepository();
    private final InMemoryListingAccessLogRepository accessLogs = new InMemoryListingAccessLogRepository();
    private StubAdvertisingRightPort rights = new StubAdvertisingRightPort();
    private final StubUnitInventoryPort units = new StubUnitInventoryPort();
    private final AtomicInteger nextId = new AtomicInteger();

    private ListingReviewCommandService service() {
        return new ListingReviewCommandService(listings, accessLogs, rights, units,
                () -> "01ID" + String.format("%022d", nextId.incrementAndGet()), () -> LOG_ID);
    }

    @Test
    void decide_stepsInOrder_firstFailingAnswers() {
        Map<String, Executable> steps = new LinkedHashMap<>();
        Map<String, Class<? extends RuntimeException>> expected = new LinkedHashMap<>();
        // (3) an unknown listing, every later step broken too
        steps.put("listing", () -> {
            world(RevisionFixtures.withStatus(1, RevisionStatus.DRAFT), true, REVIEWER, Set.of(REVIEWER), false);
            service().decide(approve(UNKNOWN, 9, VERSION + 1), REVIEWER_VIEWER);
        });
        expected.put("listing", ListingNotFoundException.class);
        // (4) archived, the agreement ended; stale version, unknown revision, not pending, author, terms differ
        steps.put("archived", () -> {
            world(RevisionFixtures.withStatus(1, RevisionStatus.DRAFT), true, REVIEWER, Set.of(REVIEWER), false);
            service().decide(approve(LISTING, 9, VERSION + 1), REVIEWER_VIEWER);
        });
        expected.put("archived", ListingRefusedException.class);
        // (5) a stale data version; unknown revision, not pending, author, terms differ
        steps.put("version", () -> {
            world(RevisionFixtures.withStatus(1, RevisionStatus.DRAFT), false, REVIEWER, Set.of(REVIEWER), false);
            service().decide(approve(LISTING, 9, VERSION + 1), REVIEWER_VIEWER);
        });
        expected.put("version", ListingChangedException.class);
        // (6) an unknown revision number
        steps.put("revision", () -> {
            world(RevisionFixtures.withStatus(1, RevisionStatus.DRAFT), false, REVIEWER, Set.of(REVIEWER), false);
            service().decide(approve(LISTING, 9, VERSION), REVIEWER_VIEWER);
        });
        expected.put("revision", ListingNotFoundException.class);
        // (7) the revision not pending; author, assignee, terms differ
        steps.put("status", () -> {
            world(RevisionFixtures.withStatus(1, RevisionStatus.DRAFT), false, REVIEWER, Set.of(REVIEWER), false);
            service().decide(approve(LISTING, 1, VERSION), REVIEWER_VIEWER);
        });
        expected.put("status", RevisionStateException.class);
        // (8) the reviewer is an author and the assignee; terms differ
        steps.put("independence", () -> {
            world(RevisionFixtures.pending(1), false, REVIEWER, Set.of(REVIEWER), false);
            service().decide(approve(LISTING, 1, VERSION), REVIEWER_VIEWER);
        });
        expected.put("independence", ReviewerNotIndependentException.class);
        // (9) only the terms differ
        steps.put("terms", () -> {
            world(RevisionFixtures.pending(1), false, SALE, Set.of(SALE), false);
            service().decide(approve(LISTING, 1, VERSION), REVIEWER_VIEWER);
        });
        expected.put("terms", ListingRefusedException.class);

        for (Map.Entry<String, Executable> step : steps.entrySet()) {
            RuntimeException refused = assertThrows(expected.get(step.getKey()), step.getValue(), step.getKey());
            switch (step.getKey()) {
                case "archived" -> assertEquals(ListingRefusedException.Reason.LISTING_ARCHIVED,
                        ((ListingRefusedException) refused).reason());
                case "status" -> assertEquals(RevisionStateException.Reason.REVISION_NOT_PENDING,
                        ((RevisionStateException) refused).reason());
                case "independence" -> assertEquals(List.of(ReviewIneligibility.AUTHOR, ReviewIneligibility.ASSIGNED_SALE),
                        ((ReviewerNotIndependentException) refused).reasons());
                case "terms" -> assertEquals(ListingRefusedException.Reason.REVISION_TERMS_MISMATCH,
                        ((ListingRefusedException) refused).reason());
                default -> assertInstanceOf(expected.get(step.getKey()), refused);
            }
            assertTrue(listings.decisions().isEmpty(), step.getKey());
            assertFalse(listings.calls().stream().anyMatch(call -> call.startsWith("update")), step.getKey());
        }
    }

    @Test
    void decide_authorOrAssignee_reasonsInOrder() {
        world(RevisionFixtures.pending(1), false, SALE, Set.of(REVIEWER), true);
        assertEquals(List.of(ReviewIneligibility.AUTHOR), refusal(REVIEWER_VIEWER).reasons());

        world(RevisionFixtures.pending(1), false, REVIEWER, Set.of(SALE), true);
        assertEquals(List.of(ReviewIneligibility.ASSIGNED_SALE), refusal(REVIEWER_VIEWER).reasons());

        world(RevisionFixtures.pending(1), false, REVIEWER, Set.of(REVIEWER, SALE), true);
        assertEquals(List.of(ReviewIneligibility.AUTHOR, ReviewIneligibility.ASSIGNED_SALE),
                refusal(REVIEWER_VIEWER).reasons());
        assertTrue(listings.decisions().isEmpty());
    }

    @Test
    void decide_copySourceAuthor_refused() {
        // the authors statement follows the copy chain: the reviewer wrote the revision this one was copied from
        world(RevisionFixtures.pending(2), false, SALE, Set.of(SALE, REVIEWER), true);

        ReviewerNotIndependentException refused = assertThrows(ReviewerNotIndependentException.class,
                () -> service().decide(approve(LISTING, 2, VERSION), REVIEWER_VIEWER));

        assertEquals(List.of(ReviewIneligibility.AUTHOR), refused.reasons());
        assertTrue(listings.calls().contains("findAuthors [R2]"), listings.calls().toString());
    }

    @Test
    void decide_adminNeitherAuthorNorAssignee_approved() {
        world(RevisionFixtures.pending(1), false, SALE, Set.of(SALE), true);

        RevisionCommandResult result = service().decide(approve(LISTING, 1, VERSION), ADMIN_VIEWER);

        assertEquals(new RevisionCommandResult(LISTING, 1, RevisionStatus.APPROVED, VERSION + 1), result);
        assertEquals(RevisionStatus.APPROVED, listings.stored(LISTING).orElseThrow().revision(1).orElseThrow().status());
        assertEquals(1, listings.decisions().size());
        RevisionDecision decision = listings.decisions().getFirst();
        assertAll(
                () -> assertEquals("R1", decision.revisionId()),
                () -> assertEquals(ReviewOutcome.APPROVED, decision.outcome()),
                () -> assertEquals(REVIEWER, decision.decidedBy()),
                () -> assertEquals(1, decision.checklistVersionOrNull()));
        assertEquals(VERSION + 1, listings.stored(LISTING).orElseThrow().rowVersion());
    }

    @Test
    void decide_absentStanding_listingArchived() {
        world(RevisionFixtures.pending(1), false, SALE, Set.of(SALE), true);
        // E05 no longer returns the agreement's standing: read as ended (plan E06S4-P3)
        rights = new StubAdvertisingRightPort();
        rights.terms(UNIT, AGREEMENT, new AgreementTermsFacts(false, snapshotTerms(), null, null));

        ListingRefusedException refused = assertThrows(ListingRefusedException.class,
                () -> service().decide(approve(LISTING, 1, VERSION), REVIEWER_VIEWER));

        assertEquals(ListingRefusedException.Reason.LISTING_ARCHIVED, refused.reason());
    }

    @Test
    void decide_versionMovedBetweenReads_concurrentModification() {
        world(RevisionFixtures.pending(1), false, SALE, Set.of(SALE), true);
        Listing stored = listings.stored(LISTING).orElseThrow();
        listings = new InMemoryListingRepository() {
            @Override
            public Optional<Listing> findForReview(String listingId, int revisionNo) {
                // another command commits between the row read and the working set read
                moveVersion(listingId);
                return super.findForReview(listingId, revisionNo);
            }
        };
        listings.put(stored);

        assertThrows(ListingChangedException.class,
                () -> service().decide(approve(LISTING, 1, VERSION), REVIEWER_VIEWER));

        assertTrue(listings.decisions().isEmpty());
        assertEquals(RevisionStatus.PENDING_REVIEW,
                listings.stored(LISTING).orElseThrow().revision(1).orElseThrow().status());
    }

    @Test
    void decide_approvalTermsEqualNeither_termsMismatchNothingWritten() {
        world(RevisionFixtures.pending(1), false, SALE, Set.of(SALE), false);

        ListingRefusedException refused = assertThrows(ListingRefusedException.class,
                () -> service().decide(approve(LISTING, 1, VERSION), REVIEWER_VIEWER));

        assertEquals(ListingRefusedException.Reason.REVISION_TERMS_MISMATCH, refused.reason());
        assertTrue(listings.decisions().isEmpty());
        assertFalse(listings.calls().stream().anyMatch(call -> call.startsWith("update")));
        assertEquals(VERSION, listings.stored(LISTING).orElseThrow().rowVersion());
    }

    @Test
    void decide_approvalNoE05Terms_termsMismatch() {
        world(RevisionFixtures.pending(1), false, SALE, Set.of(SALE), false);
        rights.terms(UNIT, AGREEMENT, null);

        ListingRefusedException refused = assertThrows(ListingRefusedException.class,
                () -> service().decide(approve(LISTING, 1, VERSION), REVIEWER_VIEWER));

        assertEquals(ListingRefusedException.Reason.REVISION_TERMS_MISMATCH, refused.reason());
    }

    @Test
    void decide_approvalEqualsPendingVersion_approved() {
        world(RevisionFixtures.pending(1), false, SALE, Set.of(SALE), false);
        rights.terms(UNIT, AGREEMENT, new AgreementTermsFacts(false, otherTerms(), LocalDate.parse("2026-12-01"),
                snapshotTerms()));

        RevisionCommandResult result = service().decide(approve(LISTING, 1, VERSION), REVIEWER_VIEWER);

        assertEquals(RevisionStatus.APPROVED, result.status());
    }

    @Test
    void decide_returnWithTermsMismatchReason_writtenWithoutTermsRead() {
        world(RevisionFixtures.pending(1), false, SALE, Set.of(SALE), false);

        RevisionCommandResult result = service().decide(new RevisionDecisionCommand(LISTING, 1, VERSION,
                ReviewOutcome.NEEDS_CHANGES, List.of(), List.of(ChangeReason.TERMS_MISMATCH), null, NOTE),
                REVIEWER_VIEWER);

        assertEquals(new RevisionCommandResult(LISTING, 1, RevisionStatus.NEEDS_CHANGES, VERSION + 1), result);
        RevisionDecision decision = listings.decisions().getFirst();
        assertEquals(List.of(ChangeReason.TERMS_MISMATCH), decision.changeReasons());
        assertEquals("Mô tả thiếu diện tích phòng.", decision.noteOrNull().text());
        assertFalse(rights.calls().stream().anyMatch(call -> call.startsWith("terms")), rights.calls().toString());
    }

    @Test
    void decide_neverWritesOutsideListing() {
        world(RevisionFixtures.pending(1), false, SALE, Set.of(SALE), true);

        service().decide(approve(LISTING, 1, VERSION), REVIEWER_VIEWER);
        world(RevisionFixtures.pending(1), false, SALE, Set.of(SALE), true);
        service().decide(new RevisionDecisionCommand(LISTING, 1, VERSION, ReviewOutcome.REJECTED, List.of(), List.of(),
                RejectionGroup.WRONG_UNIT, NOTE), REVIEWER_VIEWER);

        assertTrue(rights.calls().stream().allMatch(call -> call.startsWith("standings") || call.startsWith("terms")),
                rights.calls().toString());
        assertEquals(List.of(), units.calls());
        assertEquals(List.of(), accessLogs.recorded());
        assertEquals(ListingState.UNPUBLISHED, listings.stored(LISTING).orElseThrow().state());
    }

    @Test
    void openUnitLocation_eligible_locationAndOneLog() {
        world(RevisionFixtures.pending(1), false, SALE, Set.of(SALE), true);
        units.address(new UnitAddressFacts(UNIT, "P101", "P101 · Floor 1", "12 Tran Phu", "Hai Chau"));
        Instant before = Instant.now();

        UnitLocationResult result = service().openUnitLocation(new UnitLocationOpenCommand(LISTING, 1),
                REVIEWER_VIEWER);

        Instant after = Instant.now();
        assertEquals(new UnitLocationResult("P101", "P101 · Floor 1", "12 Tran Phu", "Hai Chau"), result);
        assertEquals(1, accessLogs.recorded().size());
        ListingAccessLog log = accessLogs.recorded().getFirst();
        assertAll(
                () -> assertEquals(LOG_ID, log.id()),
                () -> assertEquals(REVIEWER, log.userId()),
                () -> assertEquals(LISTING, log.listingId()),
                () -> assertEquals(1, log.revisionNo()),
                () -> assertEquals(ListingAccessLog.UNIT_LOCATION_OPENED, log.action()),
                () -> assertFalse(log.accessedAt().isBefore(before.truncatedTo(java.time.temporal.ChronoUnit.MICROS))),
                () -> assertFalse(log.accessedAt().isAfter(after)));
        assertEquals(List.of("address " + UNIT), units.calls());
        assertEquals("UnitLocationResult[hidden]", result.toString());
    }

    @Test
    void openUnitLocation_notEligible_noLogNoAddressCall() {
        units.address(new UnitAddressFacts(UNIT, "P101", "P101 · Floor 1", "12 Tran Phu", "Hai Chau"));
        Map<String, Executable> cases = new LinkedHashMap<>();
        cases.put("author", () -> {
            world(RevisionFixtures.pending(1), false, SALE, Set.of(REVIEWER), true);
            assertEquals(List.of(ReviewIneligibility.AUTHOR), assertThrows(ReviewerNotIndependentException.class,
                    () -> service().openUnitLocation(new UnitLocationOpenCommand(LISTING, 1), REVIEWER_VIEWER))
                    .reasons());
        });
        cases.put("assignee", () -> {
            world(RevisionFixtures.pending(1), false, REVIEWER, Set.of(SALE), true);
            assertEquals(List.of(ReviewIneligibility.ASSIGNED_SALE), assertThrows(ReviewerNotIndependentException.class,
                    () -> service().openUnitLocation(new UnitLocationOpenCommand(LISTING, 1), ADMIN_VIEWER)).reasons());
        });
        cases.put("decided", () -> {
            world(RevisionFixtures.withStatus(1, RevisionStatus.NEEDS_CHANGES), false, SALE, Set.of(SALE), true);
            assertEquals(RevisionStateException.Reason.REVISION_NOT_PENDING, assertThrows(RevisionStateException.class,
                    () -> service().openUnitLocation(new UnitLocationOpenCommand(LISTING, 1), REVIEWER_VIEWER))
                    .reason());
        });
        cases.put("archived", () -> {
            world(RevisionFixtures.pending(1), true, SALE, Set.of(SALE), true);
            assertEquals(ListingRefusedException.Reason.LISTING_ARCHIVED, assertThrows(ListingRefusedException.class,
                    () -> service().openUnitLocation(new UnitLocationOpenCommand(LISTING, 1), REVIEWER_VIEWER))
                    .reason());
        });
        cases.put("unknown revision", () -> {
            world(RevisionFixtures.pending(1), false, SALE, Set.of(SALE), true);
            assertThrows(ListingNotFoundException.class,
                    () -> service().openUnitLocation(new UnitLocationOpenCommand(LISTING, 2), REVIEWER_VIEWER));
        });

        for (Map.Entry<String, Executable> refusal : cases.entrySet()) {
            assertAll(refusal.getKey(), refusal.getValue());
        }

        assertEquals(List.of(), accessLogs.recorded());
        assertEquals(List.of(), units.calls());
    }

    @Test
    void openUnitLocation_unitUnknownToProperty_illegalStateNoLog() {
        world(RevisionFixtures.pending(1), false, SALE, Set.of(SALE), true);

        assertThrows(IllegalStateException.class,
                () -> service().openUnitLocation(new UnitLocationOpenCommand(LISTING, 1), REVIEWER_VIEWER));

        assertEquals(List.of(), accessLogs.recorded());
        assertEquals(List.of("address " + UNIT), units.calls());
    }

    /** A 403 REVIEWER_NOT_INDEPENDENT of an approval by the reviewer. */
    private ReviewerNotIndependentException refusal(ListingViewer viewer) {
        return assertThrows(ReviewerNotIndependentException.class,
                () -> service().decide(approve(LISTING, 1, VERSION), viewer));
    }

    /**
     * A fresh world: the listing at row version 7 with the revision given, E05's standing (ended or not, with its
     * assignee), the revision's authors, and E05's terms equal to the snapshot's or not.
     */
    private void world(ListingRevision revision, boolean ended, String assignee, Set<String> authors,
                       boolean termsEqual) {
        listings = new InMemoryListingRepository();
        listings.put(listing(revision));
        listings.putAuthors(revision.id(), authors);
        rights.standing(new AgreementStandingFacts(AGREEMENT, UNIT, "LONG_TERM_RENT", ended, assignee));
        rights.terms(UNIT, AGREEMENT, new AgreementTermsFacts(false, termsEqual ? snapshotTerms() : otherTerms(), null,
                null));
    }

    private static Listing listing(ListingRevision revision) {
        List<ListingRevision> revisions = new ArrayList<>();
        for (int number = 1; number < revision.revisionNo(); number++) {
            revisions.add(RevisionFixtures.withStatus(number, RevisionStatus.CANCELLED));
        }
        revisions.add(revision);
        return Listing.load(LISTING, CODE, UNIT, TransactionType.LONG_TERM_RENT, AGREEMENT, ListingState.UNPUBLISHED,
                SALE, CREATED, CREATED, VERSION, revisions);
    }

    private static RevisionDecisionCommand approve(String listingId, Integer revisionNo, long dataVersion) {
        return new RevisionDecisionCommand(listingId, revisionNo, dataVersion, ReviewOutcome.APPROVED,
                ReviewCheck.VERSION_1, List.of(), null, null);
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
                new AgreementTermsFacts.Fee("Điện", "AMOUNT", 3_500L, "PER_KWH"),
                new AgreementTermsFacts.Fee("Internet", "INCLUDED", null, null),
                new AgreementTermsFacts.Fee("Nước", "PROVIDER_TARIFF", null, null)));
    }
}
