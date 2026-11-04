package vn.sunvibe.survey.core.domain.repository;

import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Checklist rows (survey-checklist spec 5.2): written at start, at each takeover, at submit and at the decision. */
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
     *
     * @throws vn.sunvibe.survey.core.domain.exception.ChecklistSubmittedConflictException a SUBMITTED revision of the
     *                                                                                     lead and cycle exists
     */
    void update(SurveyChecklist checklist);

    Optional<SurveyChecklist> findById(SurveyChecklistId id);

    /**
     * The checklist with a PostgreSQL {@code FOR SHARE} lock, as the first load of the row in its persistence context
     * (survey-media spec 5.4, plan PD-10): the media commands' read; the takeover keeps the plain read.
     */
    Optional<SurveyChecklist> findByIdForShare(SurveyChecklistId id);

    /**
     * The checklist with a PostgreSQL {@code FOR NO KEY UPDATE} lock, as the first load of the row in its persistence
     * context (submission spec 5.4, plan PD-8): a submit or a decision writes the row, and a plain read followed by a
     * lock query would keep the stale instance.
     */
    Optional<SurveyChecklist> findByIdForUpdate(SurveyChecklistId id);

    /** The lead of a checklist by a scalar query, without loading it (spec 5.5); empty when unknown. */
    Optional<String> findLeadIdById(SurveyChecklistId id);

    /** Every checklist of the lead, by number. */
    List<SurveyChecklist> findByLeadId(String leadId);

    /** Every revision on these visits, by number, in one query (survey-checklist submission plan PD-2). */
    List<SurveyChecklist> findByVisitAppointmentIds(Collection<SurveyAppointmentId> visitIds);

    /**
     * One page of the SUBMITTED revisions of these lead cycles, oldest submission first, with the size of the whole set
     * (survey-checklist submission plan PD-4). {@code leadIds} and {@code intentCycles} pair up by position; the page is
     * zero-based.
     */
    PageResult<SurveyChecklist> findSubmittedOf(List<String> leadIds, List<Integer> intentCycles, int page, int size);

    /** The lead's highest checklist number plus one (1 for the first). */
    int nextChecklistNo(String leadId);
}
