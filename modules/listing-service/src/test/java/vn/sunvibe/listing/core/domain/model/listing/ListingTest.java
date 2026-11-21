package vn.sunvibe.listing.core.domain.model.listing;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The listing aggregate (listing BR-1, BR-2, BR-3, sections 3.2, 3.3; listing-publish E06-S1 spec E06S1-D16): a stable
 * shell with its revisions and the history of its first command.
 */
class ListingTest {

    private static final Instant NOW = Instant.parse("2026-11-20T03:15:00.123456Z");
    private static final ListingCode CODE = ListingCode.of("TD-7K3M-Q9TX");

    @Test
    void create_newListing_unpublishedWithDraftRevisionOneAndCreatedEntry() {
        Listing listing = Listing.create("L1", CODE, "U1", "A1", "S1", NOW, "R1", "H1");

        assertEquals("L1", listing.id());
        assertEquals(CODE, listing.code());
        assertEquals("U1", listing.unitId());
        assertEquals(TransactionType.LONG_TERM_RENT, listing.transactionType());
        assertEquals("A1", listing.agreementId());
        assertEquals(ListingState.UNPUBLISHED, listing.state());
        assertEquals("S1", listing.createdBy());
        assertEquals(NOW, listing.createdAt());
        assertEquals(NOW, listing.updatedAt());
        assertEquals(0L, listing.rowVersion());
        assertEquals(1, listing.revisions().size());
        ListingRevision revision = listing.revisions().getFirst();
        assertEquals("R1", revision.id());
        assertEquals(1, revision.revisionNo());
        assertEquals(RevisionStatus.DRAFT, revision.status());
        assertEquals("S1", revision.createdBy());
        assertEquals(NOW, revision.createdAt());
        assertEquals(NOW, revision.updatedAt());
        assertEquals(List.of(new ListingHistoryEntry("H1", ListingHistoryAction.CREATED, 1, "S1", NOW)),
                listing.pendingHistory());
    }

    @Test
    void create_nullField_illegalState() {
        assertAll(
                () -> assertThrows(IllegalStateException.class,
                        () -> Listing.create(null, CODE, "U1", "A1", "S1", NOW, "R1", "H1")),
                () -> assertThrows(IllegalStateException.class,
                        () -> Listing.create("L1", null, "U1", "A1", "S1", NOW, "R1", "H1")),
                () -> assertThrows(IllegalStateException.class,
                        () -> Listing.create("L1", CODE, null, "A1", "S1", NOW, "R1", "H1")),
                () -> assertThrows(IllegalStateException.class,
                        () -> Listing.create("L1", CODE, "U1", null, "S1", NOW, "R1", "H1")),
                () -> assertThrows(IllegalStateException.class,
                        () -> Listing.create("L1", CODE, "U1", "A1", null, NOW, "R1", "H1")),
                () -> assertThrows(IllegalStateException.class,
                        () -> Listing.create("L1", CODE, "U1", "A1", "S1", null, "R1", "H1")));
    }

    @Test
    void load_storedListing_keepsItsFields() {
        Instant later = NOW.plusSeconds(60);
        List<ListingRevision> revisions = List.of(
                ListingRevision.load("R2", 2, RevisionStatus.DRAFT, "S2", later, later),
                ListingRevision.load("R1", 1, RevisionStatus.REPLACED, "S1", NOW, later));

        Listing listing = Listing.load("L1", CODE, "U1", TransactionType.LONG_TERM_RENT, "A1", ListingState.PAUSED,
                "S1", NOW, later, 3L, revisions);

        assertEquals("L1", listing.id());
        assertEquals(CODE, listing.code());
        assertEquals("U1", listing.unitId());
        assertEquals(TransactionType.LONG_TERM_RENT, listing.transactionType());
        assertEquals("A1", listing.agreementId());
        assertEquals(ListingState.PAUSED, listing.state());
        assertEquals("S1", listing.createdBy());
        assertEquals(NOW, listing.createdAt());
        assertEquals(later, listing.updatedAt());
        assertEquals(3L, listing.rowVersion());
        assertEquals(List.of(1, 2), listing.revisions().stream().map(ListingRevision::revisionNo).toList());
        assertEquals(RevisionStatus.REPLACED, listing.revisions().getFirst().status());
        assertTrue(listing.pendingHistory().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> listing.revisions().clear());
    }

    @Test
    void load_noRevision_illegalState() {
        assertThrows(IllegalStateException.class, () -> load(List.of()));
    }

    @Test
    void load_revisionNumbersWithAGap_illegalState() {
        assertThrows(IllegalStateException.class, () -> load(List.of(
                ListingRevision.load("R1", 1, RevisionStatus.REPLACED, "S1", NOW, NOW),
                ListingRevision.load("R3", 3, RevisionStatus.DRAFT, "S1", NOW, NOW))));
    }

    @Test
    void load_twoOpenRevisions_illegalState() {
        assertThrows(IllegalStateException.class, () -> load(List.of(
                ListingRevision.load("R1", 1, RevisionStatus.DRAFT, "S1", NOW, NOW),
                ListingRevision.load("R2", 2, RevisionStatus.PENDING_REVIEW, "S1", NOW, NOW))));
    }

    @Test
    void load_twoApprovedOrTwoLive_illegalState() {
        assertAll(
                () -> assertThrows(IllegalStateException.class, () -> load(List.of(
                        ListingRevision.load("R1", 1, RevisionStatus.APPROVED, "S1", NOW, NOW),
                        ListingRevision.load("R2", 2, RevisionStatus.APPROVED, "S1", NOW, NOW)))),
                () -> assertThrows(IllegalStateException.class, () -> load(List.of(
                        ListingRevision.load("R1", 1, RevisionStatus.LIVE, "S1", NOW, NOW),
                        ListingRevision.load("R2", 2, RevisionStatus.LIVE, "S1", NOW, NOW)))));
    }

    private static Listing load(List<ListingRevision> revisions) {
        return Listing.load("L1", CODE, "U1", TransactionType.LONG_TERM_RENT, "A1", ListingState.UNPUBLISHED, "S1",
                NOW, NOW, 0L, revisions);
    }
}
