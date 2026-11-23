package vn.sunvibe.listing.core.domain.model.listing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CODE;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CREATED;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.forCommand;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.withStatus;

/**
 * Pin P-1 of listing-publish E06-S6 (spec section 8, S-1): a listing's working set and its full load hold a Live
 * revision beside an Approved one, a Replaced revision beside a Live one, and never two Live revisions (listing
 * section 3.3, listing-publish BR-6). The statuses a put live makes reachable are read as the code reads them today.
 */
class ListingLiveWorkingSetTest {

    @Test
    void loadForCommand_liveAndApproved_loadedBoth() {
        Listing listing = forCommand(2, withStatus(1, RevisionStatus.LIVE), withStatus(2, RevisionStatus.APPROVED));

        assertAll(
                () -> assertEquals(Optional.of(RevisionStatus.LIVE), listing.revision(1).map(ListingRevision::status)),
                () -> assertEquals(Optional.of(RevisionStatus.APPROVED),
                        listing.revision(2).map(ListingRevision::status)));
    }

    @Test
    void loadForCommand_twoLive_illegalState() {
        assertThrows(IllegalStateException.class,
                () -> forCommand(3, withStatus(1, RevisionStatus.LIVE), withStatus(3, RevisionStatus.LIVE)));
    }

    @Test
    void load_liveAndReplaced_loaded() {
        Listing listing = Listing.load("L1", CODE, "U1", TransactionType.LONG_TERM_RENT, "A1", ListingState.PUBLISHED,
                "S1", CREATED, CREATED, 4L, List.of(withStatus(1, RevisionStatus.REPLACED),
                        withStatus(2, RevisionStatus.LIVE), withStatus(3, RevisionStatus.DRAFT)));

        assertEquals(List.of(RevisionStatus.REPLACED, RevisionStatus.LIVE, RevisionStatus.DRAFT),
                listing.revisions().stream().map(ListingRevision::status).toList());
    }
}
