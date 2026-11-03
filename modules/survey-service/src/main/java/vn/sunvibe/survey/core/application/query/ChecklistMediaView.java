package vn.sunvibe.survey.core.application.query;

/**
 * The media block of a checklist answer (survey-media spec A-1, 7.1): whether the checklist's photos are available
 * (uploads on or a media-set row exists), whether this viewer could upload now (the same function as the media list's
 * {@code uploadEnabled}) and the set version the evidence items of readiness were computed from (0 without a set row).
 */
public record ChecklistMediaView(boolean available, boolean uploadEnabled, long setVersion) {
}
