package vn.sunvibe.survey.core.application.command.media;

import vn.sunvibe.survey.core.application.command.common.CorrectionInput;

/**
 * A holder's deselection of a carried photo as sent (survey-checklist submission spec 4.6, 7.1): the revision, the photo
 * and the correction, null when none is sent.
 */
public record DeselectMediaCommand(String checklistId, String mediaId, CorrectionInput correctionOrNull) {
}
