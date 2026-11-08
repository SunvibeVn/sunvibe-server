package vn.sunvibe.survey.core.application.shared.external.media;

import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;

/**
 * Whether a submission references a photo (survey-media spec 4.7, BR-8, survey-checklist submission spec I-5): such a photo
 * cannot be removed, and its content is never deleted.
 */
public interface EvidenceReferencePort {

    boolean isReferenced(SurveyMediaId mediaId);
}
