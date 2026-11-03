package vn.sunvibe.survey.core.domain.policy.media;

import java.time.Duration;

import vn.sunvibe.survey.core.domain.model.media.MediaStatus;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;

/** What a holder's removal of a photo needs and writes (survey-media spec 4.7, A-10, BR-12). */
public final class MediaRemovalPolicy {

    /** spec A-17: a content deletion is due 10 minutes after its request, so reads already streaming end. */
    public static final Duration CONTENT_DELETION_DELAY = Duration.ofMinutes(10);

    private MediaRemovalPolicy() {
    }

    /**
     * spec 4.7, BR-12: a correction is needed when the photo's content was committed (PROCESSING, READY) and its
     * uploader is not the caller; removing one's own photo or one without usable content is free.
     */
    public static boolean correctionRequired(SurveyMedia photo, String callerUserId) {
        return hasCommittedContent(photo) && !photo.getUploadedByUserId().equals(callerUserId);
    }

    /** spec A-10: the holder's removal of a PROCESSING or READY photo writes CHECKLIST_MEDIA_REMOVED. */
    public static boolean writesHistory(SurveyMedia photo) {
        return hasCommittedContent(photo);
    }

    private static boolean hasCommittedContent(SurveyMedia photo) {
        return photo.getStatus() == MediaStatus.PROCESSING || photo.getStatus() == MediaStatus.READY;
    }
}
