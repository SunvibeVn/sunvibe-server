package vn.sunvibe.survey.core.domain.repository.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.EvidenceEntry;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The frozen photo set of each submission (survey-checklist submission spec 4.3, I-2, I-5): insert-only. A row here
 * keeps the photo's content for good.
 */
public interface SurveyChecklistEvidenceDomainRepository {

    /** Insert the frozen set of a submission and flush. */
    void saveAll(SurveyChecklistId checklistId, List<EvidenceEntry> entries);

    /** The frozen set of the revision, by photo id. */
    List<EvidenceEntry> findByChecklistId(SurveyChecklistId checklistId);

    /** The ids among these that any submission references. */
    Set<String> findReferencedAmong(Collection<SurveyMediaId> mediaIds);
}
