package vn.sunvibe.listing.core.domain.model.listing;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The shape of a review decision held in the domain (listing-publish US-4 AC-3, BR-7; E06-S4 spec E06S4-D6, D8; RV-6):
 * an approval confirms the five checks of version 1 and carries no reason, group or note; a return 1 to 5 distinct
 * reasons and a note; a rejection a group and a note. Its text names no reason, group or note.
 */
class RevisionDecisionTest {

    private static final Instant NOW = Instant.parse("2026-11-22T03:00:00.123456Z");
    private static final ReviewNote NOTE = ReviewNote.ofOrNull("Mô tả thiếu diện tích phòng");

    @Test
    void approved_fiveChecks_versionOneNoReasonsNoNote() {
        List<ReviewCheck> reversed = new ArrayList<>(ReviewCheck.VERSION_1);
        java.util.Collections.reverse(reversed);

        RevisionDecision decision = RevisionDecision.approved("R2", 2, reversed, "U9", NOW);

        assertAll(
                () -> assertEquals("R2", decision.revisionId()),
                () -> assertEquals(2, decision.revisionNo()),
                () -> assertEquals(ReviewOutcome.APPROVED, decision.outcome()),
                () -> assertEquals(RevisionStatus.APPROVED, decision.outcome().status()),
                () -> assertEquals("U9", decision.decidedBy()),
                () -> assertEquals(NOW, decision.decidedAt()),
                () -> assertEquals(1, decision.checklistVersionOrNull()),
                () -> assertEquals(List.of(), decision.changeReasons()),
                () -> assertNull(decision.rejectionGroupOrNull()),
                () -> assertNull(decision.noteOrNull()));
    }

    @Test
    void approved_fourChecksOrRepeated_refused() {
        List<ReviewCheck> four = ReviewCheck.VERSION_1.subList(0, 4);
        List<ReviewCheck> repeated = new ArrayList<>(four);
        repeated.add(ReviewCheck.CONTENT_MATCHES_UNIT);
        List<ReviewCheck> six = new ArrayList<>(ReviewCheck.VERSION_1);
        six.add(ReviewCheck.NO_FRAUD_SIGNS);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RevisionDecision.approved("R2", 2, four, "U9", NOW)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RevisionDecision.approved("R2", 2, repeated, "U9", NOW)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RevisionDecision.approved("R2", 2, six, "U9", NOW)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RevisionDecision.approved("R2", 2, null, "U9", NOW)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionDecision("R2", 2, ReviewOutcome.APPROVED, "U9", NOW, null, List.of(), null,
                                null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionDecision("R2", 2, ReviewOutcome.APPROVED, "U9", NOW, 1,
                                List.of(ChangeReason.OTHER), null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionDecision("R2", 2, ReviewOutcome.APPROVED, "U9", NOW, 1, List.of(),
                                RejectionGroup.OTHER, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionDecision("R2", 2, ReviewOutcome.APPROVED, "U9", NOW, 1, List.of(), null,
                                NOTE)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionDecision("R2", 2, ReviewOutcome.APPROVED, "U9", NOW, 0, List.of(), null,
                                null)));
    }

    @Test
    void needsChanges_reasonsAndNote_kept() {
        RevisionDecision decision = RevisionDecision.needsChanges("R2", 2,
                List.of(ChangeReason.OTHER, ChangeReason.CONTENT_MISMATCH), NOTE, "U9", NOW);

        assertAll(
                () -> assertEquals(ReviewOutcome.NEEDS_CHANGES, decision.outcome()),
                () -> assertEquals(List.of(ChangeReason.CONTENT_MISMATCH, ChangeReason.OTHER), decision.changeReasons()),
                () -> assertEquals(NOTE, decision.noteOrNull()),
                () -> assertNull(decision.checklistVersionOrNull()),
                () -> assertNull(decision.rejectionGroupOrNull()));
        assertEquals(5, RevisionDecision.needsChanges("R2", 2, List.of(ChangeReason.values()), NOTE, "U9", NOW)
                .changeReasons().size());
    }

    @Test
    void needsChanges_noReasonRepeatedReasonOrNoNote_refused() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RevisionDecision.needsChanges("R2", 2, List.of(), NOTE, "U9", NOW)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RevisionDecision.needsChanges("R2", 2, null, NOTE, "U9", NOW)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RevisionDecision.needsChanges("R2", 2,
                                List.of(ChangeReason.OTHER, ChangeReason.OTHER), NOTE, "U9", NOW)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RevisionDecision.needsChanges("R2", 2, List.of(ChangeReason.OTHER), null, "U9", NOW)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionDecision("R2", 2, ReviewOutcome.NEEDS_CHANGES, "U9", NOW, null,
                                List.of(ChangeReason.OTHER), RejectionGroup.OTHER, NOTE)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionDecision("R2", 2, ReviewOutcome.NEEDS_CHANGES, "U9", NOW, 1,
                                List.of(ChangeReason.OTHER), null, NOTE)));
    }

    @Test
    void rejected_groupAndNote_kept() {
        RevisionDecision decision = RevisionDecision.rejected("R2", 2, RejectionGroup.WRONG_UNIT, NOTE, "U9", NOW);

        assertAll(
                () -> assertEquals(ReviewOutcome.REJECTED, decision.outcome()),
                () -> assertEquals(RejectionGroup.WRONG_UNIT, decision.rejectionGroupOrNull()),
                () -> assertEquals(NOTE, decision.noteOrNull()),
                () -> assertEquals(List.of(), decision.changeReasons()),
                () -> assertNull(decision.checklistVersionOrNull()));
    }

    @Test
    void rejected_noGroupOrNoNote_refused() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RevisionDecision.rejected("R2", 2, null, NOTE, "U9", NOW)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RevisionDecision.rejected("R2", 2, RejectionGroup.OTHER, null, "U9", NOW)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionDecision("R2", 2, ReviewOutcome.REJECTED, "U9", NOW, null,
                                List.of(ChangeReason.OTHER), RejectionGroup.OTHER, NOTE)));
    }

    @Test
    void toString_namesNoReasonGroupOrNote() {
        String returned = RevisionDecision.needsChanges("R2", 2, List.of(ChangeReason.TERMS_MISMATCH), NOTE, "U9", NOW)
                .toString();
        String rejected = RevisionDecision.rejected("R3", 3, RejectionGroup.SUSPECTED_FRAUD, NOTE, "U9", NOW)
                .toString();

        assertEquals("RevisionDecision[R2, NEEDS_CHANGES]", returned);
        assertFalse(returned.contains("TERMS_MISMATCH"), returned);
        assertFalse(rejected.contains("SUSPECTED_FRAUD"), rejected);
        assertFalse(returned.contains("diện tích"), returned);
        assertFalse(rejected.contains("diện tích"), rejected);
        assertFalse(returned.contains("U9"), returned);
    }
}
