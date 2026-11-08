package vn.sunvibe.survey.core.domain.policy.checklist;

import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * The first reason the viewer cannot decide a revision (survey-checklist submission spec 4.2): the same order as the
 * decision (survey-checklist submission spec 5.5) on the state blockers it computes, so a read and a command cannot disagree. The exclusion is asked
 * last: it costs reads a viewer refused earlier never needs.
 */
public final class ReviewRights {

    private ReviewRights() {
    }

    /**
     * @param reviewerRole  the viewer has a reviewer role and an account that can sign in
     * @param stateBlockers {@link DecisionPolicy#stateBlockers} for the revision now
     * @param excluded      whether the viewer is in the excluded set
     */
    public static Optional<ReviewReason> refusal(boolean reviewerRole, List<String> stateBlockers,
                                                 BooleanSupplier excluded) {
        if (!reviewerRole) {
            return Optional.of(ReviewReason.NOT_REVIEWER_ROLE);
        }
        if (!stateBlockers.isEmpty()) {
            return Optional.of(ofBlocker(stateBlockers.get(0)));
        }
        return excluded.getAsBoolean() ? Optional.of(ReviewReason.NOT_INDEPENDENT) : Optional.empty();
    }

    /**
     * A SUBMITTED revision off the current visit cannot arise (a booking is refused while one exists, survey-checklist submission spec 4.8), and the
     * contract has no reason of its own for it: it counts as a record that moved on.
     */
    private static ReviewReason ofBlocker(String field) {
        return switch (field) {
            case "status" -> ReviewReason.NOT_SUBMITTED;
            case "lead.status" -> ReviewReason.LEAD_STATUS;
            case "lead.intentCycle", "visit" -> ReviewReason.INTENT_CHANGED;
            case "media.serving" -> ReviewReason.SERVING_DISABLED;
            default -> throw new IllegalArgumentException("Unknown state blocker " + field);
        };
    }
}
