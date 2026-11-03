package vn.sunvibe.survey.core.application.query;

/** A photo and the set version after an ADMIN hold or release, or after its no-op (survey-media spec A-23, 7.1). */
public record MediaStateResult(MediaItemView media, long setVersion) {
}
