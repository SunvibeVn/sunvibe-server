package vn.sunvibe.survey.core.infrastructure.config;

/**
 * What survey-media runs with after the start-up guard (survey-media spec A-1): uploads and processing are effectively
 * off while every adapter is {@code none}, whatever the switches say.
 *
 * @param nonProduction   the active-profile set is non-empty and inside {dev, test, lan}
 * @param everyAdapterNone store, scanner and processor are all {@code none}: nothing is scheduled (plan PD-4)
 */
public record MediaRuntimeMode(boolean nonProduction, boolean everyAdapterNone, boolean uploadsOn,
                               boolean processingOn, boolean servingOn) {
}
