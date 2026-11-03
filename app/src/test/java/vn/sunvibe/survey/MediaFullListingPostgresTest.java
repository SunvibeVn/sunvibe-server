package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.github.f4b6a3.ulid.UlidCreator;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import vn.sunvibe.survey.core.application.service.MediaFullListingJob;
import vn.sunvibe.survey.core.application.service.MediaSweeper;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.Bucket;
import vn.sunvibe.survey.core.domain.model.media.ListingRun;
import vn.sunvibe.survey.core.domain.model.media.PauseReason;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaListingRunDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaVariantDomainRepository;
import vn.sunvibe.survey.core.domain.repository.IntakeHealthRepository;
import vn.sunvibe.survey.core.application.shared.MediaClock;
import vn.sunvibe.survey.core.application.shared.MediaIncidentRecorder;
import vn.sunvibe.survey.core.application.shared.external.media.MediaProbeRequests;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * The daily full listing of {@code media/} (survey-media spec 4.4 step 4, A-18; SM-14 listing part; plan Task A22):
 * one leased, fenced run that resumes from its stored key, deletes orphans as the sweep does, keeps the count of those
 * it could not delete (which blocks a resume until a newer run lists to the end without any), and is abandoned after
 * 24 hours. Needs Docker.
 */
class MediaFullListingPostgresTest extends MediaApiTestBase {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private MediaTestStore store;

	@Autowired
	private MediaFullListingJob listing;

	@Autowired
	private SurveyMediaListingRunDomainRepository runs;

	@Autowired
	private MediaSweeper sweeper;

	@Autowired
	private SurveyMediaVariantDomainRepository variants;

	@Autowired
	private MediaIncidentRecorder incidents;

	@Autowired
	private IntakeHealthRepository health;

	@Autowired
	private MediaClock clock;

	@Autowired
	@Qualifier("Ulid")
	private IdGenerator<String> ulids;

	@BeforeEach
	void clean() {
		jdbc.update("delete from survey_media_listing_runs");
		MediaIntakeFixture.open(jdbc);
	}

	@AfterEach
	void reset() {
		store.reset();
		jdbc.update("delete from survey_media_listing_runs");
		jdbc.update("update survey_media_incidents set resolved_at = greatest(?, opened_at) where resolved_at is null",
				Timestamp.from(Instant.now()));
		MediaIntakeFixture.open(jdbc);
	}

	@Test
	void killedRun_resumesWithStartAfterFromStoredKey() {
		String before = orphan(Duration.ofHours(2));
		String after = orphan(Duration.ofHours(2));
		jdbc.update("insert into survey_media_listing_runs (started_at, lease_token, lease_until, start_after_key,"
				+ " keys_seen, undeleted_count) values (?, 'KILLEDRUNNER0000000000000A', ?, ?, 5, 0)", ts(-3600),
				ts(-60), before);

		assertTrue(listing.runOnce());

		assertTrue(MediaApiTestSupport.objectExists(Bucket.MEDIA, before), "listed before the kill: not again");
		assertFalse(MediaApiTestSupport.objectExists(Bucket.MEDIA, after));
		Map<String, Object> run = jdbc.queryForMap("select * from survey_media_listing_runs");
		assertNotNull(run.get("finished_at"));
		assertNull(run.get("start_after_key"), "listed to the end");
		assertTrue(((Number) run.get("keys_seen")).longValue() > 5);
	}

	/**
	 * survey-media spec A-18, BR-18 (migration re-review m-2 and m-3, SV-069): an overdue orphan is recorded and pauses
	 * intake once its page's progress is written, not only when a run lists to the end; a listing that fails asks for a
	 * store probe.
	 */
	@Test
	void overdueOrphanOnFirstPage_recordedAndPausedBeforeTheRunEnds_failedListAsksStoreProbe() {
		orphan(Duration.ofHours(25));
		store.mediaPagesBeforeListFailure.set(1);
		RecordingProbeRequests probes = new RecordingProbeRequests();

		assertTrue(job(probes).runOnce());

		assertNull(jdbc.queryForObject("select finished_at from survey_media_listing_runs", Timestamp.class),
				"the run stopped at its second page");
		assertTrue(jdbc.queryForObject("select media_count from survey_media_incidents where kind = 'ORPHAN_OVERDUE'"
				+ " and resolved_at is null", Integer.class) >= 1);
		assertNotNull(pausedSince());
		assertEquals("DELETION_OVERDUE", jdbc.queryForObject("select pause_reason from survey_media_health where"
				+ " id = 1", String.class));
		assertEquals(1, probes.store);
	}

	/** survey-media spec A-18 (migration re-review m-3, SV-069): an orphan the store does not delete asks for a probe. */
	@Test
	void undeletableOrphan_asksStoreProbe() {
		String key = orphan(Duration.ofHours(2));
		store.failDeletes.set(true);
		RecordingProbeRequests probes = new RecordingProbeRequests();
		try {
			assertTrue(job(probes).runOnce());
		} finally {
			store.failDeletes.set(false);
			store.delete(Bucket.MEDIA, key);
		}

		assertTrue(probes.store >= 1);
	}

