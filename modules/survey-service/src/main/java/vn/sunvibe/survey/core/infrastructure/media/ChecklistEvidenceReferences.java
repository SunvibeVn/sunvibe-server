package vn.sunvibe.survey.core.infrastructure.media;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.survey.core.application.shared.external.media.EvidenceReferencePort;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistEvidenceDomainRepository;

import java.util.List;

/** survey-media spec 4.7, BR-8, checklist submission spec I-5: a photo in any submission's frozen set is referenced. */
@Component
@RequiredArgsConstructor
public class ChecklistEvidenceReferences implements EvidenceReferencePort {

    private final SurveyChecklistEvidenceDomainRepository evidence;

    @Override
    public boolean isReferenced(SurveyMediaId mediaId) {
        return !evidence.findReferencedAmong(List.of(mediaId)).isEmpty();
    }
}
