package vn.sunvibe.listing.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.application.command.RevisionCopyCommand;
import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.exception.ListingRefusedException;
import vn.sunvibe.listing.core.application.query.RevisionCommandResult;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;
import vn.sunvibe.listing.core.domain.exception.ListingChangedException;
import vn.sunvibe.listing.core.domain.exception.RevisionStateException;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryAction;
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryEntry;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;
import vn.sunvibe.listing.core.testsupport.InMemoryListingRepository;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;
import vn.sunvibe.listing.core.testsupport.StubAdvertisingRightPort;
import vn.sunvibe.listing.core.testsupport.StubUnitProfilePort;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CREATED;

/**
 * The copy of a Needs changes revision over the in-memory listings (listing-publish US-2 AC-6, BR-6; E06-S4 spec section
 * 3.6, E06S4-D14; RV-10 service half): the drafter's new Draft with the source's content and terms and its source number,
 * the revision commands' checks in their order, then the copy's own refusals; nothing written when refused.
 */
class ListingCopyCommandServiceTest {

    private static final String LISTING = "01LISTING00000000000000001";
    private static final String AGREEMENT = "01AGREEMENT000000000000001";
    private static final String UNIT = "01UNIT00000000000000000001";
    private static final String SALE = "01SALE00000000000000000001";
    private static final long VERSION = 7L;
    private static final ListingViewer SALE_VIEWER = new ListingViewer(SALE, Set.of("ROLE_SALE"));

    private InMemoryListingRepository listings = new InMemoryListingRepository();
    private StubAdvertisingRightPort rights = new StubAdvertisingRightPort();
    private final AtomicInteger nextId = new AtomicInteger();

    private ListingCommandService service() {
        return new ListingCommandService(listings, rights, new StubUnitProfilePort(),
                () -> "01ID" + String.format("%022d", nextId.incrementAndGet()), RandomGenerator.of("L64X128MixRandom"));
    }

    @Test
    void copyRevision_drafter_newDraftResult() {
        world(false, SALE, withStatus(1, RevisionStatus.REJECTED), needsChanges(2));

        RevisionCommandResult result = service().copyRevision(new RevisionCopyCommand(LISTING, 2, VERSION),
                SALE_VIEWER);

        assertEquals(new RevisionCommandResult(LISTING, 3, RevisionStatus.DRAFT, VERSION + 1), result);
        Listing stored = listings.stored(LISTING).orElseThrow();
        ListingRevision copy = stored.revision(3).orElseThrow();
        assertAll(
                () -> assertEquals(RevisionStatus.DRAFT, copy.status()),
                () -> assertEquals(SALE, copy.createdBy()),
                () -> assertEquals(RevisionFixtures.completeContent(), copy.content()),
                () -> assertEquals(RevisionFixtures.inEffectTerms(), copy.termsOrNull()),
                () -> assertEquals(2, copy.copiedFromRevisionNoOrNull()),
                () -> assertNull(copy.submittedAtOrNull()),
                () -> assertEquals(RevisionStatus.NEEDS_CHANGES, stored.revision(2).orElseThrow().status()),
                () -> assertEquals(VERSION + 1, stored.rowVersion()));
        assertEquals(1, listings.writtenHistory().size());
        ListingHistoryEntry entry = listings.writtenHistory().getFirst();
        assertEquals(ListingHistoryAction.REVISION_COPIED, entry.action());
        assertEquals(3, entry.revisionNoOrNull());
        assertEquals(SALE, entry.actorIdOrNull());
    }

