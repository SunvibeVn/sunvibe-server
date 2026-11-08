package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistStatus;
import vn.sunvibe.survey.core.domain.model.checklist.FailedKind;
import vn.sunvibe.survey.core.domain.model.checklist.NeedsChangesKind;

/**
 * Where the latest revision of a visit stands (survey-checklist submission spec 4.1): its status and, for a decided
 * one, the kind of its decision.
 */
public record RevisionMark(ChecklistStatus status, NeedsChangesKind needsChangesKindOrNull,
                           FailedKind failedKindOrNull) {

    /** The reviewer asked for a new visit ("dấu cần khảo sát lại", survey-checklist submission spec 4.1). */
    public boolean needsResurvey() {
        return needsChangesKindOrNull == NeedsChangesKind.RESURVEY_REQUIRED || failedKindOrNull == FailedKind.TEMPORARY;
    }
}
