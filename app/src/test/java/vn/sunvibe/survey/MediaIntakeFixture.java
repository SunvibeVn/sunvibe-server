package vn.sunvibe.survey;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.jdbc.core.JdbcTemplate;

import vn.sunvibe.survey.core.domain.model.media.PauseReason;

/**
 * The intake health row for the media API tests (survey-media spec A-18): the migration seeds it paused, so a test
 * that uploads opens it first, with a fresh sweep time.
 */
public final class MediaIntakeFixture {

	private MediaIntakeFixture() {
	}

	/** Intake open: no pause, no scan block, a complete sweep just now. */
	public static void open(JdbcTemplate jdbc) {
		jdbc.update("UPDATE survey_media_health SET intake_paused_since = NULL, pause_reason = NULL,"
				+ " scan_blocked_since = NULL, last_sweep_ok_at = ? WHERE id = 1",
				Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MICROS)));
	}

	/** Intake paused for the reason, as a failing path would leave it. */
	public static void pause(JdbcTemplate jdbc, PauseReason reason) {
		jdbc.update("UPDATE survey_media_health SET intake_paused_since = ?, pause_reason = ? WHERE id = 1",
				Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MICROS)), reason.name());
	}

	/** The seeded state of the migration: paused NOT_STARTED, never swept. */
	public static void reset(JdbcTemplate jdbc) {
		jdbc.update("UPDATE survey_media_health SET failure_seq = 0, state_epoch = 0, last_sweep_ok_at = NULL,"
				+ " last_probe_ok_at = NULL, last_failure_at = NULL, last_worker_beat_at = NULL,"
				+ " last_deletion_beat_at = NULL, scan_blocked_since = NULL, intake_paused_since = ?,"
				+ " pause_reason = 'NOT_STARTED' WHERE id = 1",
				Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MICROS)));
	}
}
