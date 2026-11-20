package vn.sunvibe.listing.core.domain.model.listing;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.domain.exception.RevisionNotSubmittableException;
import vn.sunvibe.listing.core.domain.exception.RevisionStateException;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.NOW;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.completeDraft;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.draft;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.forCommand;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.pending;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.withStatus;

/**
 * The revision commands of the listing aggregate (listing-publish US-2 AC-1 to AC-6, BR-6; listing section 3.3, BR-3;
 * E06-S2 spec E06S2-D7, E06S2-D10, E06S2-D11): each guarded by status, each recording one history entry with
 * identifiers only and one revision change.
 */
class ListingRevisionCommandsTest {

    @Test
    void startRevision_noOpenRevision_nextEmptyDraftByActor() {
        Listing listing = forCommand(3, withStatus(3, RevisionStatus.CANCELLED));

        ListingRevision started = listing.startRevision("R4", "H1", "S2", NOW);

        assertAll(
                () -> assertEquals(4, started.revisionNo()),
                () -> assertEquals("R4", started.id()),
                () -> assertEquals(RevisionStatus.DRAFT, started.status()),
                () -> assertSame(RevisionContent.EMPTY, started.content()),
                () -> assertNull(started.termsOrNull()),
                () -> assertEquals("S2", started.createdBy()),
                () -> assertEquals(NOW, started.createdAt()),
                () -> assertEquals(4, listing.newestRevisionNo()),
                () -> assertEquals(NOW, listing.updatedAt()),
                () -> assertEquals(List.of(new ListingHistoryEntry("H1", ListingHistoryAction.REVISION_STARTED, 4, "S2",
                        NOW)), listing.pendingHistory()),
                () -> assertEquals(List.of(new RevisionChange(started, RevisionChangeKind.ADDED)),
                        listing.pendingRevisionChanges()),
                () -> assertSame(started, listing.revision(4).orElseThrow()));
    }

    @Test
    void startRevision_draftOrPendingOpen_openRevisionExists() {
        for (ListingRevision open : List.of(draft(3), pending(3))) {
            Listing listing = forCommand(3, open);
            RevisionStateException refused = assertThrows(RevisionStateException.class,
                    () -> listing.startRevision("R4", "H1", "S1", NOW));
            assertEquals(RevisionStateException.Reason.OPEN_REVISION_EXISTS, refused.reason());
            assertTrue(listing.pendingHistory().isEmpty());
            assertTrue(listing.pendingRevisionChanges().isEmpty());
        }
    }

    @Test
    void saveContent_draft_contentReplacedWithHistory() {
        Listing listing = forCommand(2, completeDraft(2));
        RevisionContent content = new RevisionContent("Tiêu đề mới", null,
                RevisionFeatures.NONE_CHOSEN);

        ListingRevision saved = listing.saveContent(2, content, "H1", "S1", NOW);

        assertSame(content, saved.content());
        assertEquals(RevisionFixtures.inEffectTerms(), saved.termsOrNull());
        assertEquals(RevisionStatus.DRAFT, saved.status());
        assertEquals(NOW, saved.updatedAt());
        assertEquals(RevisionFixtures.CREATED, saved.createdAt());
        assertSame(saved, listing.revision(2).orElseThrow());
        assertEquals(List.of(new ListingHistoryEntry("H1", ListingHistoryAction.REVISION_SAVED, 2, "S1", NOW)),
                listing.pendingHistory());
        assertEquals(List.of(new RevisionChange(saved, RevisionChangeKind.UPDATED)), listing.pendingRevisionChanges());
    }

    @Test
    void saveContent_pendingOrCancelled_revisionNotDraft() {
        for (ListingRevision locked : List.of(pending(2), withStatus(2, RevisionStatus.CANCELLED))) {
            Listing listing = forCommand(2, locked);
            RevisionStateException refused = assertThrows(RevisionStateException.class,
                    () -> listing.saveContent(2, RevisionContent.EMPTY, "H1", "S1", NOW));
            assertEquals(RevisionStateException.Reason.REVISION_NOT_DRAFT, refused.reason());
            assertTrue(listing.pendingHistory().isEmpty());
        }
    }

