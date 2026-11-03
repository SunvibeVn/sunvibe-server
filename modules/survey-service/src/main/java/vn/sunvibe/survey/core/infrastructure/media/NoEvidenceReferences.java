package vn.sunvibe.survey.core.infrastructure.media;

import org.springframework.stereotype.Component;

import vn.sunvibe.survey.core.application.shared.external.media.EvidenceReferencePort;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;

/** survey-media spec 4.7, BR-8: no submission references a photo in this slice. */
@Component
public class NoEvidenceReferences implements EvidenceReferencePort {

    @Override
    public boolean isReferenced(SurveyMediaId mediaId) {
        return false;
    }
}
