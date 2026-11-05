package vn.sunvibe.property.core.domain.policy.intake;

import org.junit.jupiter.api.Test;
import vn.sunvibe.property.core.domain.model.enums.AssetType;
import vn.sunvibe.property.core.domain.model.enums.RentalLayout;
import vn.sunvibe.property.core.domain.model.intake.IntakeSourceRef;
import vn.sunvibe.property.core.domain.model.intake.IntakeStatus;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.model.intake.SelectedSpace;
import vn.sunvibe.property.core.domain.policy.intake.CurrentRowPolicy.RowFacts;
import vn.sunvibe.property.core.domain.policy.intake.CurrentRowPolicy.SourceFacts;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** The current row and the effective status of property-create intake spec 4.1. */
class CurrentRowPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-06T02:00:00Z");
    private static final SourceFacts LIVE = new SourceFacts("01PASSED", false, 1, false);

    @Test
    void current_openLiveRow() {
        InventoryIntake open = row(1, "01PASSED", 1);

        assertEquals(Optional.of(open), CurrentRowPolicy.current(List.of(open), LIVE));
    }

    @Test
    void current_completedOnCurrentPassed() {
        InventoryIntake completed = completed(1, "01PASSED", 1);

        assertEquals(Optional.of(completed), CurrentRowPolicy.current(List.of(completed), LIVE));
    }

    /** An open row with a live source comes before a completed one. */
    @Test
    void current_openBeforeCompleted() {
        InventoryIntake completed = completed(1, "01PASSED", 1);
        InventoryIntake open = row(2, "01PASSED", 1);

        assertEquals(Optional.of(open), CurrentRowPolicy.current(List.of(completed, open), LIVE));
    }

    @Test
    void current_openRowDeadSourceReadsNone() {
        InventoryIntake open = row(1, "01PASSED", 1);

        assertEquals(Optional.empty(), CurrentRowPolicy.current(List.of(open),
                new SourceFacts("01OTHER", false, 1, false)), "another PASSED");
        assertEquals(Optional.empty(), CurrentRowPolicy.current(List.of(open), new SourceFacts(null, false, 1, false)),
                "marked");
        assertEquals(Optional.empty(), CurrentRowPolicy.current(List.of(open), new SourceFacts("01PASSED", true, 1, false)),
                "evidence gone");
        assertEquals(Optional.empty(), CurrentRowPolicy.current(List.of(open), new SourceFacts("01PASSED", false, 2, false)),
                "another cycle");
        assertEquals(Optional.empty(), CurrentRowPolicy.current(List.of(open), new SourceFacts("01PASSED", false, 1, true)),
                "closed lead");
    }

    /** spec 4.1: a result on a marked source is no current row, but readiness still reads it. */
    @Test
    void current_completedRowKeptForReadiness() {
        InventoryIntake completed = completed(1, "01MARKED", 1);

        assertEquals(Optional.empty(), CurrentRowPolicy.current(List.of(completed), LIVE));
        assertEquals(Optional.of(completed), CurrentRowPolicy.latestCompleted(List.of(completed)));
    }

    @Test
    void latestCompleted_highestNumberWins() {
        InventoryIntake first = completed(1, "01OLD", 1);
        InventoryIntake second = completed(2, "01PASSED", 1);

        assertEquals(Optional.of(second), CurrentRowPolicy.latestCompleted(List.of(first, second)));
    }

    @Test
    void effective_liveRowReadsStored() {
        assertEquals(Optional.of(IntakeStatus.DRAFT), effective(IntakeStatus.DRAFT, facts(true, false, false, false, false)));
        assertEquals(Optional.of(IntakeStatus.OWNER_CONFIRMED),
                effective(IntakeStatus.OWNER_CONFIRMED, facts(true, false, true, false, false)));
    }

    @Test
    void effective_expiredRequestReadsDraft() {
        assertEquals(Optional.of(IntakeStatus.DRAFT),
                effective(IntakeStatus.AWAITING_OWNER, facts(true, true, false, false, false)));
    }

    @Test
    void effective_deadProofReadsDraft() {
        assertEquals(Optional.of(IntakeStatus.DRAFT),
                effective(IntakeStatus.OWNER_CONFIRMED, facts(true, false, false, false, false)));
        assertEquals(Optional.of(IntakeStatus.DRAFT),
                effective(IntakeStatus.CASE_OPEN, facts(true, false, false, true, false)));
    }

    @Test
    void effective_caseOpenNothingLeft_readsConfirmed() {
        assertEquals(Optional.of(IntakeStatus.OWNER_CONFIRMED),
                effective(IntakeStatus.CASE_OPEN, facts(true, false, true, false, false)));
    }

    /** spec 4.1: a dead source goes first, then the request, the proof and the cases. */
    @Test
    void effective_firstMatchWins() {
        assertEquals(Optional.empty(), effective(IntakeStatus.AWAITING_OWNER, facts(false, true, false, false, false)));
        assertEquals(Optional.empty(), effective(IntakeStatus.CASE_OPEN, facts(false, false, true, true, true)));
        assertEquals(Optional.of(IntakeStatus.DRAFT),
                effective(IntakeStatus.CASE_OPEN, facts(true, false, false, true, true)));
    }

    /** spec 4.1: a hold can appear with a new proof, so a confirmed row can read CASE_OPEN. */
    @Test
    void effective_holdWithNewProof_readsCaseOpen() {
        assertEquals(Optional.of(IntakeStatus.CASE_OPEN),
                effective(IntakeStatus.OWNER_CONFIRMED, facts(true, false, true, true, true)));
    }

    /** An undecided case alone holds a CASE_OPEN row, and leaves a confirmed one as it is. */
    @Test
    void effective_openCaseKeepsCaseOpen() {
        assertEquals(Optional.of(IntakeStatus.CASE_OPEN),
                effective(IntakeStatus.CASE_OPEN, facts(true, false, true, true, false)));
        assertEquals(Optional.of(IntakeStatus.OWNER_CONFIRMED),
                effective(IntakeStatus.OWNER_CONFIRMED, facts(true, false, true, true, false)));
    }

    @Test
    void effective_completedAndAbandonedStay() {
        assertEquals(Optional.of(IntakeStatus.COMPLETED),
                effective(IntakeStatus.COMPLETED, facts(false, false, false, false, false)));
        assertEquals(Optional.of(IntakeStatus.ABANDONED),
                effective(IntakeStatus.ABANDONED, facts(false, false, false, false, false)));
    }

    /** spec 4.1: the block reads as the store would leave it, so a computed DRAFT shows nothing built on the old proof. */
    @Test
    void effective_blockReadsAsTheStoreWould() {
        assertTrue(CurrentRowPolicy.readsCleared(IntakeStatus.OWNER_CONFIRMED, IntakeStatus.DRAFT));
        assertTrue(CurrentRowPolicy.readsCleared(IntakeStatus.CASE_OPEN, IntakeStatus.DRAFT));
        assertFalse(CurrentRowPolicy.readsCleared(IntakeStatus.DRAFT, IntakeStatus.DRAFT));
        assertFalse(CurrentRowPolicy.readsCleared(IntakeStatus.CASE_OPEN, IntakeStatus.OWNER_CONFIRMED));
    }

    /** spec 4.1: expectedVersion is null only with no current row. */
    @Test
    void expectedVersion_nullRules() {
        assertTrue(CurrentRowPolicy.versionMatches(null, null));
        assertTrue(CurrentRowPolicy.versionMatches(3L, 3L));
        assertFalse(CurrentRowPolicy.versionMatches(3L, null), "a value with no current row");
        assertFalse(CurrentRowPolicy.versionMatches(null, 3L), "null on a row");
        assertFalse(CurrentRowPolicy.versionMatches(2L, 3L), "a stale value");
    }

    private static Optional<IntakeStatus> effective(IntakeStatus stored, RowFacts facts) {
        return CurrentRowPolicy.effectiveStatus(stored, facts);
    }

    private static RowFacts facts(boolean sourceLive, boolean requestExpired, boolean proofLive, boolean openOrWaiting,
                                  boolean waiting) {
        return new RowFacts(sourceLive, requestExpired, proofLive, openOrWaiting, waiting);
    }

    private static InventoryIntake row(int no, String checklist, int cycle) {
        return InventoryIntake.create("01INTAKE" + no, no, "01LEAD", cycle, new IntakeSourceRef(checklist, "a".repeat(64),
                        RentalLayout.WHOLE, AssetType.HOUSE, "20242", "1 bach dang", null, null),
                List.of(new SelectedSpace("01SPACE", "A1", "Floor 1")), NOW);
    }

    private static InventoryIntake completed(int no, String checklist, int cycle) {
        InventoryIntake row = row(no, checklist, cycle);
        row.awaitOwner(NOW);
        row.confirmOwner(NOW);
        row.complete("01PROP" + no, "01PROOF" + no, Map.of(), "hash", "01SALE", NOW);
        return row;
    }
}
