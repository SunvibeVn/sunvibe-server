package vn.sunvibe.survey.core.application.shared;

import java.time.Duration;

/**
 * The content read bounds of this instance (survey-media spec A-25): concurrent GETs per user and per instance, the
 * no-progress timeout and the total deadline of each variant.
 */
public record MediaReadSettings(int userConcurrency, int instanceConcurrency, Duration noProgressTimeout,
                                Duration fullDeadline, Duration previewDeadline, Duration thumbnailDeadline) {

    public MediaReadSettings {
        if (userConcurrency < 1 || instanceConcurrency < 1 || noProgressTimeout == null || fullDeadline == null
                || previewDeadline == null || thumbnailDeadline == null) {
            throw new IllegalArgumentException("Read bounds are positive.");
        }
    }
}
