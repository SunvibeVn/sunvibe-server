package vn.sunvibe.survey.core.domain.repository.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistPerson;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSave;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;

import java.util.Collection;
import java.util.List;

/** The insert-only save log of checklists (survey-checklist spec 5.2, I-6). */
public interface SurveyChecklistSaveDomainRepository {

    void save(ChecklistSave save);

    /** Everyone in the save logs of these revisions, in one query (survey-checklist submission plan PD-2). */
    List<ChecklistPerson> findActors(Collection<SurveyChecklistId> checklistIds);

    /** The saves of a checklist, oldest first. */
    List<ChecklistSave> findByChecklistId(SurveyChecklistId checklistId);
}