	@Test
	void fencedOutRunner_cannotWriteProgress() {
		ListingRun mine = runs.startIfDue("RUNNERA0000000000000000001", Instant.now().plusSeconds(300),
				Instant.now().minus(Duration.ofHours(24)), Instant.now()).orElseThrow();
		jdbc.update("update survey_media_listing_runs set lease_token = 'RUNNERB0000000000000000001' where id = ?",
				mine.id());

		assertFalse(runs.progress(mine.id(), "RUNNERA0000000000000000001", "media/x", 10, 1,
				Instant.now().plusSeconds(300)));
		assertFalse(runs.finish(mine.id(), "RUNNERA0000000000000000001", Instant.now()));
		Map<String, Object> run = jdbc.queryForMap("select * from survey_media_listing_runs where id = ?", mine.id());
		assertEquals(0L, ((Number) run.get("keys_seen")).longValue());
		assertNull(run.get("finished_at"));
	}

	@Test
	void undeletableOrphan_keepsCountAndBlocksResume_untilNextRunDeletesAndListsToEnd() {
		String orphan = orphan(Duration.ofHours(2));
		store.failDeletes.set(true);
		Logger logger = (Logger) LoggerFactory.getLogger(MediaFullListingJob.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			assertTrue(listing.runOnce());
		} finally {
			logger.detachAppender(appender);
		}

		assertEquals(1, jdbc.queryForObject("select undeleted_count from survey_media_listing_runs", Integer.class));
		String mediaId = orphan.split("/")[1];
		// migration review I-2: the operator learns which photo's objects stayed, by id only (never the key)
		assertTrue(appender.list.stream().anyMatch(event -> event.getLevel() == Level.WARN
				&& event.getFormattedMessage().contains(mediaId)), appender.list.toString());
		assertTrue(appender.list.stream().noneMatch(event -> event.getFormattedMessage().contains("media/")),
				appender.list.toString());
		assertTrue(runs.blocksResume());
		MediaIntakeFixture.pause(jdbc, PauseReason.STORE);
		store.failDeletes.set(false);
		sweeper.sweep();
		assertNotNull(pausedSince(), "the run's undeleted orphan blocks the resume");

		jdbc.update("update survey_media_listing_runs set started_at = ?", ts(-25 * 3600));
		assertTrue(listing.runOnce());

		assertFalse(MediaApiTestSupport.objectExists(Bucket.MEDIA, orphan));
		assertFalse(runs.blocksResume());
		sweeper.sweep();
		assertNull(pausedSince());
	}