    @Test
    void captureTerms_draft_termsReplacedKind() {
        Listing listing = forCommand(1, completeDraft(1));
        RevisionTerms terms = new RevisionTerms(TermsSource.PENDING_START, java.time.LocalDate.parse("2026-11-30"), NOW,
                9_000_000L, new RevisionTerms.Deposit(DepositBasis.AMOUNT, null, 0L), 12, 3, List.of());

        ListingRevision captured = listing.captureTerms(1, terms, "H1", "S1", NOW);

        assertSame(terms, captured.termsOrNull());
        assertEquals(RevisionFixtures.completeContent(), captured.content());
        assertEquals(List.of(new ListingHistoryEntry("H1", ListingHistoryAction.TERMS_CAPTURED, 1, "S1", NOW)),
                listing.pendingHistory());
        assertEquals(List.of(new RevisionChange(captured, RevisionChangeKind.TERMS_REPLACED)),
                listing.pendingRevisionChanges());
        RevisionStateException refused = assertThrows(RevisionStateException.class,
                () -> forCommand(1, pending(1)).captureTerms(1, terms, "H2", "S1", NOW));
        assertEquals(RevisionStateException.Reason.REVISION_NOT_DRAFT, refused.reason());
    }

    @Test
    void requireDraft_pending_revisionNotDraft() {
        Listing listing = forCommand(1, pending(1));

        RevisionStateException refused = assertThrows(RevisionStateException.class, () -> listing.requireDraft(1));
        assertEquals(RevisionStateException.Reason.REVISION_NOT_DRAFT, refused.reason());
        forCommand(1, draft(1)).requireDraft(1);
    }

    @Test
    void requireSubmittable_violations_allOfThemInOrder() {
        Listing listing = forCommand(1, draft(1));

        RevisionNotSubmittableException refused = assertThrows(RevisionNotSubmittableException.class,
                () -> listing.requireSubmittable(1));
        assertEquals(List.of(new RevisionViolation(RevisionPart.TITLE, RevisionRule.REQUIRED),
                new RevisionViolation(RevisionPart.DESCRIPTION, RevisionRule.REQUIRED),
                new RevisionViolation(RevisionPart.TERMS, RevisionRule.REQUIRED)), refused.violations());
        RevisionStateException notDraft = assertThrows(RevisionStateException.class,
                () -> forCommand(1, pending(1)).requireSubmittable(1));
        assertEquals(RevisionStateException.Reason.REVISION_NOT_DRAFT, notDraft.reason());
        forCommand(1, completeDraft(1)).requireSubmittable(1);
    }

    @Test
    void submit_complete_pendingWithSnapshotAndSubmitter() {
        Listing listing = forCommand(1, completeDraft(1));
        UnitSnapshot unit = RevisionFixtures.unitSnapshot();

        ListingRevision submitted = listing.submit(1, unit, "H1", "S2", NOW);

        assertAll(
                () -> assertEquals(RevisionStatus.PENDING_REVIEW, submitted.status()),
                () -> assertSame(unit, submitted.unitSnapshotOrNull()),
                () -> assertEquals(NOW, submitted.submittedAtOrNull()),
                () -> assertEquals("S2", submitted.submittedByOrNull()),
                () -> assertEquals("S1", submitted.createdBy()),
                () -> assertEquals(RevisionFixtures.completeContent(), submitted.content()),
                () -> assertEquals(ListingState.UNPUBLISHED, listing.state()),
                () -> assertEquals(List.of(new ListingHistoryEntry("H1", ListingHistoryAction.REVISION_SUBMITTED, 1,
                        "S2", NOW)), listing.pendingHistory()),
                () -> assertEquals(List.of(new RevisionChange(submitted, RevisionChangeKind.UPDATED)),
                        listing.pendingRevisionChanges()));
        assertThrows(RevisionNotSubmittableException.class,
                () -> forCommand(1, draft(1)).submit(1, unit, "H1", "S1", NOW));
    }

    @Test
    void submit_pending_revisionNotDraft() {
        Listing listing = forCommand(1, pending(1));

        RevisionStateException refused = assertThrows(RevisionStateException.class,
                () -> listing.submit(1, RevisionFixtures.unitSnapshot(), "H1", "S1", NOW));
        assertEquals(RevisionStateException.Reason.REVISION_NOT_DRAFT, refused.reason());
    }

