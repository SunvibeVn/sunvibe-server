package vn.sunvibe.survey.core.application.usecase.media;

import vn.sunvibe.survey.core.application.command.media.CreateMediaCommand;
import vn.sunvibe.survey.core.application.command.media.DeselectMediaCommand;
import vn.sunvibe.survey.core.application.command.media.RemoveMediaCommand;
import vn.sunvibe.survey.core.application.query.media.MediaContentResult;
import vn.sunvibe.survey.core.application.query.media.MediaStateResult;
import vn.sunvibe.survey.core.domain.model.media.HoldReason;
import vn.sunvibe.survey.core.application.query.media.MediaCreateResult;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;

/** The survey photo commands of staff (survey-media spec 4.4, 5.4, 7.1). */
public interface SurveyMediaCommandUseCase {

    /**
     * spec 4.4 step 1: the checklist holder creates a photo record awaiting its bytes; the same upload key and body
     * again is the replay of that photo.
     *
     * @param viewerCanHold the viewer may hold photos (ADMIN, spec A-23), which only changes how a held photo is shown
     */
    MediaCreateResult create(SurveyViewer viewer, boolean viewerCanHold, CreateMediaCommand command);

    /**
     * The holder removes a photo (survey-media spec 4.7, 5.4 removal order): the photo, the set version and the
     * CHECKLIST_MEDIA_REMOVED History entry of a PROCESSING or READY photo; removing again answers the photo as it is.
     */
    MediaContentResult remove(SurveyViewer viewer, boolean viewerCanHold, RemoveMediaCommand command);

    /**
     * The holder takes a carried photo out of a later revision (survey-checklist submission spec 4.6, A-12): the photo
     * stays as it is in its origin, is never removed and asks for no deletion. Deselecting again changes nothing.
     */
    boolean deselect(SurveyViewer viewer, DeselectMediaCommand command);

    /** An ADMIN holds a PROCESSING or READY photo that is not removed (survey-media spec A-23); a held one: a no-op. */
    MediaStateResult hold(SurveyViewer viewer, String mediaId, HoldReason reasonOrNull);

    /** An ADMIN releases a photo's hold (survey-media spec A-23); a photo not held: a no-op. */
    MediaStateResult release(SurveyViewer viewer, String mediaId);
}
