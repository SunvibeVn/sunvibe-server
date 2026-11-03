package vn.sunvibe.survey.core.domain.model.media;

import java.time.Instant;

/**
 * The photo records and declared bytes a user created in the rolling hour (survey-media spec A-3), with the creation
 * time of the oldest of them (null when none): a refused create retries once it has left the hour.
 */
public record HourlyUsage(int records, long bytes, Instant oldestOrNull) {
}
