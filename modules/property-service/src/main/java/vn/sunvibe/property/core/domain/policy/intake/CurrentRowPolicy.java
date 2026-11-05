package vn.sunvibe.property.core.domain.policy.intake;

import vn.sunvibe.property.core.domain.model.intake.IntakeStatus;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The current row of a lead and cycle and the effective status of a row (property-create intake spec 4.1). Both are
 * computed on read and change nothing; the first accepted command stores what a read computed.
 */
public final class CurrentRowPolicy {

    private CurrentRowPolicy() {
    }

    /**
     * What decides whether a row's source is live.
     *
     * @param currentPassedIdOrNull the lead's current unmarked PASSED in its current cycle
     * @param evidenceGone          that revision lost a frozen photo for good
     * @param leadCycle             the lead's current intent cycle
     * @param leadClosed            the lead is closed
     */
    public record SourceFacts(String currentPassedIdOrNull, boolean evidenceGone, int leadCycle, boolean leadClosed) {
    }

    /**
     * What a read knows about one row.
     *
     * @param requestExpired    the row's latest request is past its end
     * @param proofLive         the row has a live proof (I-3)
     * @param caseOpenOrWaiting a case of the row is undecided, or holds it
     * @param waiting           a DECIDED SAME_ASSET holds the row
     */
    public record RowFacts(boolean sourceLive, boolean requestExpired, boolean proofLive, boolean caseOpenOrWaiting,
                           boolean waiting) {
    }

    /** spec 4.1: the current unmarked PASSED, not EVIDENCE_GONE, in the lead's current cycle, on an open lead. */
    public static boolean sourceLive(InventoryIntake row, SourceFacts facts) {
        return !facts.leadClosed() && !facts.evidenceGone() && row.getIntentCycle() == facts.leadCycle()
                && Objects.equals(row.getSourceChecklistId(), facts.currentPassedIdOrNull());
    }

    /** Its open row with a live source; else its COMPLETED row on the current unmarked PASSED; else none. */
    public static Optional<InventoryIntake> current(List<InventoryIntake> rows, SourceFacts facts) {
        Optional<InventoryIntake> open = rows.stream()
                .filter(row -> row.getStatus().isOpen() && sourceLive(row, facts)).findFirst();
        if (open.isPresent()) {
            return open;
        }
        return rows.stream().filter(row -> row.getStatus() == IntakeStatus.COMPLETED
                        && row.getIntentCycle() == facts.leadCycle()
                        && Objects.equals(row.getSourceChecklistId(), facts.currentPassedIdOrNull()))
                .max(Comparator.comparingInt(InventoryIntake::getIntakeNo));
    }

    /** The lead's latest COMPLETED row, current or not: what readiness and {@code earlierResult} read. */
    public static Optional<InventoryIntake> latestCompleted(List<InventoryIntake> rows) {
        return rows.stream().filter(row -> row.getStatus() == IntakeStatus.COMPLETED)
                .max(Comparator.comparingInt(InventoryIntake::getIntakeNo));
    }

    /** First match wins (spec 4.1); empty reads as none. */
    public static Optional<IntakeStatus> effectiveStatus(IntakeStatus stored, RowFacts facts) {
        if (!stored.isOpen()) {
            return Optional.of(stored);
        }
        if (!facts.sourceLive()) {
            return Optional.empty();
        }
        if (stored == IntakeStatus.AWAITING_OWNER && facts.requestExpired()) {
            return Optional.of(IntakeStatus.DRAFT);
        }
        boolean confirmed = stored == IntakeStatus.OWNER_CONFIRMED || stored == IntakeStatus.CASE_OPEN;
        if (confirmed && !facts.proofLive()) {
            return Optional.of(IntakeStatus.DRAFT);
        }
        if (stored == IntakeStatus.CASE_OPEN && !facts.caseOpenOrWaiting()) {
            return Optional.of(IntakeStatus.OWNER_CONFIRMED);
        }
        if (stored == IntakeStatus.OWNER_CONFIRMED && facts.waiting()) {
            return Optional.of(IntakeStatus.CASE_OPEN);
        }
        return Optional.of(stored);
    }

    /** A computed DRAFT shows no target, availability or case: the block reads as the store would leave it. */
    public static boolean readsCleared(IntakeStatus stored, IntakeStatus effective) {
        return stored != IntakeStatus.DRAFT && effective == IntakeStatus.DRAFT;
    }

    /** spec 4.1: expectedVersion is null only with no current row, and a value then, or null on a row, is stale. */
    public static boolean versionMatches(Long expected, Long rowVersionOrNull) {
        return Objects.equals(expected, rowVersionOrNull);
    }
}
