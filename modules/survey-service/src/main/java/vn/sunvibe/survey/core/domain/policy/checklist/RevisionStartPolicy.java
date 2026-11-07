package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.NeedsChangesKind;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.lead.LeadStatus;

import java.util.Optional;

/**
 * Whether a new revision can be started now (survey-checklist submission spec 4.2, 4.6, A-7), read in the order of
 * {@link ChecklistStartPolicy}: after a return for documents of the current visit's latest revision, on a
 * SURVEY_SCHEDULED lead, by its eligible assignee; whoever did not hold the previous revision declares an intake.
 */
public final class RevisionStartPolicy {

    private RevisionStartPolicy() {
    }

    public static RevisionStart of(boolean hasCurrentVisit, Optional<SurveyChecklist> latestOfCurrentVisit,
                                   Optional<RevisionMark> latestMark, LeadStatus leadStatus, String assigneeUserId,
                                   boolean assigneeEligible, String viewerUserId) {
        if (!hasCurrentVisit) {
            return RevisionStart.blocked(RevisionStartBlock.NO_CURRENT_VISIT);
        }
        if (leadStatus != LeadStatus.SURVEY_SCHEDULED) {
            return RevisionStart.blocked(RevisionStartBlock.LEAD_STATUS);
        }
        if (latestMark.map(RevisionMark::needsChangesKindOrNull).orElse(null) != NeedsChangesKind.DOCUMENTS_ONLY) {
            return RevisionStart.blocked(RevisionStartBlock.NOT_RETURNED_FOR_DOCUMENTS);
        }
        if (!viewerUserId.equals(assigneeUserId)) {
            return RevisionStart.blocked(RevisionStartBlock.NOT_ASSIGNEE);
        }
        if (!assigneeEligible) {
            return RevisionStart.blocked(RevisionStartBlock.ASSIGNEE_INELIGIBLE);
        }
        SurveyChecklist latest = latestOfCurrentVisit.orElseThrow();
        return new RevisionStart(true, latest.getId().getValue(), !viewerUserId.equals(latest.getHolderUserId()), null);
    }
}
