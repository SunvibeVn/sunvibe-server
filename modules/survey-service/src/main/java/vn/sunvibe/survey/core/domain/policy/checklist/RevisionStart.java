package vn.sunvibe.survey.core.domain.policy.checklist;

/**
 * Whether the viewer can start a new revision (survey-checklist submission spec 7.2 {@code revisionStart}): the revision
 * it follows and whether an intake declaration is needed (A-7), or the first block that applies.
 */
public record RevisionStart(boolean allowed, String checklistIdOrNull, boolean intakeRequired,
                            RevisionStartBlock blockedByOrNull) {

    public static RevisionStart blocked(RevisionStartBlock block) {
        return new RevisionStart(false, null, false, block);
    }
}
