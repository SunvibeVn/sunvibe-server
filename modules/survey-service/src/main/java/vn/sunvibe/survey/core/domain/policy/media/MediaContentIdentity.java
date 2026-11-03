package vn.sunvibe.survey.core.domain.policy.media;

import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;

import java.util.Collection;

/**
 * The content identity of a photo (survey-media spec A-16, BR-2, BR-20): the SHA-256 of its source bytes, checked at
 * create and again at the commit under the media-set lock, the first matching rule deciding: (a) the same hash on a
 * selected photo of this checklist, then (b) the same hash on a committed photo of a checklist of another visit of the
 * same lead. Removed, REJECTED, FAILED, CANCELLED and UPLOAD_EXPIRED photos of this checklist do not constrain. The
 * caller passes only photos of the same lead: hashes are never compared across leads.
 */
public final class MediaContentIdentity {

    private MediaContentIdentity() {
    }

    /**
     * @param selfIdOrNull         the photo being committed (it is selected with this hash itself), or null at create
     * @param sameChecklist        photos of this checklist
     * @param otherVisitsOfTheLead photos of checklists of the lead's other visits
     */
    public static IdentityVerdict check(String sha256, String selfIdOrNull, Collection<SurveyMedia> sameChecklist,
                                        Collection<SurveyMedia> otherVisitsOfTheLead) {
        for (SurveyMedia media : sameChecklist) {
            if (media.isSelected() && media.getSourceSha256().equals(sha256)
                    && !media.getId().getValue().equals(selfIdOrNull)) {
                return new IdentityVerdict(IdentityVerdict.Kind.DUPLICATE_CONTENT, media.getId().getValue());
            }
        }
        for (SurveyMedia media : otherVisitsOfTheLead) {
            if (media.getContentReceivedAt() != null && media.getSourceSha256().equals(sha256)) {
                return new IdentityVerdict(IdentityVerdict.Kind.FROM_OTHER_VISIT, media.getId().getValue());
            }
        }
        return IdentityVerdict.FREE;
    }
}
