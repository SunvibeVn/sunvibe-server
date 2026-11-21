package vn.sunvibe.listing.core.domain.model.listing;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.domain.exception.RevisionStateException;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CREATED;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.NOW;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.forCommand;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.pending;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.withStatus;

/**
 * The copy of a returned revision into a new Draft (listing-publish US-2 AC-6, BR-6, BR-7; E06-S4 spec E06S4-D14; RV-10
 * domain half): only a Needs changes revision, only while no revision is open; the copy keeps the content and the terms
 * snapshot with its fees, names its source, and holds no unit snapshot or submission.
 */
class ListingRevisionCopyTest {

    @Test
    void copyRevision_needsChanges_nextDraftWithContentTermsAndSource() {
        ListingRevision source = returned(2);
        Listing listing = forCommand(3, source);

        ListingRevision copy = listing.copyRevision(2, "R4", "H1", "S2", NOW);

        assertAll(
                () -> assertEquals("R4", copy.id()),
                () -> assertEquals(4, copy.revisionNo()),
                () -> assertEquals(RevisionStatus.DRAFT, copy.status()),
                () -> assertEquals("S2", copy.createdBy()),
                () -> assertEquals(NOW, copy.createdAt()),
                () -> assertEquals(NOW, copy.updatedAt()),
                () -> assertEquals(source.content(), copy.content()),
                () -> assertEquals(source.termsOrNull(), copy.termsOrNull()),
                () -> assertEquals(3, copy.termsOrNull().fees().size()),
                () -> assertEquals(2, copy.copiedFromRevisionNoOrNull()),
                () -> assertEquals(4, listing.newestRevisionNo()),
                () -> assertSame(copy, listing.revision(4).orElseThrow()),
                () -> assertSame(source, listing.revision(2).orElseThrow()),
                () -> assertEquals(List.of(new ListingHistoryEntry("H1", ListingHistoryAction.REVISION_COPIED, 4, "S2",
                        NOW)), listing.pendingHistory()),
                () -> assertEquals(List.of(new RevisionChange(copy, RevisionChangeKind.COPIED)),
                        listing.pendingRevisionChanges()),
                () -> assertTrue(listing.pendingDecisions().isEmpty()));
    }

    @Test
    void copyRevision_rejectedCancelledOrOther_notCopyable() {
        for (RevisionStatus status : EnumSet.complementOf(EnumSet.of(RevisionStatus.NEEDS_CHANGES))) {
            Listing listing = forCommand(2, status == RevisionStatus.PENDING_REVIEW ? pending(2)
                    : withStatus(2, status));

            RevisionStateException refused = assertThrows(RevisionStateException.class,
                    () -> listing.copyRevision(2, "R3", "H1", "S1", NOW), status.name());

            assertEquals(RevisionStateException.Reason.REVISION_NOT_COPYABLE, refused.reason(), status.name());
            assertTrue(listing.pendingHistory().isEmpty(), status.name());
            assertTrue(listing.pendingRevisionChanges().isEmpty(), status.name());
            assertEquals(2, listing.newestRevisionNo());
        }
        assertEquals(7, EnumSet.complementOf(EnumSet.of(RevisionStatus.NEEDS_CHANGES)).size());
    }

    @Test
    void copyRevision_openRevision_openRevisionExists() {
        for (ListingRevision open : List.of(RevisionFixtures.draft(3), pending(3))) {
            Listing listing = forCommand(3, returned(2), open);

            RevisionStateException refused = assertThrows(RevisionStateException.class,
                    () -> listing.copyRevision(2, "R4", "H1", "S1", NOW));

            assertEquals(RevisionStateException.Reason.OPEN_REVISION_EXISTS, refused.reason());
            assertTrue(listing.pendingHistory().isEmpty());
            assertTrue(listing.pendingRevisionChanges().isEmpty());
        }
    }

    @Test
    void copyOf_noUnitSnapshotSubmissionOrDecision() {
        ListingRevision copy = ListingRevision.copyOf("R4", 4, returned(2), "S2", NOW);

        assertAll(
                () -> assertEquals(RevisionStatus.DRAFT, copy.status()),
                () -> assertNull(copy.unitSnapshotOrNull()),
                () -> assertNull(copy.submittedAtOrNull()),
                () -> assertNull(copy.submittedByOrNull()),
                () -> assertEquals(2, copy.copiedFromRevisionNoOrNull()),
                () -> assertEquals(RevisionFixtures.inEffectTerms(), copy.termsOrNull()));
    }

    @Test
    void load_copySourceNotBelowNumber_refused() {
        Instant at = CREATED;
        assertAll(
                () -> assertThrows(IllegalStateException.class, () -> ListingRevision.load("R3", 3, RevisionStatus.DRAFT,
                        "S1", at, at, RevisionFixtures.completeContent(), null, null, null, null, 3)),
                () -> assertThrows(IllegalStateException.class, () -> ListingRevision.load("R3", 3, RevisionStatus.DRAFT,
                        "S1", at, at, RevisionFixtures.completeContent(), null, null, null, null, 4)),
                () -> assertThrows(IllegalStateException.class, () -> ListingRevision.load("R3", 3, RevisionStatus.DRAFT,
                        "S1", at, at, RevisionFixtures.completeContent(), null, null, null, null, 0)));
        assertEquals(2, ListingRevision.load("R3", 3, RevisionStatus.DRAFT, "S1", at, at,
                RevisionFixtures.completeContent(), null, null, null, null, 2).copiedFromRevisionNoOrNull());
        assertNull(RevisionFixtures.completeDraft(3).copiedFromRevisionNoOrNull());
    }

    @Test
    void withContent_copiedDraft_keepsCopySource() {
        Listing listing = forCommand(3, returned(2));
        listing.copyRevision(2, "R4", "H1", "S1", NOW);

        ListingRevision saved = listing.saveContent(4, new RevisionContent("Tiêu đề khác", null,
                RevisionFeatures.NONE_CHOSEN), "H2", "S1", NOW);
        ListingRevision captured = listing.captureTerms(4, RevisionFixtures.inEffectTerms(), "H3", "S1", NOW);

        assertEquals(2, saved.copiedFromRevisionNoOrNull());
        assertEquals(2, captured.copiedFromRevisionNoOrNull());
        ListingRevision submitted = forCommand(4, ListingRevision.load("R4", 4, RevisionStatus.DRAFT, "S1", CREATED,
                CREATED, RevisionFixtures.completeContent(), RevisionFixtures.inEffectTerms(), null, null, null, 2))
                .submit(4, RevisionFixtures.unitSnapshot(), "H4", "S1", NOW);
        assertEquals(2, submitted.copiedFromRevisionNoOrNull());
    }

    /** A returned revision in its full form, as a decision leaves it. */
    private static ListingRevision returned(int revisionNo) {
        return ListingRevision.load("R" + revisionNo, revisionNo, RevisionStatus.NEEDS_CHANGES, "S1", CREATED, CREATED,
                RevisionFixtures.completeContent(), RevisionFixtures.inEffectTerms(), RevisionFixtures.unitSnapshot(),
                CREATED, "S1");
    }
}
