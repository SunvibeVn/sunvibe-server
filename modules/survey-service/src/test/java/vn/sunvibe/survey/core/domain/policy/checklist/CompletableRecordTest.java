package vn.sunvibe.survey.core.domain.policy.checklist;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistStatus;
import vn.sunvibe.survey.core.domain.model.checklist.FailedKind;
import vn.sunvibe.survey.core.domain.model.checklist.NeedsChangesKind;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A completable record (survey-checklist submission spec 4.8, S-14): a current visit whose latest revision is none,
 * DRAFT, SUBMITTED or NEEDS_CHANGES DOCUMENTS_ONLY; cancel keeps SURVEY_SCHEDULED and resume returns to it only then.
 */
class CompletableRecordTest {

    private static boolean completable(ChecklistStatus status, NeedsChangesKind needsChanges, FailedKind failed) {
        return CompletableRecord.completable(true, Optional.of(new RevisionMark(status, needsChanges, failed)));
    }

    @Test
    void completable_noCurrentVisit_false() {
        assertFalse(CompletableRecord.completable(false, Optional.empty()));
    }

    @Test
    void completable_visitNoRevision_true() {
        assertTrue(CompletableRecord.completable(true, Optional.empty()));
    }

    @Test
    void completable_draft_true() {
        assertTrue(completable(ChecklistStatus.DRAFT, null, null));
    }

    @Test
    void completable_submitted_true() {
        assertTrue(completable(ChecklistStatus.SUBMITTED, null, null));
    }

    @Test
    void completable_needsChangesDocumentsOnly_true() {
        assertTrue(completable(ChecklistStatus.NEEDS_CHANGES, NeedsChangesKind.DOCUMENTS_ONLY, null));
    }

    @Test
    void completable_passed_false() {
        assertFalse(completable(ChecklistStatus.PASSED, null, null));
    }

    @Test
    void completable_needsChangesResurveyRequired_false() {
        assertFalse(completable(ChecklistStatus.NEEDS_CHANGES, NeedsChangesKind.RESURVEY_REQUIRED, null));
    }

    @Test
    void completable_failedTemporary_false() {
        assertFalse(completable(ChecklistStatus.FAILED, null, FailedKind.TEMPORARY));
    }

    @Test
    void completable_failedOutOfScope_false() {
        assertFalse(completable(ChecklistStatus.FAILED, null, FailedKind.OUT_OF_SCOPE));
    }

    @Test
    void mark_resurveyRequired_needsResurvey() {
        assertTrue(new RevisionMark(ChecklistStatus.NEEDS_CHANGES, NeedsChangesKind.RESURVEY_REQUIRED, null)
                .needsResurvey());
    }

    @Test
    void mark_failedTemporary_needsResurvey() {
        assertTrue(new RevisionMark(ChecklistStatus.FAILED, null, FailedKind.TEMPORARY).needsResurvey());
    }

    @Test
    void mark_documentsOnly_needsNoResurvey() {
        assertFalse(new RevisionMark(ChecklistStatus.NEEDS_CHANGES, NeedsChangesKind.DOCUMENTS_ONLY, null)
                .needsResurvey());
    }

    @Test
    void mark_failedNotAccepted_needsNoResurvey() {
        assertFalse(new RevisionMark(ChecklistStatus.FAILED, null, FailedKind.NOT_ACCEPTED).needsResurvey());
    }
}
