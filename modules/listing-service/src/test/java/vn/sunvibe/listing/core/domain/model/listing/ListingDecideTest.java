package vn.sunvibe.listing.core.domain.model.listing;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.domain.exception.RevisionStateException;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.NOW;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.forCommand;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.pending;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.withStatus;

/**
 * A review decision in the listing aggregate (listing-publish US-4 AC-3, BR-6, BR-7; E06-S4 spec E06S4-D8, D9; RV-8 domain
 * half): only a Pending review revision is decided; an approval first cancels the approved revision still waiting; the
 * listing's state never changes; each step one history entry by the decider and one revision change, in order.
 */
class ListingDecideTest {

    private static final ReviewNote NOTE = ReviewNote.ofOrNull("Thiếu diện tích");

    @Test
    void decide_approvalWithWaitingApproval_cancelsFirstThenApproves() {
        Listing listing = forCommand(3, approved(2), pending(3));
        RevisionDecision decision = RevisionDecision.approved("R3", 3, ReviewCheck.VERSION_1, "V1", NOW);

        ListingRevision decided = listing.decide(3, decision, "H1", "H0", NOW);

        ListingRevision cancelled = listing.revision(2).orElseThrow();
        assertAll(
                () -> assertEquals(RevisionStatus.APPROVED, decided.status()),
                () -> assertSame(decided, listing.revision(3).orElseThrow()),
                () -> assertEquals(RevisionStatus.CANCELLED, cancelled.status()),
                () -> assertEquals(NOW, cancelled.updatedAt()),
                () -> assertEquals(NOW, decided.updatedAt()),
                () -> assertEquals(List.of(
                        new ListingHistoryEntry("H0", ListingHistoryAction.APPROVAL_CANCELLED, 2, "V1", NOW),
                        new ListingHistoryEntry("H1", ListingHistoryAction.REVISION_APPROVED, 3, "V1", NOW)),
                        listing.pendingHistory()),
                () -> assertEquals(List.of(new RevisionChange(cancelled, RevisionChangeKind.UPDATED),
                        new RevisionChange(decided, RevisionChangeKind.UPDATED)), listing.pendingRevisionChanges()),
                () -> assertEquals(List.of(decision), listing.pendingDecisions()),
                () -> assertEquals(NOW, listing.updatedAt()));
    }

    @Test
    void decide_approvalAlone_oneEntryOneChange() {
        Listing listing = forCommand(1, pending(1));
        RevisionDecision decision = RevisionDecision.approved("R1", 1, ReviewCheck.VERSION_1, "V1", NOW);

        ListingRevision decided = listing.decide(1, decision, "H1", "H0", NOW);

        assertEquals(RevisionStatus.APPROVED, decided.status());
        assertEquals(List.of(new ListingHistoryEntry("H1", ListingHistoryAction.REVISION_APPROVED, 1, "V1", NOW)),
                listing.pendingHistory());
        assertEquals(List.of(new RevisionChange(decided, RevisionChangeKind.UPDATED)), listing.pendingRevisionChanges());
        assertEquals(List.of(decision), listing.pendingDecisions());
    }

    @Test
    void decide_needsChangesOrRejected_onlyTargetChanged() {
        RevisionDecision returned = RevisionDecision.needsChanges("R3", 3, List.of(ChangeReason.MISSING_INFORMATION),
                NOTE, "V1", NOW);
        RevisionDecision rejected = RevisionDecision.rejected("R3", 3, RejectionGroup.WRONG_UNIT, NOTE, "V1", NOW);
        for (RevisionDecision decision : List.of(returned, rejected)) {
            Listing listing = forCommand(3, approved(2), pending(3));

            ListingRevision decided = listing.decide(3, decision, "H1", "H0", NOW);

            ListingHistoryAction action = decision.outcome() == ReviewOutcome.NEEDS_CHANGES
                    ? ListingHistoryAction.REVISION_RETURNED : ListingHistoryAction.REVISION_REJECTED;
            assertAll(decision.outcome().name(),
                    () -> assertEquals(decision.outcome().status(), decided.status()),
                    () -> assertEquals(RevisionStatus.APPROVED, listing.revision(2).orElseThrow().status()),
                    () -> assertEquals(List.of(new ListingHistoryEntry("H1", action, 3, "V1", NOW)),
                            listing.pendingHistory()),
                    () -> assertEquals(List.of(new RevisionChange(decided, RevisionChangeKind.UPDATED)),
                            listing.pendingRevisionChanges()),
                    () -> assertEquals(List.of(decision), listing.pendingDecisions()));
        }
    }

    @Test
    void decide_notPending_revisionNotPendingNothingPending() {
        for (RevisionStatus status : List.of(RevisionStatus.DRAFT, RevisionStatus.CANCELLED, RevisionStatus.APPROVED)) {
            Listing listing = forCommand(2, withStatus(2, status));
            RevisionDecision decision = RevisionDecision.approved("R2", 2, ReviewCheck.VERSION_1, "V1", NOW);

            RevisionStateException refused = assertThrows(RevisionStateException.class,
                    () -> listing.decide(2, decision, "H1", "H0", NOW), status.name());

            assertEquals(RevisionStateException.Reason.REVISION_NOT_PENDING, refused.reason());
            assertTrue(listing.pendingHistory().isEmpty(), status.name());
            assertTrue(listing.pendingRevisionChanges().isEmpty(), status.name());
            assertTrue(listing.pendingDecisions().isEmpty(), status.name());
            assertEquals(status, listing.revision(2).orElseThrow().status());
        }
    }

    @Test
    void decide_listingStateAndOtherRevisionsUntouched() {
        Listing listing = forCommand(3, approved(2), pending(3));
        ListingState state = listing.state();
        ListingRevision other = listing.revision(2).orElseThrow();

        listing.decide(3, RevisionDecision.rejected("R3", 3, RejectionGroup.OTHER, NOTE, "V1", NOW), "H1", "H0", NOW);

        assertEquals(ListingState.UNPUBLISHED, state);
        assertEquals(state, listing.state());
        assertSame(other, listing.revision(2).orElseThrow());
        assertEquals(7L, listing.rowVersion());
        assertEquals(3, listing.newestRevisionNo());
        assertEquals(2, listing.revisions().size());
    }

    /** An approved revision waiting to go live, in its full form. */
    private static ListingRevision approved(int revisionNo) {
        return ListingRevision.load("R" + revisionNo, revisionNo, RevisionStatus.APPROVED, "S1",
                RevisionFixtures.CREATED, RevisionFixtures.CREATED, RevisionFixtures.completeContent(),
                RevisionFixtures.inEffectTerms(), RevisionFixtures.unitSnapshot(), RevisionFixtures.CREATED, "S1");
    }
}
