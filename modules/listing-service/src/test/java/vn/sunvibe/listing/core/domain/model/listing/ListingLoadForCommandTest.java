package vn.sunvibe.listing.core.domain.model.listing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CREATED;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.draft;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.forCommand;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.pending;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.withStatus;

/**
 * A listing loaded for a command (listing-publish E06-S2 spec E06S2-D19): a bounded working set, the open revision and
 * the target, checked by the aggregate on what it can see; the unique indexes guard the rest of the table.
 */
class ListingLoadForCommandTest {

    @Test
    void loadForCommand_workingSet_revisionsAreTheSet() {
        Listing listing = forCommand(21, draft(21), withStatus(5, RevisionStatus.CANCELLED));

        assertEquals(List.of(5, 21), listing.revisions().stream().map(ListingRevision::revisionNo).toList());
        assertEquals(21, listing.newestRevisionNo());
        assertEquals(7L, listing.rowVersion());
        assertEquals(RevisionStatus.DRAFT, listing.revision(21).orElseThrow().status());
        assertTrue(listing.revision(4).isEmpty());
        assertTrue(listing.pendingHistory().isEmpty());
        assertTrue(listing.pendingRevisionChanges().isEmpty());
    }

    @Test
    void loadForCommand_emptySet_allowedWithNewestNumber() {
        Listing listing = forCommand(3);

        assertTrue(listing.revisions().isEmpty());
        assertEquals(3, listing.newestRevisionNo());
    }

    @Test
    void loadForCommand_twoOpenRevisions_illegalState() {
        assertThrows(IllegalStateException.class, () -> forCommand(3, draft(3), pending(2)));
        assertThrows(IllegalStateException.class, () -> forCommand(3, draft(3), draft(2)));
        assertThrows(IllegalStateException.class,
                () -> forCommand(3, withStatus(3, RevisionStatus.APPROVED), withStatus(2, RevisionStatus.APPROVED)));
        assertThrows(IllegalStateException.class,
                () -> forCommand(3, withStatus(3, RevisionStatus.LIVE), withStatus(2, RevisionStatus.LIVE)));
    }

    @Test
    void loadForCommand_numberAboveNewest_illegalState() {
        assertThrows(IllegalStateException.class, () -> forCommand(3, draft(4)));
    }

    @Test
    void loadForCommand_duplicateNumbers_illegalState() {
        assertThrows(IllegalStateException.class,
                () -> forCommand(3, draft(3), withStatus(3, RevisionStatus.CANCELLED)));
    }

    @Test
    void loadForCommand_headerFormRevision_illegalState() {
        ListingRevision header = ListingRevision.load("R2", 2, RevisionStatus.DRAFT, "S1", CREATED, CREATED);

        assertThrows(IllegalStateException.class, () -> forCommand(2, header));
    }

    @Test
    void loadForCommand_newestBelowOne_illegalState() {
        assertThrows(IllegalStateException.class, () -> forCommand(0));
    }
}
