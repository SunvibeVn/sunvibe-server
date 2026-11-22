package vn.sunvibe.listing.core.domain.model.listing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import vn.sunvibe.listing.core.domain.exception.RevisionStateException;
import vn.sunvibe.listing.core.domain.policy.listing.FreshnessPolicy;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CODE;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CREATED;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.NOW;

/**
 * The put live in the aggregate (listing-publish US-5 AC-1, AC-3, AC-4, BR-6; E06-S6 spec E06S6-D6; PL-1 domain half):
 * an Approved revision goes Live with its time when every display condition holds, the former Live one is Replaced with
 * its time first, an Unpublished listing becomes Published, and the history names the moment; with any reason nothing
 * changes.
 */
class ListingPutLiveTest {

    @Test
    void tryPutLive_unpublishedNoLive_liveAndPublished() {
        Listing listing = forCommand(ListingState.UNPUBLISHED, 2, approved(2));

        List<DisplayReason> reasons = listing.tryPutLive(2, holding(ListingState.UNPUBLISHED), PutLiveMoment.SALE_COMMAND,
                "S9", NOW, "H1", "H2");

        ListingRevision live = listing.revision(2).orElseThrow();
        assertAll(
                () -> assertEquals(List.of(), reasons),
                () -> assertEquals(RevisionStatus.LIVE, live.status()),
                () -> assertEquals(NOW, live.wentLiveAtOrNull()),
                () -> assertNull(live.replacedAtOrNull()),
                () -> assertEquals(ListingState.PUBLISHED, listing.state()),
                () -> assertTrue(listing.stateChanged()),
                () -> assertEquals(NOW, listing.updatedAt()),
                () -> assertEquals(List.of(new ListingHistoryEntry("H2", ListingHistoryAction.REVISION_PUT_LIVE, 2, "S9",
                        NOW)), listing.pendingHistory()),
                () -> assertEquals(List.of(new RevisionChange(live, RevisionChangeKind.UPDATED)),
                        listing.pendingRevisionChanges()));
    }

    @Test
    void tryPutLive_publishedWithLive_formerReplacedFirstThenLive() {
        Listing listing = forCommand(ListingState.PUBLISHED, 3, live(1), approved(3));

        List<DisplayReason> reasons = listing.tryPutLive(3, holding(ListingState.PUBLISHED), PutLiveMoment.SALE_COMMAND,
                "S9", NOW, "H1", "H2");

        ListingRevision replaced = listing.revision(1).orElseThrow();
        ListingRevision live = listing.revision(3).orElseThrow();
        assertAll(
                () -> assertEquals(List.of(), reasons),
                () -> assertEquals(ListingState.PUBLISHED, listing.state()),
                () -> assertFalse(listing.stateChanged()),
                () -> assertEquals(RevisionStatus.REPLACED, replaced.status()),
                () -> assertEquals(NOW, replaced.replacedAtOrNull()),
                () -> assertEquals(CREATED, replaced.wentLiveAtOrNull()),
                () -> assertEquals(RevisionStatus.LIVE, live.status()),
                () -> assertEquals(NOW, live.wentLiveAtOrNull()),
                () -> assertEquals(List.of(
                        new ListingHistoryEntry("H1", ListingHistoryAction.REVISION_REPLACED, 1, "S9", NOW),
                        new ListingHistoryEntry("H2", ListingHistoryAction.REVISION_PUT_LIVE, 3, "S9", NOW)),
                        listing.pendingHistory()),
                () -> assertEquals(List.of(new RevisionChange(replaced, RevisionChangeKind.UPDATED),
                        new RevisionChange(live, RevisionChangeKind.UPDATED)), listing.pendingRevisionChanges()));
    }

    @Test
    void tryPutLive_withReasons_nothingChanged() {
        ListingRevision formerLive = live(1);
        ListingRevision target = approved(3);
        Listing listing = forCommand(ListingState.UNPUBLISHED, 3, formerLive, target);
        DisplayConditions failing = new DisplayConditions(ListingState.UNPUBLISHED, false, false,
                List.of(AgreementReasonGroup.SUSPENDED), RevisionFixtures.inEffectTerms(), UnitAvailability.OCCUPIED,
                false, confirmedToday());

        List<DisplayReason> reasons = listing.tryPutLive(3, failing, PutLiveMoment.SALE_COMMAND, "S9", NOW, "H1",
                "H2");

        assertAll(
                () -> assertEquals(List.of(DisplayReason.ADVERTISING_RIGHT, DisplayReason.UNIT_NOT_AVAILABLE), reasons),
                () -> assertSame(formerLive, listing.revision(1).orElseThrow()),
                () -> assertSame(target, listing.revision(3).orElseThrow()),
                () -> assertEquals(ListingState.UNPUBLISHED, listing.state()),
                () -> assertFalse(listing.stateChanged()),
                () -> assertEquals(CREATED, listing.updatedAt()),
                () -> assertTrue(listing.pendingHistory().isEmpty()),
                () -> assertTrue(listing.pendingRevisionChanges().isEmpty()));
    }

