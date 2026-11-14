package vn.sunvibe.property.core.domain.policy.intake;

import org.junit.jupiter.api.Test;
import vn.sunvibe.property.core.domain.model.intake.CaseOutcome;
import vn.sunvibe.property.core.domain.model.intake.CaseSignal;
import vn.sunvibe.property.core.domain.model.intake.CaseStatus;
import vn.sunvibe.property.core.domain.model.intake.DuplicateCase;
import vn.sunvibe.property.core.domain.model.intake.OwnerCheckState;
import vn.sunvibe.property.core.domain.model.intake.OwnerProof;
import vn.sunvibe.property.core.domain.model.intake.ProofBinding;
import vn.sunvibe.property.core.domain.policy.intake.CasePolicy.CaseFacts;
import vn.sunvibe.property.core.domain.policy.intake.CasePolicy.Reading;
import vn.sunvibe.property.core.domain.policy.intake.CasePolicy.Side;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Case liveness, holds and the forced property of property-create intake spec 4.10. */
class CasePolicyTest {

    private static final Instant T0 = Instant.parse("2026-11-06T02:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);
    private static final Instant T2 = T0.plusSeconds(120);
    private static final Instant T3 = T0.plusSeconds(180);
    private static final String ME = "01INTAKEME";
    private static final String OTHER = "01INTAKEOTHER";
    private static final String OWNER = "01OWNER";
    private static final String STRANGER = "01STRANGER";

    /** A side is live while open with a live source and proof on the pair's scope. */
    @Test
    void side_liveRules() {
        assertTrue(side(ME, true, null, null).live());
        assertFalse(side(ME, false, null, null).live());
    }

    @Test
    void side_completedProceedingIntake_live() {
        DuplicateCase decided = decided(intakeCase("01C"), CaseOutcome.SAME_ASSET, OTHER, T0);
        CaseFacts facts = new CaseFacts(decided, side(ME, true, OWNER, null), completedSide(OTHER, "01RESULT"), "01OWNER");

        assertEquals(Reading.FORCED, CasePolicy.read(facts));
        assertEquals("01RESULT", CasePolicy.namedProperty(facts));
    }

    /** An OPEN case with a dead side reads closed everywhere. */
    @Test
    void case_openWithDeadSide_readsClosed() {
        assertEquals(Reading.CLOSED, CasePolicy.read(new CaseFacts(propertyCase("01C"), side(ME, false, null, null), null, null)));
        assertEquals(Reading.CLOSED, CasePolicy.read(new CaseFacts(intakeCase("01C"), side(ME, true, null, null),
                side(OTHER, false, null, null), null)));
        assertEquals(Reading.OPEN, CasePolicy.read(new CaseFacts(propertyCase("01C"), side(ME, true, null, null), null, null)));
        assertEquals(Reading.OPEN, CasePolicy.read(new CaseFacts(intakeCase("01C"), side(ME, true, null, null),
                side(OTHER, true, null, null), null)));
    }

    /** A DECIDED case stays DECIDED, because its ruling binds: a dead side does not close it. */
    @Test
    void case_decidedStaysDecided() {
        DuplicateCase different = decided(propertyCase("01C"), CaseOutcome.DIFFERENT_ASSET, null, T0);

        assertEquals(Reading.RELEASED, CasePolicy.read(new CaseFacts(different, side(ME, false, null, null), null, OWNER)));
    }

    @Test
    void case_storedClosed_readsClosed() {
        assertEquals(Reading.CLOSED, CasePolicy.read(new CaseFacts(closed(propertyCase("01C")), side(ME, true, null, null),
                null, OWNER)));
    }

    /** spec 4.10 row 2: SAME_ASSET on a property of the checked proof's user forces it. */
    @Test
    void forcedProperty_computedFromDecidedRows() {
        DuplicateCase same = decided(propertyCase("01C"), CaseOutcome.SAME_ASSET, null, T0);
        CaseFacts facts = new CaseFacts(same, side(ME, true, OWNER, null), null, OWNER);

        assertEquals(Reading.FORCED, CasePolicy.read(facts));
        assertEquals(Optional.of("01PROPERTY"), CasePolicy.forcedProperty(List.of(facts)));
    }

    /** spec 4.10 row 3: on another user's property the intake holds. */
    @Test
    void sameAsset_otherUsersProperty_waits() {
        DuplicateCase same = decided(propertyCase("01C"), CaseOutcome.SAME_ASSET, null, T0);

        assertEquals(Reading.WAITING, CasePolicy.read(new CaseFacts(same, side(ME, true, STRANGER, null), null, OWNER)));
    }

    /** Two same-user rows bind the earliest by decided_at, then id; later rows bind nothing. */
    @Test
    void forcedProperty_earliestDecisionBinds() {
        CaseFacts late = forced("01A", "01PROPLATE", T0.plusSeconds(60));
        CaseFacts early = forced("01Z", "01PROPEARLY", T0);
        CaseFacts sameTimeLowerId = forced("01B", "01PROPLOWER", T0);

        assertEquals(Optional.of("01PROPEARLY"), CasePolicy.forcedProperty(List.of(late, early)));
        assertEquals(Optional.of("01PROPLOWER"), CasePolicy.forcedProperty(List.of(early, sameTimeLowerId, late)));
    }

    /** A DECIDED SAME_ASSET holds and forces only while its waiting intake has a checked proof. */
    @Test
    void forcedProperty_uncheckedProof_flipsNothing() {
        DuplicateCase same = decided(propertyCase("01C"), CaseOutcome.SAME_ASSET, null, T0);
        CaseFacts unchecked = new CaseFacts(same, side(ME, true, null, null), null, OWNER);

        assertEquals(Reading.RELEASED, CasePolicy.read(unchecked));
        assertEquals(Optional.empty(), CasePolicy.forcedProperty(List.of(unchecked)));
    }

    /** spec 4.10 row 4: the loser waits while its winner is live and open, checked or not. */
    @Test
    void intakeCandidate_openWinner_loserWaits() {
        DuplicateCase same = decided(intakeCase("01C"), CaseOutcome.SAME_ASSET, OTHER, T0);

        assertEquals(Reading.WAITING, CasePolicy.read(new CaseFacts(same, side(ME, true, OWNER, null),
                side(OTHER, true, null, null), null)));
    }

    @Test
    void intakeCandidate_winnerDied_loserReleased() {
        DuplicateCase same = decided(intakeCase("01C"), CaseOutcome.SAME_ASSET, OTHER, T0);

        assertEquals(Reading.RELEASED, CasePolicy.read(new CaseFacts(same, side(ME, true, OWNER, null),
                side(OTHER, false, null, null), null)));
    }

    /** The waiting intake of an I: pair is the loser, whichever side of the row it sits on. */
    @Test
    void intakeCandidate_winnerIsTheCasesIntake_otherWaits() {
        DuplicateCase same = decided(intakeCase("01C"), CaseOutcome.SAME_ASSET, ME, T0);

        assertEquals(OTHER, CasePolicy.waitingIntake(same));
    }

    /** spec 4.10 row 4: a completed winner reads as SAME_ASSET on its result, by the two rows above. */
    @Test
    void intakeCandidate_completedWinner_otherOwner_waits() {
        DuplicateCase same = decided(intakeCase("01C"), CaseOutcome.SAME_ASSET, OTHER, T0);

        assertEquals(Reading.WAITING, CasePolicy.read(new CaseFacts(same, side(ME, true, STRANGER, null),
                completedSide(OTHER, "01RESULT"), OWNER)));
    }

    @Test
    void differentAsset_released() {
        DuplicateCase different = decided(propertyCase("01C"), CaseOutcome.DIFFERENT_ASSET, null, T0);

        assertEquals(Reading.RELEASED, CasePolicy.read(new CaseFacts(different, side(ME, true, OWNER, null), null, OWNER)));
    }

    @Test
    void caseState_summary() {
        assertEquals(CaseState.NONE, CasePolicy.caseState(List.of()));
        assertEquals(CaseState.OPEN, CasePolicy.caseState(List.of(Reading.OPEN, Reading.WAITING)));
        assertEquals(CaseState.WAITING, CasePolicy.caseState(List.of(Reading.RELEASED, Reading.WAITING)));
        assertEquals(CaseState.DECIDED, CasePolicy.caseState(List.of(Reading.RELEASED, Reading.FORCED)));
        assertEquals(CaseState.NONE, CasePolicy.caseState(List.of(Reading.CLOSED)));
    }

    @Test
    void pairKey_formsOfSpec() {
        String scope = "a".repeat(64);
        String other = "b".repeat(64);

        assertEquals("P:01PROP:01INTAKE:" + "a".repeat(16), PairKey.property("01PROP", "01INTAKE", scope));
        assertEquals("L:01PROP:01INTAKE:" + "a".repeat(16), PairKey.linkPlace("01PROP", "01INTAKE", scope));
        assertEquals("I:01A:01B:" + "a".repeat(16) + ":" + "b".repeat(16), PairKey.intakes("01A", scope, "01B", other));
        assertEquals("I:01A:01B:" + "a".repeat(16) + ":" + "b".repeat(16), PairKey.intakes("01B", other, "01A", scope));
    }

    // ==================== showsAnotherOwner: brokerage-agreement BR-3 (c), E-15 (SV-215) ====================

    @Test
    void showsAnotherOwner_proofDeadBeforeTheDecision_false() {
        DuplicateCase same = decided(propertyCase("01C"), CaseOutcome.SAME_ASSET, null, T2);

        assertFalse(CasePolicy.showsAnotherOwner(same, List.of(proof(ME, STRANGER, OwnerCheckState.DONE, T0, null, T1)), OWNER));
        assertFalse(CasePolicy.showsAnotherOwner(same, List.of(proof(ME, STRANGER, OwnerCheckState.DONE, T0, T1, null)), OWNER));
    }

    @Test
    void showsAnotherOwner_proofInForceAtTheDecision_trueAlsoAfterItDies() {
        DuplicateCase same = decided(propertyCase("01C"), CaseOutcome.SAME_ASSET, null, T2);

        assertTrue(CasePolicy.showsAnotherOwner(same, List.of(proof(ME, STRANGER, OwnerCheckState.DONE, T0, null, null)), OWNER));
        assertTrue(CasePolicy.showsAnotherOwner(same, List.of(proof(ME, STRANGER, OwnerCheckState.DONE, T0, null, T3)), OWNER));
        assertTrue(CasePolicy.showsAnotherOwner(same, List.of(proof(ME, STRANGER, OwnerCheckState.DONE, T0, T3, null)), OWNER));
        assertTrue(CasePolicy.showsAnotherOwner(same, List.of(proof(ME, STRANGER, OwnerCheckState.DONE, T0, null, T2)), OWNER));
    }

    @Test
    void showsAnotherOwner_proofCheckedAfterTheDecision_true() {
        DuplicateCase same = decided(propertyCase("01C"), CaseOutcome.SAME_ASSET, null, T2);

        assertTrue(CasePolicy.showsAnotherOwner(same, List.of(proof(ME, STRANGER, OwnerCheckState.DONE, T3, null, null)), OWNER));
    }

    @Test
    void showsAnotherOwner_ownersOrUncheckedOrOtherIntakeProof_false() {
        DuplicateCase same = decided(propertyCase("01C"), CaseOutcome.SAME_ASSET, null, T2);

        assertFalse(CasePolicy.showsAnotherOwner(same, List.of(proof(ME, OWNER, OwnerCheckState.DONE, T0, null, null)), OWNER));
        assertFalse(CasePolicy.showsAnotherOwner(same, List.of(proof(ME, STRANGER, OwnerCheckState.REQUIRED, T0, null, null)),
                OWNER));
        assertFalse(CasePolicy.showsAnotherOwner(same, List.of(proof(ME, STRANGER, OwnerCheckState.REFUSED, T0, null, null)),
                OWNER));
        assertFalse(CasePolicy.showsAnotherOwner(same, List.of(proof(OTHER, STRANGER, OwnerCheckState.DONE, T0, null, null)),
                OWNER));
    }

    @Test
    void showsAnotherOwner_notADecidedSameAsset_false() {
        List<OwnerProof> live = List.of(proof(ME, STRANGER, OwnerCheckState.DONE, T0, null, null));

        assertFalse(CasePolicy.showsAnotherOwner(propertyCase("01C"), live, OWNER));
        assertFalse(CasePolicy.showsAnotherOwner(closed(propertyCase("01C")), live, OWNER));
        assertFalse(CasePolicy.showsAnotherOwner(decided(propertyCase("01C"), CaseOutcome.DIFFERENT_ASSET, null, T2), live, OWNER));
    }

    @Test
    void showsAnotherOwner_noOwner_anyCheckedProofCounts() {
        DuplicateCase same = decided(propertyCase("01C"), CaseOutcome.SAME_ASSET, null, T2);

        assertTrue(CasePolicy.showsAnotherOwner(same, List.of(proof(ME, OWNER, OwnerCheckState.DONE, T0, null, null)), null));
    }

    @Test
    void showsAnotherOwner_intakePair_readsTheWaitingIntakeOnly() {
        DuplicateCase same = decided(intakeCase("01C"), CaseOutcome.SAME_ASSET, OTHER, T2);

        assertTrue(CasePolicy.showsAnotherOwner(same, List.of(proof(ME, STRANGER, OwnerCheckState.DONE, T0, null, null)), OWNER));
        assertFalse(CasePolicy.showsAnotherOwner(same, List.of(proof(OTHER, STRANGER, OwnerCheckState.DONE, T0, null, null)),
                OWNER));
    }

    private static OwnerProof proof(String intakeId, String userId, OwnerCheckState check, Instant confirmedAt,
                                    Instant withdrawnAt, Instant voidedAt) {
        return OwnerProof.load("01PROOF", intakeId, "01REQUEST", new ProofBinding("01LEAD", 1, "01CHECKLIST", "scope"), userId,
                "owner-link-2026-11", confirmedAt, withdrawnAt, voidedAt, check, 0, null, null, 0, null, null, null);
    }

    private static DuplicateCase decided(DuplicateCase row, CaseOutcome outcome, String proceeding, Instant at) {
        row.decide(outcome, proceeding, "The basis", "01MANAGER", at);
        return row;
    }

    private static DuplicateCase closed(DuplicateCase row) {
        row.close();
        return row;
    }

    private static CaseFacts forced(String id, String property, Instant decidedAt) {
        DuplicateCase same = decided(propertyCase(id, property), CaseOutcome.SAME_ASSET, null, decidedAt);
        return new CaseFacts(same, side(ME, true, OWNER, null), null, OWNER);
    }

    private static Side side(String intakeId, boolean live, String checkedProofUser, String resultProperty) {
        return new Side(intakeId, live, false, checkedProofUser, resultProperty);
    }

    private static Side completedSide(String intakeId, String resultProperty) {
        return new Side(intakeId, true, true, null, resultProperty);
    }

    private static DuplicateCase propertyCase(String id) {
        return propertyCase(id, "01PROPERTY");
    }

    private static DuplicateCase propertyCase(String id, String property) {
        return DuplicateCase.load(id, ME, property, null, "P:" + id, "scope", null, CaseSignal.ADDRESS, CaseStatus.OPEN, null,
                null, null, null, null, 0L, T0);
    }

    private static DuplicateCase intakeCase(String id) {
        return DuplicateCase.load(id, ME, null, OTHER, "I:" + id, "scope", "other", CaseSignal.INTAKE, CaseStatus.OPEN, null,
                null, null, null, null, 0L, T0);
    }
}
