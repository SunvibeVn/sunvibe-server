package vn.sunvibe.survey.core.application.usecase;

import vn.sunvibe.survey.core.application.query.MediaRead;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;

/** The content reads of survey photos (survey-media spec 5.4 content GET order, 7.1, A-24, A-25, A-26, A-30). */
public interface SurveyMediaReadUseCase {

    /**
     * spec A-24 (0): serving on, checked right after authentication and the role check, before any media query.
     *
     * @throws vn.sunvibe.survey.core.application.exception.MediaServingDisabledException when serving is off
     */
    void requireServing();

    /**
     * (1) variant and {@code download} 400 → (2) 404 → (3) 409 not servable → (4) read slots → (5) budget → the store
     * → the access row; the caller streams the answer and closes it.
     *
     * @param viewerCanHold the viewer is ADMIN (spec A-23): a held photo with its content is readable
     */
    MediaRead open(SurveyViewer viewer, boolean viewerCanHold, String mediaId, String variant, String download);
}
