package vn.sunvibe.listing.core.domain.model.listing;

import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import vn.sunvibe.listing.core.domain.exception.RevisionStateException;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.NOW;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.forCommand;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.withStatus;

/**
 * Pin P-1 of listing-publish E06-S4 (spec section 8, U-23): the revision commands of E06-S2 on a decided revision, the
 * statuses a review decision makes reachable. A Needs changes, Rejected or Approved revision is locked as a Pending
 * review one is (listing section 3.3): no save, terms capture or submission, no withdrawal; and it is not open, so a new
 * Draft may start beside it (listing-publish BR-6).
 */
class ListingDecidedRevisionTest {

    @ParameterizedTest
    @EnumSource(value = RevisionStatus.class, names = {"NEEDS_CHANGES", "REJECTED", "APPROVED"})
    void commands_decidedTarget_refusedNothingPending(RevisionStatus decided) {
        Map<String, Executable> commands = new LinkedHashMap<>();
        Map<String, RevisionStateException.Reason> expected = new LinkedHashMap<>();
        Map<String, Listing> listings = new LinkedHashMap<>();
        for (String name : List.of("save", "terms", "submit", "withdraw")) {
            listings.put(name, forCommand(2, withStatus(2, decided)));
        }
        commands.put("save", () -> listings.get("save").saveContent(2, RevisionFixtures.completeContent(), "H1", "S1",
                NOW));
        expected.put("save", RevisionStateException.Reason.REVISION_NOT_DRAFT);
        commands.put("terms", () -> listings.get("terms").captureTerms(2, RevisionFixtures.inEffectTerms(), "H1", "S1",
                NOW));
        expected.put("terms", RevisionStateException.Reason.REVISION_NOT_DRAFT);
        commands.put("submit", () -> listings.get("submit").submit(2, RevisionFixtures.unitSnapshot(), "H1", "S1", NOW));
        expected.put("submit", RevisionStateException.Reason.REVISION_NOT_DRAFT);
        commands.put("withdraw", () -> listings.get("withdraw").withdraw(2, "H1", "S1", NOW));
        expected.put("withdraw", RevisionStateException.Reason.REVISION_NOT_PENDING);

        for (Map.Entry<String, Executable> command : commands.entrySet()) {
            Listing listing = listings.get(command.getKey());
            ListingRevision loaded = listing.revision(2).orElseThrow();

            RevisionStateException refused = assertThrows(RevisionStateException.class, command.getValue(),
                    command.getKey());

            assertAll(command.getKey(),
                    () -> assertEquals(expected.get(command.getKey()), refused.reason()),
                    () -> assertTrue(listing.pendingHistory().isEmpty()),
                    () -> assertTrue(listing.pendingRevisionChanges().isEmpty()),
                    () -> assertSame(loaded, listing.revision(2).orElseThrow()),
                    () -> assertEquals(decided, listing.revision(2).orElseThrow().status()));
        }
    }

    @ParameterizedTest
    @EnumSource(value = RevisionStatus.class, names = {"APPROVED", "NEEDS_CHANGES"})
    void startRevision_besideApprovedOrNeedsChanges_nextDraftByActor(RevisionStatus decided) {
        Listing listing = forCommand(3, withStatus(3, decided));

        ListingRevision started = listing.startRevision("R4", "H1", "S2", NOW);

        assertAll(
                () -> assertEquals(4, started.revisionNo()),
                () -> assertEquals(RevisionStatus.DRAFT, started.status()),
                () -> assertEquals("S2", started.createdBy()),
                () -> assertEquals(4, listing.newestRevisionNo()),
                () -> assertEquals(decided, listing.revision(3).orElseThrow().status()),
                () -> assertEquals(List.of(new ListingHistoryEntry("H1", ListingHistoryAction.REVISION_STARTED, 4, "S2",
                        NOW)), listing.pendingHistory()),
                () -> assertEquals(List.of(new RevisionChange(started, RevisionChangeKind.ADDED)),
                        listing.pendingRevisionChanges()));
    }
}
