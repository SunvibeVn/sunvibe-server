package vn.sunvibe.survey.core.application.shared.external.media;

import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;

/**
 * Whether a submission references a photo (survey-media spec 4.7, BR-8): such a photo cannot be removed. No submission
 * exists in this slice, so the adapter answers false for every photo.
 */
public interface EvidenceReferencePort {

    boolean isReferenced(SurveyMediaId mediaId);
}
