package vn.sunvibe.survey.core.domain.repository;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistTakeover;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;

import java.util.List;
import java.util.Optional;

/** Insert-only takeover rows with their checkpoints (survey-checklist spec 5.2, I-6). */
public interface SurveyChecklistTakeoverDomainRepository {

    void save(ChecklistTakeover takeover);

    /** The takeovers of a checklist by number, without checkpoints. */
    List<ChecklistTakeover> findByChecklistId(SurveyChecklistId checklistId);

    /** One takeover with its checkpoint. */
    Optional<ChecklistTakeover> find(SurveyChecklistId checklistId, int takeoverNo);

    /** The checklist's highest takeover number plus one (1 for the first). */
    int findNextTakeoverNo(SurveyChecklistId checklistId);
}
