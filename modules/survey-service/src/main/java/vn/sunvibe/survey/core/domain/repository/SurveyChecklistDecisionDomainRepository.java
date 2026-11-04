package vn.sunvibe.survey.core.domain.repository;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistDecision;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Decision rows with their photo marks (survey-checklist submission spec 4.5, I-2, I-3): insert-only. */
public interface SurveyChecklistDecisionDomainRepository {

    /** Insert the decision and its marks and flush. */
    void save(ChecklistDecision decision);

    Optional<ChecklistDecision> findByChecklistId(SurveyChecklistId checklistId);

    /** The decisions of these revisions, with their marks, in two queries; undecided revisions are absent. */
    Map<SurveyChecklistId, ChecklistDecision> findByChecklistIds(Collection<SurveyChecklistId> checklistIds);
}
