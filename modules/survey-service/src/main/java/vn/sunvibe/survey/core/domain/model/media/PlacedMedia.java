package vn.sunvibe.survey.core.domain.model.media;

/** The id, placement and category of a photo, read by a scalar query before its row is locked (survey-media A-9). */
public record PlacedMedia(SurveyMediaId id, String spaceIdOrNull, EvidenceCategory category) {
}