    @Test
    void tryPutLive_targetNotApproved_revisionNotApproved() {
        for (ListingRevision target : List.of(live(2), RevisionFixtures.draft(2), RevisionFixtures.pending(2))) {
            Listing listing = forCommand(ListingState.UNPUBLISHED, 2, target);

            RevisionStateException refused = assertThrows(RevisionStateException.class, () -> listing.tryPutLive(2,
                    holding(ListingState.UNPUBLISHED), PutLiveMoment.SALE_COMMAND, "S9", NOW, "H1", "H2"),
                    target.status().name());

            assertEquals(RevisionStateException.Reason.REVISION_NOT_APPROVED, refused.reason());
            assertTrue(listing.pendingHistory().isEmpty(), target.status().name());
            assertTrue(listing.pendingRevisionChanges().isEmpty(), target.status().name());
            assertFalse(listing.stateChanged(), target.status().name());
        }
    }

    @Test
    void tryPutLive_targetNotLoaded_illegalState() {
        Listing listing = forCommand(ListingState.UNPUBLISHED, 2, approved(2));

        assertThrows(IllegalStateException.class, () -> listing.tryPutLive(1, holding(ListingState.UNPUBLISHED),
                PutLiveMoment.SALE_COMMAND, "S9", NOW, "H1", "H2"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("moments")
    void tryPutLive_moments_historyActionAndActor(PutLiveMoment moment, String actorOrNull,
                                                  ListingHistoryAction action) {
        Listing listing = forCommand(ListingState.PUBLISHED, 3, live(1), approved(3));

        listing.tryPutLive(3, holding(ListingState.PUBLISHED), moment, actorOrNull, NOW, "H1", "H2");

        assertEquals(List.of(
                new ListingHistoryEntry("H1", ListingHistoryAction.REVISION_REPLACED, 1, actorOrNull, NOW),
                new ListingHistoryEntry("H2", action, 3, actorOrNull, NOW)), listing.pendingHistory());
    }

    static Stream<Arguments> moments() {
        return Stream.of(
                Arguments.of(PutLiveMoment.SALE_COMMAND, "S9", ListingHistoryAction.REVISION_PUT_LIVE),
                Arguments.of(PutLiveMoment.APPROVAL, null, ListingHistoryAction.REVISION_PUT_LIVE_ON_APPROVAL),
                Arguments.of(PutLiveMoment.VALID_CONFIRMATION, null,
                        ListingHistoryAction.REVISION_PUT_LIVE_ON_CONFIRMATION));
    }

    @Test
    void tryPutLive_afterPutLive_exactlyOneLive() {
        Listing listing = forCommand(ListingState.PUBLISHED, 3, live(1), approved(3));

        listing.tryPutLive(3, holding(ListingState.PUBLISHED), PutLiveMoment.SALE_COMMAND, "S9", NOW, "H1", "H2");

        assertEquals(List.of(3), listing.revisions().stream()
                .filter(revision -> revision.status() == RevisionStatus.LIVE)
                .map(ListingRevision::revisionNo)
                .toList());
    }

    private static Listing forCommand(ListingState state, int newestRevisionNo, ListingRevision... workingSet) {
        return Listing.loadForCommand("L1", CODE, "U1", TransactionType.LONG_TERM_RENT, "A1", state, "S1", CREATED,
                CREATED, 7L, newestRevisionNo, List.of(workingSet));
    }

    /** An approved revision in its full form, with the in-effect terms snapshot. */
    private static ListingRevision approved(int revisionNo) {
        return ListingRevision.load("R" + revisionNo, revisionNo, RevisionStatus.APPROVED, "S1", CREATED, CREATED,
                RevisionFixtures.completeContent(), RevisionFixtures.inEffectTerms(), RevisionFixtures.unitSnapshot(),
                CREATED, "S1");
    }

    /** A live revision in its full form that went live at {@code CREATED}. */
    private static ListingRevision live(int revisionNo) {
        return ListingRevision.load("R" + revisionNo, revisionNo, RevisionStatus.LIVE, "S1", CREATED, CREATED,
                RevisionFixtures.completeContent(), RevisionFixtures.inEffectTerms(), RevisionFixtures.unitSnapshot(),
                CREATED, "S1", null, CREATED, null);
    }

    private static DisplayConditions holding(ListingState state) {
        return new DisplayConditions(state, false, true, List.of(), RevisionFixtures.inEffectTerms(),
                UnitAvailability.AVAILABLE, false, confirmedToday());
    }

    private static FreshnessSummary confirmedToday() {
        return new FreshnessSummary(FreshnessPolicy.today(NOW), true);
    }
}
