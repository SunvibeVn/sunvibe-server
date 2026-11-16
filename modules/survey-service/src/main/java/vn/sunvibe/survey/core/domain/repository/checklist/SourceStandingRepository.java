package vn.sunvibe.survey.core.domain.repository.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistRequalification;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceEntry;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The rows the source standing of a set of leads is read from (property-create intake spec 5.1), each one statement for
 * the whole set, beside the single reads of {@link SurveyChecklistDomainRepository},
 * {@link SurveyChecklistEvidenceDomainRepository} and {@link SurveyChecklistRequalificationDomainRepository}. An empty
 * input answers empty without a statement.
 */
public interface SourceStandingRepository {

    /** The checklists of the leads, ordered by lead, then checklist number. */
    List<SurveyChecklist> findChecklistsOfLeads(Collection<String> leadIds);

    /** The frozen evidence of each checklist that has some, each list ordered by media id. */
    Map<SurveyChecklistId, List<EvidenceEntry>> findEvidenceOf(Collection<SurveyChecklistId> checklistIds);

    /** The requalification row of each checklist that has one. */
    Map<SurveyChecklistId, ChecklistRequalification> findRequalificationsOf(Collection<SurveyChecklistId> checklistIds);
}
