package vn.sunvibe.survey.core.application.shared;

import java.time.Instant;

/**
 * The application clock of survey-media (plan Global Constraints: {@code AppClock}, truncated to microseconds as the
 * TIMESTAMPTZ columns store it), as a seam the clean-up and outage tests can move (spec A-15).
 */
@FunctionalInterface
public interface MediaClock {

    /** The current application-clock time, in microseconds. */
    Instant now();
}
