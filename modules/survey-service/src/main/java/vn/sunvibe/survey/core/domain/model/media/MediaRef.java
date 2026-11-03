package vn.sunvibe.survey.core.domain.model.media;

/** A photo and its checklist, as a sweep or job finder returns them (survey-media spec 4.4 step 4). */
public record MediaRef(SurveyMediaId mediaId, String checklistId) {
}
