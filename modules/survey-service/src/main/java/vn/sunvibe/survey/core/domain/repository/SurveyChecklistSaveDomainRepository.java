package vn.sunvibe.survey.core.domain.repository;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSave;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;

import java.util.List;

/** The insert-only save log of checklists (survey-checklist spec 5.2, I-6). */
public interface SurveyChecklistSaveDomainRepository {

    void save(ChecklistSave save);

    /** The saves of a checklist, oldest first. */
    List<ChecklistSave> findByChecklistId(SurveyChecklistId checklistId);
}
