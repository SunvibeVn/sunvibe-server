package vn.sunvibe.survey.core.application.shared.common;

import org.springframework.stereotype.Component;
import vn.sunvibe.survey.core.domain.model.media.MediaSelection;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.policy.media.RevisionSelection;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaSelectionDomainRepository;

import java.util.List;
import java.util.Map;

/**
 * The photos of a revision as the reads, the caps and the gates count them (survey-checklist submission spec 4.6): its
 * own rows and the carried photos it still selects. A first revision has no selection row, so it reads as before.
 */
@Component
public class RevisionPhotos {

    private final SurveyMediaDomainRepository media;
    private final SurveyMediaSelectionDomainRepository selections;

    public RevisionPhotos(SurveyMediaDomainRepository media, SurveyMediaSelectionDomainRepository selections) {
        this.media = media;
        this.selections = selections;
    }

    /** Every photo row of the revision, then the carried photos it selects. */
    public List<SurveyMedia> of(String checklistId) {
        return RevisionSelection.photos(media.findForChecklist(checklistId), carried(checklistId));
    }

    /** The carried photos the revision selects now. */
    public List<SurveyMedia> carried(String checklistId) {
        return media.findByIds(RevisionSelection.selectedIds(selections.findByChecklistId(checklistId)));
    }

    /** The number of the revision each selected photo came from, by photo id. */
    public Map<String, Integer> carriedFrom(String checklistId, Map<String, Integer> checklistNumbers) {
        List<MediaSelection> ofRevision = selections.findByChecklistId(checklistId);
        return RevisionSelection.carriedFromNumbers(ofRevision, checklistNumbers);
    }
}