    @Test
    void withdraw_pending_cancelledContentKept() {
        Listing listing = forCommand(1, pending(1));

        ListingRevision withdrawn = listing.withdraw(1, "H1", "S1", NOW);

        assertEquals(RevisionStatus.CANCELLED, withdrawn.status());
        assertEquals(RevisionFixtures.completeContent(), withdrawn.content());
        assertEquals(RevisionFixtures.inEffectTerms(), withdrawn.termsOrNull());
        assertEquals(RevisionFixtures.unitSnapshot(), withdrawn.unitSnapshotOrNull());
        assertEquals(RevisionFixtures.CREATED, withdrawn.submittedAtOrNull());
        assertEquals(NOW, withdrawn.updatedAt());
        assertEquals(List.of(new ListingHistoryEntry("H1", ListingHistoryAction.REVISION_WITHDRAWN, 1, "S1", NOW)),
                listing.pendingHistory());
        assertEquals(List.of(new RevisionChange(withdrawn, RevisionChangeKind.UPDATED)), listing.pendingRevisionChanges());
    }

    @Test
    void withdraw_draft_revisionNotPending() {
        for (ListingRevision notPending : List.of(draft(1), withStatus(1, RevisionStatus.CANCELLED))) {
            RevisionStateException refused = assertThrows(RevisionStateException.class,
                    () -> forCommand(1, notPending).withdraw(1, "H1", "S1", NOW));
            assertEquals(RevisionStateException.Reason.REVISION_NOT_PENDING, refused.reason());
        }
    }

    @Test
    void commands_revisionNotInSet_illegalState() {
        Listing listing = forCommand(3, draft(3));

        assertAll(
                () -> assertThrows(IllegalStateException.class,
                        () -> listing.saveContent(2, RevisionContent.EMPTY, "H1", "S1", NOW)),
                () -> assertThrows(IllegalStateException.class,
                        () -> listing.captureTerms(2, RevisionFixtures.inEffectTerms(), "H1", "S1", NOW)),
                () -> assertThrows(IllegalStateException.class,
                        () -> listing.submit(2, RevisionFixtures.unitSnapshot(), "H1", "S1", NOW)),
                () -> assertThrows(IllegalStateException.class, () -> listing.withdraw(2, "H1", "S1", NOW)),
                () -> assertThrows(IllegalStateException.class, () -> listing.requireDraft(2)),
                () -> assertThrows(IllegalStateException.class, () -> listing.requireSubmittable(2)));
        assertTrue(listing.pendingHistory().isEmpty());
    }

    @Test
    void commands_lists_unmodifiable() {
        Listing listing = forCommand(1, pending(1));
        listing.withdraw(1, "H1", "S1", NOW);
        listing.startRevision("R2", "H2", "S1", NOW);

        assertEquals(2, listing.revisions().size());
        assertEquals(2, listing.pendingHistory().size());
        assertEquals(2, listing.pendingRevisionChanges().size());
        assertAll(
                () -> assertThrows(UnsupportedOperationException.class, () -> listing.revisions().clear()),
                () -> assertThrows(UnsupportedOperationException.class, () -> listing.pendingHistory().clear()),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> listing.pendingRevisionChanges().clear()));
    }

    @Test
    void commands_historyEntry_identifiersOnly() {
        Listing listing = forCommand(1, completeDraft(1));
        listing.saveContent(1, RevisionFixtures.completeContent(), "H1", "S1", NOW);
        listing.captureTerms(1, RevisionFixtures.inEffectTerms(), "H2", "S1", NOW);
        listing.submit(1, RevisionFixtures.unitSnapshot(), "H3", "S1", NOW);
        listing.withdraw(1, "H4", "S1", NOW);
        listing.startRevision("R2", "H5", "S1", NOW);

        assertEquals(List.of(
                new ListingHistoryEntry("H1", ListingHistoryAction.REVISION_SAVED, 1, "S1", NOW),
                new ListingHistoryEntry("H2", ListingHistoryAction.TERMS_CAPTURED, 1, "S1", NOW),
                new ListingHistoryEntry("H3", ListingHistoryAction.REVISION_SUBMITTED, 1, "S1", NOW),
                new ListingHistoryEntry("H4", ListingHistoryAction.REVISION_WITHDRAWN, 1, "S1", NOW),
                new ListingHistoryEntry("H5", ListingHistoryAction.REVISION_STARTED, 2, "S1", NOW)),
                listing.pendingHistory());
        // a history entry is an action, a number, an actor and a time: its record has no other component
        assertEquals(5, ListingHistoryEntry.class.getRecordComponents().length);
    }
}
