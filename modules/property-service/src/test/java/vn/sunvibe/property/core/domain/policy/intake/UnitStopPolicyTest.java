package vn.sunvibe.property.core.domain.policy.intake;

import org.junit.jupiter.api.Test;
import vn.sunvibe.property.core.domain.model.intake.OwnerCheckState;
import vn.sunvibe.property.core.domain.model.intake.OwnerProof;
import vn.sunvibe.property.core.domain.model.intake.ProofBinding;
import vn.sunvibe.property.core.domain.policy.intake.UnitStopPolicy.Completion;
import vn.sunvibe.property.core.domain.policy.intake.UnitStopPolicy.Facts;
import vn.sunvibe.property.core.domain.policy.intake.UnitStopPolicy.OtherLead;
import vn.sunvibe.property.core.domain.policy.intake.UnitStopPolicy.Stop;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The stops of brokerage-agreement BR-3 on one unit, from another lead or from the property (SV-215, spec section 3.1). */
class UnitStopPolicyTest {

    private static final Instant T0 = Instant.parse("2026-11-06T02:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);
    private static final Instant T2 = T0.plusSeconds(120);
    private static final Instant T3 = T0.plusSeconds(180);
    private static final Instant T4 = T0.plusSeconds(240);
    private static final String U1 = "U1";
    private static final String U2 = "U2";
    private static final String OWNER = "01OWNER";
    private static final String STRANGER = "01STRANGER";
    private static final List<Stop> NONE = List.of();
    private static final List<Stop> PROPERTY = List.of(Stop.PROPERTY_STOP);
    private static final List<Stop> UNIT = List.of(Stop.UNIT_STOP);

    @Test
    void stops_nothingStopped_none() {
        Facts facts = new Facts(true, true, false, false, List.of(), List.of());

        assertEquals(NONE, UnitStopPolicy.stops(U1, facts));
    }

    @Test
    void stops_ownerNotEligibleNotAloneDisputedOrRuledOut_propertyStop() {
        List<Facts> each = List.of(new Facts(false, true, false, false, List.of(), List.of()),
                new Facts(true, false, false, false, List.of(), List.of()),
                new Facts(true, true, true, false, List.of(), List.of()),
                new Facts(true, true, false, true, List.of(), List.of()));

        for (Facts facts : each) {
            assertEquals(PROPERTY, UnitStopPolicy.stops(U1, facts), facts.toString());
            assertEquals(PROPERTY, UnitStopPolicy.stops(U2, facts), facts.toString());
        }
    }

    /** The record has no lead status: a closed lead's withdrawal stops the same way (spec SV215-D3 (d)). */
    @Test
    void stops_otherLeadRestrictionOrWithdrawal_propertyStop() {
        for (OtherLead other : List.of(new OtherLead(true, null, Set.of()), new OtherLead(false, T1, Set.of()))) {
            Facts facts = facts(List.of(other), List.of());

            assertEquals(PROPERTY, UnitStopPolicy.stops(U1, facts), other.toString());
            assertEquals(PROPERTY, UnitStopPolicy.stops(U2, facts), other.toString());
        }
    }

    /** Spec SV215-D3 (d) end 2: the property's owner confirming again, strictly after the withdrawal, on any result of P. */
    @Test
    void stops_withdrawal_endsOnALaterOwnerConfirmation() {
        OtherLead withdrew = new OtherLead(false, T1, Set.of());
        for (Completion later : List.of(new Completion(false, Set.of(U1), T3, false, null, T2),
                new Completion(true, Set.of(), T3, false, null, T2))) {
            Facts facts = facts(List.of(withdrew), List.of(later));

            assertEquals(NONE, UnitStopPolicy.stops(U1, facts), later.toString());
            assertEquals(NONE, UnitStopPolicy.stops(U2, facts), later.toString());
        }
        for (Completion notLater : List.of(new Completion(false, Set.of(U1), T3, false, null, T0),
                new Completion(false, Set.of(U1), T3, false, null, T1), new Completion(false, Set.of(U1), T3, false, null, null))) {
            Facts facts = facts(List.of(withdrew), List.of(notLater));

            assertEquals(PROPERTY, UnitStopPolicy.stops(U1, facts), notLater.toString());
            assertEquals(PROPERTY, UnitStopPolicy.stops(U2, facts), notLater.toString());
        }
    }

    @Test
    void stops_twoWithdrawals_eachNeedsItsOwnConfirmation() {
        List<OtherLead> withdrew = List.of(new OtherLead(false, T1, Set.of()), new OtherLead(false, T3, Set.of()));
        Completion between = new Completion(false, Set.of(), T4, false, null, T2);

        assertEquals(PROPERTY, UnitStopPolicy.stops(U1, facts(withdrew, List.of(between))));
        assertEquals(NONE, UnitStopPolicy.stops(U1, facts(withdrew, List.of(between,
                new Completion(true, Set.of(), T4, false, null, T4)))));
    }

    /** An owner confirmation ends only a withdrawal; a later completion does not lift a whole-asset mark. */
    @Test
    void stops_otherLeadWholeAssetMark_propertyStopNotLifted() {
        Completion marked = completion(true, Set.of(), T0, true, T1);

        assertEquals(PROPERTY, UnitStopPolicy.stops(U1, facts(List.of(), List.of(marked))));
        assertEquals(PROPERTY, UnitStopPolicy.stops(U2, facts(List.of(), List.of(marked))));
        Facts withLater = facts(List.of(), List.of(marked, new Completion(false, Set.of(U1), T2, false, null, T3)));
        assertEquals(PROPERTY, UnitStopPolicy.stops(U1, withLater));
        assertEquals(PROPERTY, UnitStopPolicy.stops(U2, withLater));
    }

    @Test
    void stops_otherLeadSuspendedUnit_unitStopOnThatUnitOnly() {
        Facts facts = facts(List.of(new OtherLead(false, null, Set.of(U1))), List.of());

        assertEquals(UNIT, UnitStopPolicy.stops(U1, facts));
        assertEquals(NONE, UnitStopPolicy.stops(U2, facts));
    }

    @Test
    void stops_otherLeadMarkedCompletion_unitStopOnItsUnitsOnly() {
        Facts facts = facts(List.of(), List.of(completion(true, Set.of(U1), T0, false, T1)));

        assertEquals(UNIT, UnitStopPolicy.stops(U1, facts));
        assertEquals(NONE, UnitStopPolicy.stops(U2, facts));
    }

    /** This lead's own marks keep their as-built place (lead level ENDED, gate RECONCILIATION_REQUIRED). */
    @Test
    void stops_ownMarkedCompletion_none() {
        assertEquals(NONE, UnitStopPolicy.stops(U1, facts(List.of(), List.of(completion(false, Set.of(U1), T0, false, T1)))));
        assertEquals(NONE, UnitStopPolicy.stops(U1, facts(List.of(), List.of(completion(false, Set.of(U1), T0, true, T1)))));
    }

    @Test
    void stops_laterCompletion_liftsForGood() {
        Completion marked = completion(true, Set.of(U1), T0, false, T1);

        assertEquals(NONE, UnitStopPolicy.stops(U1, facts(List.of(), List.of(marked,
                completion(false, Set.of(U1), T2, false, null)))));
        assertEquals(NONE, UnitStopPolicy.stops(U1, facts(List.of(), List.of(marked,
                completion(true, Set.of(U1), T2, false, null)))));
    }

    @Test
    void stops_completionAtOrBeforeTheMarkOrElsewhere_doesNotLift() {
        Completion marked = completion(true, Set.of(U1), T0, false, T1);
        for (Completion other : List.of(completion(false, Set.of(U1), T1, false, null),
                completion(false, Set.of(U1), T0, false, null), completion(false, Set.of(U2), T2, false, null))) {

            assertEquals(UNIT, UnitStopPolicy.stops(U1, facts(List.of(), List.of(marked, other))), other.toString());
        }
    }

    @Test
    void stops_twoMarks_onlyTheEarlierLifted() {
        List<Completion> marks = new ArrayList<>(List.of(completion(true, Set.of(U1), T0, false, T1),
                completion(true, Set.of(U1), T2, false, T3)));

        assertEquals(UNIT, UnitStopPolicy.stops(U1, facts(List.of(), marks)));
        marks.add(completion(false, Set.of(U1), T4, false, null));
        assertEquals(NONE, UnitStopPolicy.stops(U1, facts(List.of(), marks)));
    }

    @Test
    void stops_bothGroups_propertyThenUnit() {
        Facts facts = new Facts(false, true, false, false, List.of(new OtherLead(false, null, Set.of(U1))), List.of());

        assertEquals(List.of(Stop.PROPERTY_STOP, Stop.UNIT_STOP), UnitStopPolicy.stops(U1, facts));
    }

    @Test
    void ownerConfirmedAt_checkedInForceProofOfTheOwnerOnly() {
        assertEquals(T1, UnitStopPolicy.ownerConfirmedAt(proof(OwnerCheckState.DONE, null, null), OWNER));
        assertNull(UnitStopPolicy.ownerConfirmedAt(proof(OwnerCheckState.DONE, null, null), STRANGER));
        assertNull(UnitStopPolicy.ownerConfirmedAt(proof(OwnerCheckState.DONE, null, null), null));
        assertNull(UnitStopPolicy.ownerConfirmedAt(proof(OwnerCheckState.REQUIRED, null, null), OWNER));
        assertNull(UnitStopPolicy.ownerConfirmedAt(proof(OwnerCheckState.REFUSED, null, null), OWNER));
        assertNull(UnitStopPolicy.ownerConfirmedAt(proof(OwnerCheckState.DONE, T2, null), OWNER));
        assertNull(UnitStopPolicy.ownerConfirmedAt(proof(OwnerCheckState.DONE, null, T2), OWNER));
        assertNull(UnitStopPolicy.ownerConfirmedAt(null, OWNER));
    }

    private static Facts facts(List<OtherLead> otherLeads, List<Completion> completions) {
        return new Facts(true, true, false, false, otherLeads, completions);
    }

    /** A completion with no owner confirmation of its own. */
    private static Completion completion(boolean ofOtherLead, Set<String> unitIds, Instant completedAt, boolean wholeAssetMark,
                                         Instant markedAtOrNull) {
        return new Completion(ofOtherLead, unitIds, completedAt, wholeAssetMark, markedAtOrNull, null);
    }

    /** A proof of OWNER confirmed at T1. */
    private static OwnerProof proof(OwnerCheckState check, Instant withdrawnAt, Instant voidedAt) {
        return OwnerProof.load("01PROOF", "01INTAKE", "01REQUEST", new ProofBinding("01LEAD", 1, "01CHECKLIST", "scope"),
                OWNER, "owner-link-2026-11", T1, withdrawnAt, voidedAt, check, 0, null, null, 0, null, null, null);
    }
}
