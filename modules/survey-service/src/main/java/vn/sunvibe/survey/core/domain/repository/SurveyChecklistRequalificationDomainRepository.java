package vn.sunvibe.survey.core.domain.repository;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistRequalification;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;

import java.util.Optional;

/** The reason of each requalification mark (property-create requalification spec 5.2, I-2): insert-only. */
public interface SurveyChecklistRequalificationDomainRepository {

    /** Insert the row and flush. */
    void save(ChecklistRequalification requalification);

    Optional<ChecklistRequalification> findByChecklistId(SurveyChecklistId checklistId);
}