    @Test
    void copyRevision_checksInOrder_firstFailingAnswers() {
        // (1) an unknown listing, every later check broken too
        world(true, SALE, withStatus(1, RevisionStatus.REJECTED), withStatus(2, RevisionStatus.DRAFT));
        assertThrows(ListingNotFoundException.class, () -> service().copyRevision(
                new RevisionCopyCommand("01LISTING00000000000000009", 9, VERSION - 1), SALE_VIEWER), "listing");
        assertNothingWritten();

        // (2) archived: the agreement ended; stale version, unknown revision
        assertEquals(ListingRefusedException.Reason.LISTING_ARCHIVED, assertThrows(ListingRefusedException.class,
                () -> service().copyRevision(new RevisionCopyCommand(LISTING, 9, VERSION - 1), SALE_VIEWER)).reason());
        assertNothingWritten();

        // (3) a stale data version; unknown revision
        world(false, SALE, withStatus(1, RevisionStatus.REJECTED), withStatus(2, RevisionStatus.CANCELLED),
                withStatus(3, RevisionStatus.DRAFT));
        assertThrows(ListingChangedException.class,
                () -> service().copyRevision(new RevisionCopyCommand(LISTING, 9, VERSION - 1), SALE_VIEWER));
        assertNothingWritten();

        // (4) an unknown revision number, or none well-formed
        for (Integer revisionNo : java.util.Arrays.asList(9, null)) {
            assertThrows(ListingNotFoundException.class, () -> service().copyRevision(
                    new RevisionCopyCommand(LISTING, revisionNo, VERSION), SALE_VIEWER), String.valueOf(revisionNo));
        }
        assertNothingWritten();

        // (5) the source is not Needs changes (Rejected, withdrawn, the open Draft), an open revision existing too
        for (int sourceNo : List.of(1, 2, 3)) {
            assertEquals(RevisionStateException.Reason.REVISION_NOT_COPYABLE, assertThrows(
                    RevisionStateException.class, () -> service().copyRevision(
                            new RevisionCopyCommand(LISTING, sourceNo, VERSION), SALE_VIEWER)).reason());
        }
        assertNothingWritten();

        // (6) a Needs changes source while a Draft is open
        world(false, SALE, needsChanges(1), withStatus(2, RevisionStatus.DRAFT));
        assertEquals(RevisionStateException.Reason.OPEN_REVISION_EXISTS, assertThrows(RevisionStateException.class,
                () -> service().copyRevision(new RevisionCopyCommand(LISTING, 1, VERSION), SALE_VIEWER)).reason());
        assertNothingWritten();
    }

    @Test
    void copyRevision_managerOrNotAssignee_notFound() {
        for (String role : List.of("ROLE_SALES_MANAGER", "ROLE_ADMIN", "ROLE_LISTING_REVIEWER")) {
            world(false, "01MANAGER0000000000000001", needsChanges(1));
            ListingViewer manager = new ListingViewer("01MANAGER0000000000000001", Set.of(role));
            assertThrows(ListingNotFoundException.class,
                    () -> service().copyRevision(new RevisionCopyCommand(LISTING, 1, VERSION), manager), role);
            assertNothingWritten();
        }

        world(false, SALE, needsChanges(1));
        ListingViewer otherSale = new ListingViewer("01SALE00000000000000000002", Set.of("ROLE_SALE"));
        assertThrows(ListingNotFoundException.class,
                () -> service().copyRevision(new RevisionCopyCommand(LISTING, 1, VERSION), otherSale), "other sale");
        assertNothingWritten();

        // a standing E05 no longer returns names nobody
        rights = new StubAdvertisingRightPort();
        assertThrows(ListingNotFoundException.class,
                () -> service().copyRevision(new RevisionCopyCommand(LISTING, 1, VERSION), SALE_VIEWER), "gone");
        assertNothingWritten();
    }

    /** A fresh world: the listing at row version 7 with the revisions given, E05's standing (ended or not, assignee). */
    private void world(boolean ended, String assignee, ListingRevision... revisions) {
        listings = new InMemoryListingRepository();
        rights = new StubAdvertisingRightPort();
        listings.put(Listing.load(LISTING, RevisionFixtures.CODE, UNIT, TransactionType.LONG_TERM_RENT, AGREEMENT,
                ListingState.UNPUBLISHED, SALE, CREATED, CREATED, VERSION, List.of(revisions)));
        rights.standing(new AgreementStandingFacts(AGREEMENT, UNIT, "LONG_TERM_RENT", ended, assignee));
    }

    /** A returned revision with a complete content, an in-effect terms snapshot and its unit snapshot. */
    private static ListingRevision needsChanges(int revisionNo) {
        return ListingRevision.load("R" + revisionNo, revisionNo, RevisionStatus.NEEDS_CHANGES, "S1", CREATED, CREATED,
                RevisionFixtures.completeContent(), RevisionFixtures.inEffectTerms(), RevisionFixtures.unitSnapshot(),
                CREATED, "S1");
    }

    private static ListingRevision withStatus(int revisionNo, RevisionStatus status) {
        return RevisionFixtures.withStatus(revisionNo, status);
    }

    private void assertNothingWritten() {
        assertTrue(listings.calls().stream().noneMatch(call -> call.startsWith("update")), listings.calls().toString());
        assertTrue(listings.writtenHistory().isEmpty());
        assertEquals(VERSION, listings.stored(LISTING).orElseThrow().rowVersion());
    }
}
