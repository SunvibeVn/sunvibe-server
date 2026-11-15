package vn.sunvibe.brokerage.core.application.query;

import vn.sunvibe.brokerage.core.domain.model.eligibility.ReasonGroup;

import java.util.List;

/**
 * The agreement eligibility of the units of a lead's intake basis (brokerage-agreement US-1 AC-1, AC-2). It names no
 * lead, owner or ownership row (brokerage-agreement BR-17).
 *
 * @param basisState           the readiness state of the lead's latest COMPLETED intake; NONE without one
 * @param viewerIsAssignedSale the viewer is the lead's current assignee and holds SALE or SALE_LEADER
 * @param units                one per unit the basis maps, in the readiness order; empty with NONE
 */
public record EligibilityView(String basisState, boolean viewerIsAssignedSale, List<UnitEligibility> units) {

    public EligibilityView {
        units = List.copyOf(units);
    }

    /**
     * One unit of the basis.
     *
     * @param unitCodeOrNull      its code; null when the unit row is missing
     * @param reasonGroups        the groups that stop an agreement, in the order of {@link ReasonGroup}; empty when
     *                            eligible
     * @param unfinishedAgreement the unit has an unfinished long-term rental agreement (brokerage-agreement BR-1)
     * @param agreementIdOrNull   that agreement's id when it rests on the lead read; null otherwise
     */
    public record UnitEligibility(String unitId, String unitCodeOrNull, List<ReasonGroup> reasonGroups,
                                  boolean unfinishedAgreement, String agreementIdOrNull) {

        public UnitEligibility {
            reasonGroups = List.copyOf(reasonGroups);
        }

        public boolean eligible() {
            return reasonGroups.isEmpty();
        }
    }
}
