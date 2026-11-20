package vn.sunvibe.listing.core.domain.model.listing;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CREATED;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.NOW;

/**
 * A revision's two forms (listing-publish E06-S2 spec E06S2-D19): the full form with its content, and the header form
 * of the newest-revisions read, whose content cannot be taken for an empty one; and what a Pending review revision holds
 * (US-2 AC-4, AC-5).
 */
class ListingRevisionTest {

    private static final RevisionContent CONTENT = RevisionFixtures.completeContent();
    private static final RevisionTerms TERMS = RevisionFixtures.inEffectTerms();
    private static final UnitSnapshot UNIT = RevisionFixtures.unitSnapshot();

    @Test
    void create_newDraft_fullFormEmptyContent() {
        ListingRevision revision = ListingRevision.create("R1", 1, "S1", NOW);

        assertTrue(revision.fullForm());
        assertEquals(RevisionStatus.DRAFT, revision.status());
        assertSame(RevisionContent.EMPTY, revision.content());
        assertNull(revision.termsOrNull());
        assertNull(revision.unitSnapshotOrNull());
        assertNull(revision.submittedAtOrNull());
        assertNull(revision.submittedByOrNull());
        assertEquals(NOW, revision.createdAt());
        assertEquals(NOW, revision.updatedAt());
    }

    @Test
    void load_headerForm_contentAccessorsIllegalState() {
        ListingRevision header = ListingRevision.load("R1", 1, RevisionStatus.PENDING_REVIEW, "S1", CREATED, NOW);

        assertFalse(header.fullForm());
        assertAll(
                () -> assertThrows(IllegalStateException.class, header::content),
                () -> assertThrows(IllegalStateException.class, header::termsOrNull),
                () -> assertThrows(IllegalStateException.class, header::unitSnapshotOrNull),
                () -> assertThrows(IllegalStateException.class, header::submittedAtOrNull),
                () -> assertThrows(IllegalStateException.class, header::submittedByOrNull));
        assertEquals(RevisionStatus.PENDING_REVIEW, header.status());
    }

    @Test
    void load_fullForm_keepsEveryField() {
        ListingRevision revision = ListingRevision.load("R2", 2, RevisionStatus.PENDING_REVIEW, "S1", CREATED, NOW,
                CONTENT, TERMS, UNIT, NOW, "S2");

        assertAll(
                () -> assertEquals("R2", revision.id()),
                () -> assertEquals(2, revision.revisionNo()),
                () -> assertEquals(RevisionStatus.PENDING_REVIEW, revision.status()),
                () -> assertEquals("S1", revision.createdBy()),
                () -> assertEquals(CREATED, revision.createdAt()),
                () -> assertEquals(NOW, revision.updatedAt()),
                () -> assertTrue(revision.fullForm()),
                () -> assertSame(CONTENT, revision.content()),
                () -> assertSame(TERMS, revision.termsOrNull()),
                () -> assertSame(UNIT, revision.unitSnapshotOrNull()),
                () -> assertEquals(NOW, revision.submittedAtOrNull()),
                () -> assertEquals("S2", revision.submittedByOrNull()));
    }

    @Test
    void load_halfSubmission_illegalState() {
        assertAll(
                () -> assertThrows(IllegalStateException.class, () -> ListingRevision.load("R1", 1,
                        RevisionStatus.DRAFT, "S1", CREATED, NOW, CONTENT, TERMS, null, NOW, null)),
                () -> assertThrows(IllegalStateException.class, () -> ListingRevision.load("R1", 1,
                        RevisionStatus.DRAFT, "S1", CREATED, NOW, CONTENT, TERMS, null, null, "S1")),
                () -> assertThrows(IllegalStateException.class, () -> ListingRevision.load("R1", 1,
                        RevisionStatus.DRAFT, "S1", CREATED, NOW, null, TERMS, null, null, null)));
    }

    @Test
    void load_pendingMissingAPart_illegalState() {
        RevisionContent noTitle = new RevisionContent(null, RevisionFixtures.DESCRIPTION, RevisionFeatures.NONE_CHOSEN);
        RevisionContent noDescription = new RevisionContent(RevisionFixtures.TITLE, null, RevisionFeatures.NONE_CHOSEN);
        assertAll(
                () -> assertThrows(IllegalStateException.class, () -> pending(noTitle, TERMS, UNIT, NOW, "S1")),
                () -> assertThrows(IllegalStateException.class, () -> pending(noDescription, TERMS, UNIT, NOW, "S1")),
                () -> assertThrows(IllegalStateException.class, () -> pending(CONTENT, null, UNIT, NOW, "S1")),
                () -> assertThrows(IllegalStateException.class, () -> pending(CONTENT, TERMS, null, NOW, "S1")),
                () -> assertThrows(IllegalStateException.class, () -> pending(CONTENT, TERMS, UNIT, null, null)),
                () -> assertDoesNotThrow(() -> pending(CONTENT, TERMS, UNIT, NOW, "S1")));
    }

    @Test
    void load_cancelledWithoutContent_allowed() {
        ListingRevision cancelled = ListingRevision.load("R1", 1, RevisionStatus.CANCELLED, "S1", CREATED, NOW,
                RevisionContent.EMPTY, null, null, null, null);

        assertEquals(RevisionStatus.CANCELLED, cancelled.status());
        assertSame(RevisionContent.EMPTY, cancelled.content());
    }

    private static ListingRevision pending(RevisionContent content, RevisionTerms terms, UnitSnapshot unit,
                                           Instant submittedAt, String submittedBy) {
        return ListingRevision.load("R1", 1, RevisionStatus.PENDING_REVIEW, "S1", CREATED, NOW, content, terms, unit,
                submittedAt, submittedBy);
    }
}
