package vn.sunvibe.survey;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import vn.sunvibe.survey.core.application.shared.external.media.EvidenceReferencePort;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;

/**
 * The media tests' evidence references (survey-media spec 4.7, BR-8): no submission exists in this slice, so the
 * production port is empty; a test marks a photo referenced here to see the 409 MEDIA_REFERENCED.
 */
public final class MediaTestReferences implements EvidenceReferencePort {

	private final Set<String> referenced = ConcurrentHashMap.newKeySet();

	public void reference(String mediaId) {
		referenced.add(mediaId);
	}

	public void reset() {
		referenced.clear();
	}

	@Override
	public boolean isReferenced(SurveyMediaId mediaId) {
		return referenced.contains(mediaId.getValue());
	}
}
