package vn.sunvibe.property.core.domain.policy.intake;

import vn.sunvibe.property.core.domain.model.intake.CaseOutcome;
import vn.sunvibe.property.core.domain.model.intake.CaseStatus;
import vn.sunvibe.property.core.domain.model.intake.DuplicateCase;
import vn.sunvibe.property.core.domain.model.intake.OwnerCheckState;
import vn.sunvibe.property.core.domain.model.intake.OwnerProof;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * How a duplicate case reads now (property-create intake spec 4.10). Who owns what is read each time, never stored, so a
 * later checked proof by another account gets the other row of the decision table. An OPEN case with a dead side reads
 * closed everywhere, and a DECIDED case stays decided, because its ruling binds.
 */
public final class CasePolicy {

    private CasePolicy() {
    }

    /** What a case means now. */
    public enum Reading {
        /** Undecided, with both sides live. */
        OPEN,
        /** An exit ended it, or a side died before a decision. */
        CLOSED,
        /** Decided, and it stops nothing now. */
        RELEASED,
        /** A SAME_ASSET decision holds the waiting intake. */
        WAITING,
        /** A SAME_ASSET decision names the property the waiting intake must link to. */
        FORCED
    }

    /**
     * One intake side of a case.
     *
     * @param live                   open, with a live source and proof, on the pair's scope; a COMPLETED winner is live too
     * @param completed              the row is COMPLETED
     * @param checkedProofUserOrNull the user of its live proof whose check is DONE
     * @param resultPropertyIdOrNull its result, when COMPLETED
     */
    public record Side(String intakeId, boolean live, boolean completed, String checkedProofUserOrNull,
                       String resultPropertyIdOrNull) {
    }

    /**
     * A case with what it needs to be read.
     *
     * @param side                the side of {@code intake_id}
     * @param candidateOrNull     the side of {@code candidate_intake_id}
     * @param propertyOwnerOrNull the owner of the property the case names, directly or as a completed winner's result
     */
    public record CaseFacts(DuplicateCase row, Side side, Side candidateOrNull, String propertyOwnerOrNull) {
    }

    public static Reading read(CaseFacts facts) {
        DuplicateCase row = facts.row();
        if (row.getStatus() == CaseStatus.CLOSED) {
            return Reading.CLOSED;
        }
        if (row.getStatus() == CaseStatus.OPEN) {
            return sidesLive(facts) ? Reading.OPEN : Reading.CLOSED;
        }
        if (row.getOutcome() == CaseOutcome.DIFFERENT_ASSET) {
            return Reading.RELEASED;
        }
        if (row.onProperty()) {
            return byOwner(facts.side(), facts.propertyOwnerOrNull());
        }
        Side winner = winner(facts);
        if (winner.completed()) {
            return byOwner(waiting(facts), facts.propertyOwnerOrNull());
        }
        return winner.live() ? Reading.WAITING : Reading.RELEASED;
    }

    /** The intake a SAME_ASSET decision can hold: the case's intake, or the loser of an intake pair. */
    public static String waitingIntake(DuplicateCase row) {
        if (row.onProperty() || !row.getIntakeId().equals(row.getProceedingIntakeId())) {
            return row.getIntakeId();
        }
        return row.getCandidateIntakeId();
    }

    /**
     * Whether a decision shows another owner of the property it names (brokerage-agreement BR-3 (c), E-15; SV-215): it is
     * a SAME_ASSET decision, and its waiting intake has a proof checked by a manager for an account other than the
     * property's owner (any account when the property has none) that was in force when the manager ruled or was confirmed
     * after: neither withdrawn nor voided before the decision, a time equal to it counting as in force. Once shown it stays,
     * also after that proof or its lead dies, until a separate flow exists; a proof that died before the decision showed
     * nothing to the ruling.
     *
     * @param proofs the proofs of the waiting intake's lead; only the waiting intake's own count
     */
    public static boolean showsAnotherOwner(DuplicateCase row, List<OwnerProof> proofs, String propertyOwnerOrNull) {
        if (row.getStatus() != CaseStatus.DECIDED || row.getOutcome() != CaseOutcome.SAME_ASSET) {
            return false;
        }
        String waiting = waitingIntake(row);
        Instant decidedAt = row.getDecidedAt();
        return proofs.stream().anyMatch(proof -> waiting.equals(proof.getIntakeId())
                && proof.getOwnerCheck() == OwnerCheckState.DONE && !proof.getUserId().equals(propertyOwnerOrNull)
                && notBefore(proof.getWithdrawnAt(), decidedAt) && notBefore(proof.getVoidedAt(), decidedAt));
    }

    private static boolean notBefore(Instant atOrNull, Instant decidedAt) {
        return atOrNull == null || !atOrNull.isBefore(decidedAt);
    }

    /** The property the case names: its candidate, or a completed winner's result; null while a winner is open. */
    public static String namedProperty(CaseFacts facts) {
        if (facts.row().onProperty()) {
            return facts.row().getCandidatePropertyId();
        }
        return facts.row().getProceedingIntakeId() == null ? null : winner(facts).resultPropertyIdOrNull();
    }

    /** The same-user property of the earliest decision, by decided_at, then id; later rows bind nothing. */
    public static Optional<String> forcedProperty(List<CaseFacts> cases) {
        return cases.stream().filter(facts -> read(facts) == Reading.FORCED)
                .min(Comparator.comparing((CaseFacts facts) -> facts.row().getDecidedAt())
                        .thenComparing(facts -> facts.row().getId()))
                .map(CasePolicy::namedProperty);
    }

    /** What the block shows; CLOSED readings count as no case. */
    public static CaseState caseState(List<Reading> readings) {
        if (readings.contains(Reading.OPEN)) {
            return CaseState.OPEN;
        }
        if (readings.contains(Reading.WAITING)) {
            return CaseState.WAITING;
        }
        return readings.contains(Reading.RELEASED) || readings.contains(Reading.FORCED) ? CaseState.DECIDED
                : CaseState.NONE;
    }

    private static boolean sidesLive(CaseFacts facts) {
        return facts.side().live() && (facts.candidateOrNull() == null || facts.candidateOrNull().live());
    }

    private static Reading byOwner(Side waiting, String ownerOrNull) {
        if (!waiting.live() || waiting.checkedProofUserOrNull() == null) {
            return Reading.RELEASED;
        }
        return waiting.checkedProofUserOrNull().equals(ownerOrNull) ? Reading.FORCED : Reading.WAITING;
    }

    private static Side waiting(CaseFacts facts) {
        return waitingIntake(facts.row()).equals(facts.side().intakeId()) ? facts.side() : facts.candidateOrNull();
    }

    private static Side winner(CaseFacts facts) {
        return facts.row().getProceedingIntakeId().equals(facts.side().intakeId()) ? facts.side()
                : facts.candidateOrNull();
    }
}