	/** Migration re-review Minor 2: ids only (a hand-placed name prints as ?), at most ten per page, then the count. */
	@Test
	void undeletableOrphans_loggedByIdOnly_atMostTenPerPage() {
		MediaApiTestSupport.storedObject(store, Bucket.MEDIA, "media/0-holiday-photo.jpg", Duration.ofHours(2));
		for (int i = 0; i < 11; i++) {
			orphan(Duration.ofHours(2));
		}
		store.failDeletes.set(true);
		Logger logger = (Logger) LoggerFactory.getLogger(MediaFullListingJob.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			assertTrue(listing.runOnce());
		} finally {
			logger.detachAppender(appender);
			store.failDeletes.set(false);
		}

		assertTrue(jdbc.queryForObject("select undeleted_count from survey_media_listing_runs", Integer.class) >= 12,
				"other classes may leave orphans in the shared store");
		long perOrphan = appender.list.stream().filter(event -> event.getFormattedMessage().contains("an orphan of photo"))
				.count();
		assertEquals(10, perOrphan, appender.list.toString());
		assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("photo ? (")),
				appender.list.toString());
		assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("orphans on this page")),
				appender.list.toString());
		assertTrue(appender.list.stream().noneMatch(event -> event.getFormattedMessage().contains("holiday")),
				appender.list.toString());
	}

	@Test
	void newerCompleteRun_supersedesOlderCount() {
		jdbc.update("insert into survey_media_listing_runs (started_at, keys_seen, undeleted_count, finished_at)"
				+ " values (?, 10, 2, ?)", ts(-50 * 3600), ts(-49 * 3600));
		assertTrue(runs.blocksResume());

		jdbc.update("insert into survey_media_listing_runs (started_at, keys_seen, undeleted_count, finished_at)"
				+ " values (?, 10, 0, ?)", ts(-26 * 3600), ts(-25 * 3600));

		assertFalse(runs.blocksResume());
	}

	/**
	 * Spec conflict A-18 vs SM-14, resolved 2026-11-03 for SM-14 read in full: an older complete run's undeleted
	 * orphans keep blocking while a newer run is open (it has not reached them yet) and stop blocking when that run
	 * ends, by listing to the end (its own count then decides) or by abandonment (it then blocks nothing, so no run
	 * blocks the resume for ever).
	 */
	@Test
	void newerOpenRun_keepsOlderCountBlocking_untilItListsToTheEnd() {
		jdbc.update("insert into survey_media_listing_runs (started_at, keys_seen, undeleted_count, finished_at)"
				+ " values (?, 10, 2, ?)", ts(-26 * 3600), ts(-25 * 3600));
		long newer = jdbc.queryForObject("insert into survey_media_listing_runs (started_at, lease_token, lease_until,"
				+ " start_after_key, keys_seen, undeleted_count) values (?, 'NEWERRUNNER000000000000001', ?, 'media/01',"
				+ " 7, 0) returning id", Long.class, ts(-60), ts(240));

		assertTrue(runs.blocksResume(), "the open run has not reached the older run's orphans yet");
		MediaIntakeFixture.pause(jdbc, PauseReason.DELETION_OVERDUE);
		sweeper.sweep();
		assertNotNull(pausedSince(), "the older run's undeleted orphans block the resume");

		jdbc.update("update survey_media_listing_runs set finished_at = ?, lease_token = null, lease_until = null,"
				+ " start_after_key = null where id = ?", ts(0), newer);

		assertFalse(runs.blocksResume(), "the newer run listed to the end without any");
		sweeper.sweep();
		assertNull(pausedSince());
	}

	@Test
	void newerRunAbandoned_endsTheOlderBlock_soNoRunBlocksForEver() {
		jdbc.update("insert into survey_media_listing_runs (started_at, keys_seen, undeleted_count, finished_at)"
				+ " values (?, 10, 2, ?)", ts(-50 * 3600), ts(-49 * 3600));
		// never listed a page (a listing outage): no key until the abandonment marks it
		jdbc.update("insert into survey_media_listing_runs (started_at, lease_token, lease_until, keys_seen,"
				+ " undeleted_count) values (?, 'STUCKRUNNER000000000000002', ?, 0, 0)", ts(-25 * 3600), ts(3600));
		assertTrue(runs.blocksResume(), "the newer run is still open");

		assertEquals(1, runs.abandonStartedBefore(Instant.now().minus(Duration.ofHours(24)), Instant.now()).size());

		assertEquals(1, jdbc.queryForObject("select count(*) from survey_media_listing_runs where finished_at is not"
				+ " null and start_after_key is not null", Integer.class), "abandoned, not read as listed to the end");
		assertFalse(runs.blocksResume(), "the next run ended: the older count no longer blocks");
	}

	@Test
	void abandonedRunWithUndeletedOrphans_blocksNothing() {
		jdbc.update("insert into survey_media_listing_runs (started_at, keys_seen, undeleted_count, finished_at)"
				+ " values (?, 10, 0, ?)", ts(-50 * 3600), ts(-49 * 3600));
		jdbc.update("insert into survey_media_listing_runs (started_at, lease_token, lease_until, start_after_key,"
				+ " keys_seen, undeleted_count) values (?, 'STUCKRUNNER000000000000003', ?, 'media/01', 7, 3)",
				ts(-25 * 3600), ts(3600));
		assertTrue(runs.blocksResume(), "open with orphans it could not delete");

		runs.abandonStartedBefore(Instant.now().minus(Duration.ofHours(24)), Instant.now());

		assertFalse(runs.blocksResume(), "an abandoned run blocks nothing");
	}

	@Test
	void runOpenOver24Hours_abandonedThenBlocksNothing() {
		jdbc.update("insert into survey_media_listing_runs (started_at, lease_token, lease_until, start_after_key,"
				+ " keys_seen, undeleted_count) values (?, 'STUCKRUNNER000000000000001', ?, 'media/01', 7, 3)",
				ts(-25 * 3600), ts(3600));
		assertTrue(runs.blocksResume());

		listing.runOnce();

		assertEquals(0, jdbc.queryForObject("select count(*) from survey_media_listing_runs where undeleted_count = 3"
				+ " and finished_at is null", Integer.class));
		assertFalse(runs.blocksResume(), "an abandoned run counts for nothing");
	}

	@Test
	void oneOpenRun_partialUniqueHolds() {
		jdbc.update("insert into survey_media_listing_runs (started_at, keys_seen, undeleted_count) values (?, 0, 0)",
				ts(0));

		assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into survey_media_listing_runs"
				+ " (started_at, keys_seen, undeleted_count) values (?, 0, 0)", ts(0)));
	}

	// ==================== helpers ====================

	/** The full listing as the context builds it, with probe requests recorded. */
	private MediaFullListingJob job(MediaProbeRequests probes) {
		return new MediaFullListingJob(runs, variants, store, incidents, health, clock, ulids, probes);
	}

	private static final class RecordingProbeRequests implements MediaProbeRequests {
		int store;

		@Override
		public void requestStoreProbe() {
			store++;
		}

		@Override
		public void requestScannerProbe() {
		}
	}

	/** An object under media/ that no variant row references, its age set. */
	private String orphan(Duration age) {
		String key = "media/" + UlidCreator.getMonotonicUlid() + "/1/FULL";
		MediaApiTestSupport.storedObject(store, Bucket.MEDIA, key, age);
		return key;
	}

	private Timestamp pausedSince() {
		return jdbc.queryForObject("select intake_paused_since from survey_media_health where id = 1",
				Timestamp.class);
	}

	private static Timestamp ts(long secondsFromNow) {
		return Timestamp.from(Instant.now().plusSeconds(secondsFromNow));
	}
}
