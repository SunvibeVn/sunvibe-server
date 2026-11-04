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
    /**
     * The list as the answer of a deselection (survey-checklist submission spec 7.1): a change that was committed is
     * outside the hourly list budget and never answers 429; a repeat changed nothing, so it counts as a read.
     */
    MediaListView listAfterDeselection(SurveyViewer viewer, boolean viewerCanHold, String checklistId, boolean changed);

    Optional<MediaListView> list(SurveyViewer viewer, boolean viewerCanHold, String checklistId,
                                 String knownVersionOrNull);
}
