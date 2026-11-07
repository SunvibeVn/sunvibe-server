package vn.sunvibe.survey.core.domain.repository.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSubmission;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;

import java.util.Optional;

/** Submission rows (survey-checklist submission spec 4.3, I-2): insert-only, one per revision. */
public interface SurveyChecklistSubmissionDomainRepository {

    /** Insert the submission and flush. */
    void save(ChecklistSubmission submission);

    Optional<ChecklistSubmission> findByChecklistId(SurveyChecklistId checklistId);
}
