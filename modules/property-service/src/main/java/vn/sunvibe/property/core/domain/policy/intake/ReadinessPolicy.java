package vn.sunvibe.property.core.domain.policy.intake;

import java.util.ArrayList;
import java.util.List;

/**
 * The state of a completed result, computed on every read (property-create intake spec 4.11, property-create BR-19). ENDED wins over
 * SUSPENDED. Completion moves no status and writes no VERIFIED, so nothing here reads as a verified owner (property-create BR-16).
 */
public final class ReadinessPolicy {

    /** US-7 AC-3: what E05 and E06 still need; fixed. */
    public static final List<String> MISSING_FOR_BROKERAGE = List.of("AGREEMENT", "ADVERTISING_PERMISSION",
            "CONFIRMED_TERMS", "PHOTO_RIGHTS");

    private ReadinessPolicy() {
    }

    /**
     * @param requalified   the source of the result carries a mark
     * @param intentChanged the lead's cycle moved on from the result's
     * @param evidenceGone  a frozen photo of the source lost its content
     * @param evidenceHeld  a frozen photo of the source is held
     * @param proofInForce  the proof in force stands: not withdrawn, and made after the account's last loss of sign-in
     * @param ownerEligible the account may own now
     * @param restricted    an unreleased whole-lead suspension holds
     */
    public record Facts(String leadStatus, boolean requalified, boolean intentChanged, boolean evidenceGone,
                        boolean evidenceHeld, boolean proofInForce, boolean ownerEligible, boolean restricted) {
    }

    /** The state with every reason of that state. */
    public record Result(ReadinessState state, List<ReadinessReason> reasons) {
    }

    public static Result of(Facts facts) {
        List<ReadinessReason> ended = new ArrayList<>();
        switch (facts.leadStatus()) {
            case "DUPLICATE" -> ended.add(ReadinessReason.DUPLICATE);
            case "REJECTED" -> ended.add(ReadinessReason.REJECTED);
            case "WITHDRAWN" -> ended.add(ReadinessReason.WITHDRAWN);
            default -> {
            }
        }
        if (facts.requalified()) {
            ended.add(ReadinessReason.REQUALIFIED);
        }
        if (facts.intentChanged()) {
            ended.add(ReadinessReason.INTENT_CHANGED);
        }
        if (facts.evidenceGone()) {
            ended.add(ReadinessReason.EVIDENCE_GONE);
        }
        if (!ended.isEmpty()) {
            return new Result(ReadinessState.ENDED, List.copyOf(ended));
        }
        List<ReadinessReason> suspended = new ArrayList<>();
        if ("ON_HOLD".equals(facts.leadStatus())) {
            suspended.add(ReadinessReason.LEAD_ON_HOLD);
        }
        if (facts.evidenceHeld()) {
            suspended.add(ReadinessReason.EVIDENCE_HELD);
        }
        if (!facts.proofInForce()) {
            suspended.add(ReadinessReason.OWNER_NOT_CONFIRMED);
        }
        if (!facts.ownerEligible()) {
            suspended.add(ReadinessReason.OWNER_NOT_ELIGIBLE);
        }
        if (facts.restricted()) {
            suspended.add(ReadinessReason.RESTRICTED);
        }
        return suspended.isEmpty() ? new Result(ReadinessState.USABLE, List.of())
                : new Result(ReadinessState.SUSPENDED, List.copyOf(suspended));
    }
}
