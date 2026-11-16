package vn.sunvibe.survey.core.application.query.common;

import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;

/**
 * survey-checklist SV-310: a photo that a decision on an earlier revision of the visit did not accept, with that
 * revision, the photo's frozen placement there and the reviewer's reason. The reason is private: never logged (survey
 * BR-4, BR-14).
 */
public record RefusedPhotoView(String mediaId, String checklistId, int checklistNo, EvidenceCategory category,
                               String spaceIdOrNull, String spaceCodeOrNull, String reason) {

    @Override
    public String toString() {
        return "RefusedPhotoView[" + mediaId + "]";
    }
}
