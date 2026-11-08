package vn.sunvibe.survey.core.domain.repository.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistPerson;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistTakeoverRow;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Insert-only takeover rows with their checkpoints (survey-checklist spec 5.2, I-6). */
public interface SurveyChecklistTakeoverDomainRepository {

    void save(ChecklistTakeoverRow takeover);

    /** Every takeover or intake target of these revisions, in one query (survey-checklist submission plan PD-2). */
    List<ChecklistPerson> findTargets(Collection<SurveyChecklistId> checklistIds);

    /** The takeovers of a checklist by number, without checkpoints. */
    List<ChecklistTakeoverRow> findByChecklistId(SurveyChecklistId checklistId);

    /** One takeover with its checkpoint. */
    Optional<ChecklistTakeoverRow> find(SurveyChecklistId checklistId, int takeoverNo);

    /** The checklist's highest takeover number plus one (1 for the first). */
    int findNextTakeoverNo(SurveyChecklistId checklistId);
}
