package vn.sunvibe.survey.core.domain.policy.checklist;

import java.util.HashSet;
import java.util.Set;

/**
 * The users who may not decide a revision (survey-checklist D3-9, D3-14, survey-checklist submission spec 4.2): everyone who performed,
 * recorded, held, submitted, edited or added or removed photos in it or in an earlier revision of its visit, and the
 * lead's current assignee. An owner-statement receiver is not excluded.
 */
public final class ReviewerExclusion {

    private ReviewerExclusion() {
    }

    /**
     * @param currentAssigneeIdOrNull the lead's assignee now, null for a lead without one
     */
    public static Set<String> excluded(ReviewerExclusionFacts facts, String currentAssigneeIdOrNull) {
        Set<String> excluded = new HashSet<>();
        excluded.add(facts.performerUserId());
        excluded.add(facts.recorderUserId());
        excluded.addAll(facts.holders());
        excluded.addAll(facts.submitters());
        excluded.addAll(facts.savers());
        excluded.addAll(facts.uploaders());
        excluded.addAll(facts.removers());
        excluded.addAll(facts.deselectors());
        if (currentAssigneeIdOrNull != null) {
            excluded.add(currentAssigneeIdOrNull);
        }
        return Set.copyOf(excluded);
    }
}
