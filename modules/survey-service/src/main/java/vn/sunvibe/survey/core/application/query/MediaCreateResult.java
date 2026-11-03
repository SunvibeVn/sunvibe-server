package vn.sunvibe.survey.core.application.query;

/**
 * The answer to a create (survey-media spec 4.4 step 1, 7.1): the photo and the set version after the change;
 * {@code created} is false for a replay of the same upload key and body (200 instead of 201).
 */
public record MediaCreateResult(MediaItemView media, long setVersion, boolean created) {
}
