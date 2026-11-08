package vn.sunvibe.property.core.domain.policy.intake;

import vn.sunvibe.property.core.domain.model.lead.LeadStatus;
import vn.sunvibe.property.core.domain.model.property.enums.AssetType;
import vn.sunvibe.property.core.domain.model.property.enums.RentalLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * The gate of an intake (property-create intake spec 4.4, property-create BR-1, property-create BR-2, D5-4, D5-13). Computed on every read and again inside
 * completion, never stored. Nobody can skip it, ADMIN included.
 */
public final class IntakeGate {

    private static final String LONG_TERM = "LONG_TERM_RENT";

    private IntakeGate() {
    }

    /**
     * @param hasPassed              the cycle has a current unmarked PASSED
     * @param passedUsable           that revision can still be used
     * @param assetTypeOrNull        its asset type
     * @param scopeOrNull            its rental scope
     * @param reconciliationRequired an earlier result stands on a wrong asset or owner, or was ruled out (D5-12, property-create BR-20)
     * @param restricted             an unreleased whole-lead suspension holds
     */
    public record Facts(String intent, LeadStatus leadStatus, boolean hasPassed, boolean passedUsable,
                        AssetType assetTypeOrNull, RentalLayout scopeOrNull, boolean reconciliationRequired,
                        boolean restricted) {
    }

    public static List<GateReason> reasons(Facts facts) {
        List<GateReason> reasons = new ArrayList<>();
        if (!LONG_TERM.equals(facts.intent())) {
            reasons.add(GateReason.NOT_LONG_TERM);
        }
        if (facts.leadStatus() != LeadStatus.QUALIFIED) {
            reasons.add(GateReason.NOT_QUALIFIED);
        }
        if (!facts.hasPassed()) {
            reasons.add(GateReason.NO_PASSED);
        } else {
            if (!facts.passedUsable()) {
                reasons.add(GateReason.PASSED_NOT_USABLE);
            }
            if (!AssetLayoutRule.allows(facts.assetTypeOrNull(), facts.scopeOrNull())) {
                reasons.add(GateReason.TYPE_SCOPE_MISMATCH);
            }
        }
        if (facts.reconciliationRequired()) {
            reasons.add(GateReason.RECONCILIATION_REQUIRED);
        }
        if (facts.restricted()) {
            reasons.add(GateReason.RESTRICTED);
        }
        return List.copyOf(reasons);
    }
}
