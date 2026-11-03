package vn.sunvibe.survey.core.application.usecase;

import java.util.Optional;

import vn.sunvibe.survey.core.application.query.MediaListView;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;

/** The survey photo reads of staff (survey-media spec 4.3, 7.1). */
public interface SurveyMediaQueryUseCase {

    /**
     * spec 7.1, A-30: the checklist's photos for this viewer; empty (204) when {@code knownVersionOrNull} equals the
     * version the view would be computed from.
     *
     * @param viewerCanHold the viewer may hold photos (ADMIN, spec A-23)
     */
    Optional<MediaListView> list(SurveyViewer viewer, boolean viewerCanHold, String checklistId,
                                 String knownVersionOrNull);
}
