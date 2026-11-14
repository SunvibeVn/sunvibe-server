package vn.sunvibe.property.core.domain.policy.intake;

import vn.sunvibe.property.core.domain.model.intake.OwnerCheckState;
import vn.sunvibe.property.core.domain.model.intake.OwnerProof;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The stops of brokerage-agreement BR-3 that reach one unit of a lead's result from another lead with a result on the same
 * property, or from the property itself (brokerage-agreement BR-18, SV-215). Each is read from stored facts and ends only
 * as SV-215 spec section 3.1 says: another lead's hold or closure neither starts nor ends one.
 * <ul>
 * <li>The whole asset stops (PROPERTY_STOP) while the property's owner is not eligible, the OWNER holders valid today are
 * not exactly the property's owner, a SAME_ASSET decision showed another owner (E-15), a manager ruled another lead's
 * earlier result out, another lead holds an unreleased whole-lead suspension, another lead's result carries a
 * wrong-asset or wrong-owner mark, or the owner withdrew the proof of another lead's latest result and has not confirmed
 * again since: no result of the property names a checked proof of the owner confirmed after the withdrawal.</li>
 * <li>One unit stops (UNIT_STOP) while another lead's unreleased suspension names it, or another lead's result that maps
 * it carries a mark of another kind and no result of any lead mapped the unit after that mark: such a completion lifts
 * the mark for good.</li>
 * </ul>
 * This lead's own causes keep their place in the lead-level readiness and the gate, so a result of this lead never stops
 * a unit here; its owner confirmation still ends another lead's withdrawal.
 */
public final class UnitStopPolicy {

    private UnitStopPolicy() {
    }

    /** The two groups of brokerage-agreement BR-3; neither names the lead it comes from (BR-18). */
    public enum Stop {
        /** A whole-asset stop, BR-3 (a) to (e). */
        PROPERTY_STOP,
        /** A stop of this unit from another lead, BR-3 (f) and (g). */
        UNIT_STOP
    }

    /**
     * What the stops of one property are read from.
     *
     * @param ownerEligible   the property's owner account is eligible now (BR-3 (d))
     * @param ownerHoldsAlone the OWNER rows valid today name the property's owner and nobody else (BR-3 (e))
     * @param ownerDisputed   a SAME_ASSET decision on the property showed another owner (BR-3 (c), E-15)
     * @param resultRuledOut  a manager ruled that another lead's earlier result on the property is another asset (BR-3 (a))
     * @param otherLeads      each other lead with a COMPLETED result on the property
     * @param completions     each COMPLETED result on the property, of any lead
     */
    public record Facts(boolean ownerEligible, boolean ownerHoldsAlone, boolean ownerDisputed, boolean resultRuledOut,
                        List<OtherLead> otherLeads, List<Completion> completions) {

        public Facts {
            otherLeads = List.copyOf(otherLeads);
            completions = List.copyOf(completions);
        }
    }

    /**
     * Another lead with a COMPLETED result on the property.
     *
     * @param wholeLeadRestricted    an unreleased suspension of the whole lead (BR-3 (b))
     * @param proofWithdrawnAtOrNull when the proof its latest COMPLETED row on the property names was withdrawn; null when
     *                               it was not (BR-3 (d))
     * @param suspendedUnitIds       the units its unreleased suspensions name (BR-3 (f))
     */
    public record OtherLead(boolean wholeLeadRestricted, Instant proofWithdrawnAtOrNull, Set<String> suspendedUnitIds) {

        public OtherLead {
            suspendedUnitIds = Set.copyOf(suspendedUnitIds);
        }
    }

    /**
     * One COMPLETED result on the property.
     *
     * @param ofOtherLead            the result belongs to another lead than the one read
     * @param unitIds                the units of the lead read that it maps, possibly none
     * @param wholeAssetMark         its source carries a wrong-asset or wrong-owner mark (BR-3 (a))
     * @param markedAtOrNull         when its source was marked; null when it was not
     * @param ownerConfirmedAtOrNull {@link #ownerConfirmedAt} of the proof it names
     */
    public record Completion(boolean ofOtherLead, Set<String> unitIds, Instant completedAt, boolean wholeAssetMark,
                             Instant markedAtOrNull, Instant ownerConfirmedAtOrNull) {

        public Completion {
            unitIds = Set.copyOf(unitIds);
        }
    }

    /** The stops of this unit, the whole-asset group first; each at most once. */
    public static List<Stop> stops(String unitId, Facts facts) {
        List<Stop> stops = new ArrayList<>(2);
        if (propertyStopped(facts)) {
            stops.add(Stop.PROPERTY_STOP);
        }
        if (unitStopped(unitId, facts)) {
            stops.add(Stop.UNIT_STOP);
        }
        return List.copyOf(stops);
    }

    /**
     * When the property's owner confirmed the link again through this proof (brokerage-agreement BR-3 (d) "mà chưa xác nhận
     * lại", SV-215): the proof's confirmation time when the proof is the property owner's own, checked by a manager and
     * neither withdrawn nor voided; null otherwise, and when the property has no owner.
     */
    public static Instant ownerConfirmedAt(OwnerProof proofOrNull, String propertyOwnerOrNull) {
        if (proofOrNull == null || propertyOwnerOrNull == null || !propertyOwnerOrNull.equals(proofOrNull.getUserId())
                || proofOrNull.getOwnerCheck() != OwnerCheckState.DONE || !proofOrNull.inForce()) {
            return null;
        }
        return proofOrNull.getConfirmedAt();
    }

    private static boolean propertyStopped(Facts facts) {
        if (!facts.ownerEligible() || !facts.ownerHoldsAlone() || facts.ownerDisputed() || facts.resultRuledOut()) {
            return true;
        }
        boolean byOtherLead = facts.otherLeads().stream().anyMatch(other -> other.wholeLeadRestricted()
                || withdrawalStands(other.proofWithdrawnAtOrNull(), facts.completions()));
        return byOtherLead || facts.completions().stream()
                .anyMatch(result -> result.ofOtherLead() && result.markedAtOrNull() != null && result.wholeAssetMark());
    }

    /** A withdrawal stands until some result of the property names an owner confirmation made strictly after it. */
    private static boolean withdrawalStands(Instant withdrawnAtOrNull, List<Completion> completions) {
        return withdrawnAtOrNull != null && completions.stream().map(Completion::ownerConfirmedAtOrNull)
                .noneMatch(confirmedAt -> confirmedAt != null && confirmedAt.isAfter(withdrawnAtOrNull));
    }

    private static boolean unitStopped(String unitId, Facts facts) {
        if (facts.otherLeads().stream().anyMatch(other -> other.suspendedUnitIds().contains(unitId))) {
            return true;
        }
        return facts.completions().stream().anyMatch(result -> result.ofOtherLead() && result.markedAtOrNull() != null
                && !result.wholeAssetMark() && result.unitIds().contains(unitId)
                && !completedAfter(unitId, result.markedAtOrNull(), facts.completions()));
    }

    /** A result of any lead that maps the unit and was completed strictly after the mark lifts it for good. */
    private static boolean completedAfter(String unitId, Instant markedAt, List<Completion> completions) {
        return completions.stream().anyMatch(result -> result.unitIds().contains(unitId)
                && result.completedAt().isAfter(markedAt));
    }
}
