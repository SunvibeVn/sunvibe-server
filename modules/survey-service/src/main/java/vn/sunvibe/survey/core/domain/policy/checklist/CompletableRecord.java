package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.NeedsChangesKind;

import java.util.Optional;

/**
 * A record that can still be finished on the current visit (survey-checklist submission spec 4.8): the latest revision
 * of the current visit is none, DRAFT, SUBMITTED or NEEDS_CHANGES DOCUMENTS_ONLY. A cancel keeps SURVEY_SCHEDULED and a
 * resume returns to it only then, so a return for a re-survey never reaches PASSED on the old visit (US-3 AC-2a).
 */
public final class CompletableRecord {

    private CompletableRecord() {
    }

    public static boolean completable(boolean hasCurrentVisit, Optional<RevisionMark> latestRevisionOfCurrentVisit) {
        if (!hasCurrentVisit) {
            return false;
        }
        if (latestRevisionOfCurrentVisit.isEmpty()) {
            return true;
        }
        RevisionMark mark = latestRevisionOfCurrentVisit.get();
        return switch (mark.status()) {
            case DRAFT, SUBMITTED -> true;
            case NEEDS_CHANGES -> mark.needsChangesKindOrNull() == NeedsChangesKind.DOCUMENTS_ONLY;
            case PASSED, FAILED -> false;
        };
    }
}
