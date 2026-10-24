package vn.sunvibe.survey.core.domain.repository;

import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;

import java.util.List;
import java.util.Optional;

/** Checklist rows (survey-checklist spec 5.2): written at start and at each takeover only (I-7). */
public interface SurveyChecklistDomainRepository {

    /**
     * Insert a new checklist and flush; the aggregate gets version 0.
     *
     * @throws vn.sunvibe.survey.core.domain.exception.ChecklistAlreadyStartedException on either unique index
     */
    void insert(SurveyChecklist checklist);

    /**
     * Update the checklist row guarded by its version and flush; the aggregate gets the new version. A row changed
     * since it was read fails with an optimistic locking failure.
     */
    void update(SurveyChecklist checklist);

    Optional<SurveyChecklist> findById(SurveyChecklistId id);

    /** The lead of a checklist by a scalar query, without loading it (spec 5.5); empty when unknown. */
    Optional<String> findLeadIdById(SurveyChecklistId id);

    /** Every checklist of the lead, by number. */
    List<SurveyChecklist> findByLeadId(String leadId);

    /** The lead's highest checklist number plus one (1 for the first). */
    int nextChecklistNo(String leadId);
}
