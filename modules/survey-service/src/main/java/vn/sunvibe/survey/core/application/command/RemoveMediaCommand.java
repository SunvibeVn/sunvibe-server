package vn.sunvibe.survey.core.application.command;

/** A holder's removal of a photo as sent (survey-media spec 4.7, 7.1): the correction is null when none is sent. */
public record RemoveMediaCommand(String mediaId, CorrectionInput correctionOrNull) {
}
