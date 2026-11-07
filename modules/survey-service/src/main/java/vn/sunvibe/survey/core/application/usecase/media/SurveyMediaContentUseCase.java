package vn.sunvibe.survey.core.application.usecase.media;

import vn.sunvibe.survey.core.application.command.media.ContentBody;
import vn.sunvibe.survey.core.application.command.media.UploadContentCommand;
import vn.sunvibe.survey.core.application.query.media.MediaContentResult;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;

/** The raw-bytes upload of a survey photo (survey-media spec 4.4 step 2, 5.4 content order, 7.1). */
public interface SurveyMediaContentUseCase {

    /**
     * Checks, claims, receives, stores and commits the photo's bytes. An answer decided before the body slots leaves
     * the body untouched ({@link ContentBody#touched()} false: the caller closes the connection); every later answer
     * comes after the body was read or drained.
     */
    MediaContentResult upload(SurveyViewer viewer, boolean viewerCanHold, UploadContentCommand command,
                              ContentBody body);
}
