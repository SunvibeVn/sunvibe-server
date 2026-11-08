package vn.sunvibe.survey.core.application.shared.media;

import vn.sunvibe.survey.core.domain.model.media.IntakeHealth;
import vn.sunvibe.survey.core.domain.policy.checklist.Editability;

/**
 * What a viewer may do with a checklist's photos now (survey-media spec 7.2, A-1, A-23): the one function the media
 * list and the checklist read ({@code ChecklistResponse.media.uploadEnabled}) both use.
 */
public final class MediaViewerRights {

    private MediaViewerRights() {
    }

    /**
     * survey-media spec 7.2: uploads on, intake not paused and the viewer could create now: the holder who is the lead's eligible
     * assignee on an editable checklist (the checklist's editability for this viewer already says all three).
     */
    public static boolean uploadEnabled(boolean uploadsOn, IntakeHealth health, Editability editabilityForViewer) {
        return uploadsOn && !health.paused() && editabilityForViewer.editable();
    }
}
