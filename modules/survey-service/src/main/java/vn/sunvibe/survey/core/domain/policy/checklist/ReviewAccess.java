package vn.sunvibe.survey.core.domain.policy.checklist;

import java.util.List;

/**
 * What a read says about the review of a revision (survey-checklist submission spec 4.2, 7.2): whether the viewer can
 * decide it, why not, and the {@code errors[].field} of every PASSED rule that fails now.
 */
public record ReviewAccess(boolean canReview, ReviewReason reasonOrNull, List<String> decisionBlockedBy) {

    public ReviewAccess {
        decisionBlockedBy = List.copyOf(decisionBlockedBy);
    }
}
