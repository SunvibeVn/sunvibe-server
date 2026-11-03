package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.survey.core.domain.exception.MediaDuplicateContentException;
import vn.sunvibe.survey.core.domain.exception.MediaUploadKeyTakenException;
import vn.sunvibe.survey.core.domain.model.appointment.Basis;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.log.AccessResourceType;
import vn.sunvibe.survey.core.domain.model.log.SurveyAccessLog;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.model.media.HoldReason;
import vn.sunvibe.survey.core.domain.model.media.MediaCounterKind;
import vn.sunvibe.survey.core.domain.model.media.MediaJob;
import vn.sunvibe.survey.core.domain.model.media.MediaJobKind;
import vn.sunvibe.survey.core.domain.model.media.MediaJobState;
import vn.sunvibe.survey.core.domain.model.media.MediaPlacement;
import vn.sunvibe.survey.core.domain.model.media.MediaRemovalCorrection;
import vn.sunvibe.survey.core.domain.model.media.MediaSet;
import vn.sunvibe.survey.core.domain.model.media.MediaStatus;
import vn.sunvibe.survey.core.domain.model.media.MediaStatusReason;
import vn.sunvibe.survey.core.domain.model.media.MediaVariant;
import vn.sunvibe.survey.core.domain.model.media.RemovalKind;
import vn.sunvibe.survey.core.domain.model.media.SourceFormat;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.model.media.VariantKind;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.model.security.SecurityOutcome;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.model.security.SurveySecurityEvent;
import vn.sunvibe.survey.core.domain.repository.MediaCountersRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyAccessLogDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaBodySlotDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaJobDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaSetDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaVariantDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveySecurityEventDomainRepository;

/**
 * survey-media persistence on PostgreSQL (spec 5.2 JPA notes, 5.4; plan Task A4): every status with its marks
 * round-trips; the row locks are the ones the lock order relies on; conditional updates bump the version and never
 * leave a stale managed entity; unique violations become domain exceptions; the counters' SQL works. Needs Docker.
 */
class MediaPersistencePostgresTest extends MediaApiTestBase {

	/** pg_try_advisory_xact_lock of an event key on another connection (spec A-19). */
	private static boolean tryEventKey(java.sql.Connection other, String key) {
		try (java.sql.PreparedStatement lock = other.prepareStatement(
				"select pg_try_advisory_xact_lock(?, hashtext(?))")) {
			lock.setInt(1, vn.sunvibe.survey.core.infrastructure.persistence.adapter.MediaSql.EVENT_LOCK_NAMESPACE);
			lock.setString(2, key);
			try (java.sql.ResultSet row = lock.executeQuery()) {
				row.next();
				return row.getBoolean(1);
			}
		} catch (java.sql.SQLException e) {
			throw new IllegalStateException(e);
		}
	}

	private static final String LEAD = "01JLEADMED1APERS1STAAAAAAA";
	private static final String OTHER_SALE = "01JSA1EMED1A00000000000002";
	private static final String ADMIN = "01JADM1NMED1A0000000000001";
	private static final Instant T0 = Instant.parse("2026-11-01T02:00:00.123456Z");
	private static final String LOCK_NOT_AVAILABLE = "55P03";

	@Autowired
	private SurveyMediaDomainRepository media;

	@Autowired
	private SurveyMediaSetDomainRepository sets;

	@Autowired
	private SurveyMediaVariantDomainRepository variants;

	@Autowired
	private SurveyMediaJobDomainRepository jobs;

	@Autowired
	private SurveyMediaBodySlotDomainRepository bodySlots;

	@Autowired
	private MediaCountersRepository counters;

	@Autowired
	private SurveyChecklistDomainRepository checklists;

	@Autowired
	private SurveyAccessLogDomainRepository accessLogs;

	@Autowired
	private SurveySecurityEventDomainRepository securityEvents;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private String checklist;
	private int sequence;

	@BeforeEach
	void createChecklist() {
		MediaTestRows.delete(jdbc, List.of(LEAD));
		jdbc.update("DELETE FROM survey_media_read_budgets");
		jdbc.update("DELETE FROM survey_media_body_slots");
		checklist = MediaTestRows.checklist(jdbc, LEAD, 1);
		inTransaction(() -> {
			sets.insertIgnoringExisting(checklist, T0);
			return null;
		});
	}

	@AfterEach
	void clean() {
		MediaTestRows.delete(jdbc, List.of(LEAD));
		jdbc.update("DELETE FROM survey_media_read_budgets");
		jdbc.update("DELETE FROM survey_media_body_slots");
	}

	@AfterAll
	void cleanAll() {
		clean();
	}

	// ==================== round trip ====================

	@Test
	void roundTrip_everyStatusWithMarksAndHold() {
		List<SurveyMedia> stored = new ArrayList<>();
		stored.add(stored(photo(EvidenceCategory.EXTERIOR_ACCESS, MediaPlacement.asset())));
		SurveyMedia receiving = stored(photo(EvidenceCategory.KITCHEN, MediaPlacement.space(
				"01JSPACEMED1A0000000000001", "P101")));
		receiving.claim("01JT0KENMED1A0000000000001", "quarantine/" + receiving.getId() + "/1", T0.plusSeconds(60));
		stored.add(updated(receiving));
		SurveyMedia ready = processing(stored(photo(EvidenceCategory.DETAIL, MediaPlacement.asset())));
		ready.hold(ADMIN, HoldReason.CREDENTIAL_EXPOSURE, T0.plusSeconds(100));
		ready.ready(variantsOf(ready, 1), SourceFormat.HEIF, T0.plusSeconds(120));
		ready.markSourceDeleted(T0.plusSeconds(121));
		ready.release(ADMIN, T0.plusSeconds(130));
		inTransaction(() -> {
			variants.saveAll(ready.getId(), ready.getVariants());
			media.update(ready);
			return null;
		});
		stored.add(ready);
		SurveyMedia removed = processing(stored(photo(EvidenceCategory.BATHROOM, MediaPlacement.space(
				"01JSPACEMED1A0000000000001", "P101"))));
		removed.ready(variantsOf(removed, 2), SourceFormat.JPEG, T0.plusSeconds(120));
		removed.remove(RemovalKind.HOLDER, OTHER_SALE, new MediaRemovalCorrection("Sai phòng",
				new Basis(LocalDate.parse("2026-10-31"), "Chủ nhà xác nhận")), null, T0.plusSeconds(200));
		removed.markContentDeleted(T0.plusSeconds(900));
		inTransaction(() -> {
			variants.saveAll(removed.getId(), removed.getVariants());
			media.update(removed);
			return null;
		});
		stored.add(removed);
		SurveyMedia cancelled = processing(stored(photo(EvidenceCategory.MAIN_SPACE, MediaPlacement.space(
				"01JSPACEMED1A0000000000001", "P101"))));
		cancelled.remove(RemovalKind.CASCADE, OTHER_SALE, null, 77L, T0.plusSeconds(200));
		cancelled.cancel(T0.plusSeconds(210));
		stored.add(updated(cancelled));
		SurveyMedia rejected = processing(stored(photo(EvidenceCategory.EXTERIOR_ACCESS, MediaPlacement.asset())));
		rejected.reject(MediaStatusReason.MALWARE_DETECTED, T0.plusSeconds(150));
		stored.add(updated(rejected));
		SurveyMedia failed = processing(stored(photo(EvidenceCategory.EXTERIOR_ACCESS, MediaPlacement.asset())));
		failed.fail(T0.plusSeconds(150));
		stored.add(updated(failed));
		SurveyMedia expired = stored(photo(EvidenceCategory.EXTERIOR_ACCESS, MediaPlacement.asset()));
		expired.expire(T0.plusSeconds(3700));
		stored.add(updated(expired));
		SurveyMedia missing = processing(stored(photo(EvidenceCategory.EXTERIOR_ACCESS, MediaPlacement.asset())));
		missing.ready(variantsOf(missing, 1), SourceFormat.PNG, T0.plusSeconds(120));
		missing.hold(ADMIN, HoldReason.SECURITY_INCIDENT, T0.plusSeconds(125));
		missing.markContentMissing(T0.plusSeconds(400));
		inTransaction(() -> {
			variants.saveAll(missing.getId(), missing.getVariants());
			media.update(missing);
			return null;
		});
		stored.add(missing);

		List<SurveyMedia> loaded = inTransaction(() -> media.findForChecklist(checklist));

		assertEquals(stored.size(), loaded.size());
		for (int i = 0; i < stored.size(); i++) {
			assertSame(stored.get(i), loaded.get(i));
		}
		assertEquals(List.of(MediaStatus.AWAITING_CONTENT, MediaStatus.RECEIVING, MediaStatus.READY,
				MediaStatus.READY, MediaStatus.CANCELLED, MediaStatus.REJECTED, MediaStatus.FAILED,
				MediaStatus.UPLOAD_EXPIRED, MediaStatus.READY), loaded.stream().map(SurveyMedia::getStatus).toList());
	}

	@Test
	void update_staleVersion_failsOptimistically() {
		SurveyMedia photo = stored(photo(EvidenceCategory.EXTERIOR_ACCESS, MediaPlacement.asset()));
		SurveyMedia stale = inTransaction(() -> media.findForChecklist(checklist)).get(0);
		photo.claim("01JT0KENMED1A0000000000001", "quarantine/x/1", T0.plusSeconds(10));
		updated(photo);

		stale.expire(T0.plusSeconds(20));
		assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class, () -> updated(stale));
	}

	// ==================== locks (spec 5.4) ====================

	@Test
	void lockForUpdate_blocksSecondLockNowait() {
		SurveyMedia first = stored(photo(EvidenceCategory.EXTERIOR_ACCESS, MediaPlacement.asset()));
		SurveyMedia second = stored(photo(EvidenceCategory.DETAIL, MediaPlacement.asset()));

		inTransaction(() -> {
			List<SurveyMedia> locked = media.lockForUpdate(List.of(second.getId(), first.getId()));
			assertEquals(List.of(first.getId(), second.getId()), locked.stream().map(SurveyMedia::getId).toList());
			probe(other -> {
				SqlFailure refused = assertThrows(SqlFailure.class, () -> execute(other,
						"SELECT 1 FROM survey_media WHERE id = ? FOR UPDATE NOWAIT", second.getId().getValue()));
				assertEquals(LOCK_NOT_AVAILABLE, refused.sqlState());
			});
			return null;
		});
	}

	@Test
	void setLockForUpdate_blocksSecondLockNowait() {
		inTransaction(() -> {
			MediaSet set = sets.lockForUpdate(checklist).orElseThrow();
			assertEquals(0L, set.getSetVersion());
			probe(other -> {
				SqlFailure refused = assertThrows(SqlFailure.class, () -> execute(other,
						"SELECT 1 FROM survey_media_sets WHERE checklist_id = ? FOR UPDATE NOWAIT", checklist));
				assertEquals(LOCK_NOT_AVAILABLE, refused.sqlState());
			});
			return null;
		});
	}

	@Test
	void findByIdForShare_allowsShareNowaitAndBlocksUpdateNowait() {
		inTransaction(() -> {
			assertTrue(checklists.findByIdForShare(SurveyChecklistId.of(checklist)).isPresent());
			probe(other -> {
				execute(other, "SELECT 1 FROM survey_checklists WHERE id = ? FOR SHARE NOWAIT", checklist);
				SqlFailure refused = assertThrows(SqlFailure.class, () -> execute(other,
						"SELECT 1 FROM survey_checklists WHERE id = ? FOR UPDATE NOWAIT", checklist));
				assertEquals(LOCK_NOT_AVAILABLE, refused.sqlState());
			});
			return null;
		});
	}

	// ==================== conditional updates ====================

	@Test
	void claimIfAwaiting_beforeDeadline_claims_afterDeadline_updatesNothing() {
		SurveyMedia photo = stored(photo(EvidenceCategory.EXTERIOR_ACCESS, MediaPlacement.asset()));

		boolean late = inTransaction(() -> media.claimIfAwaiting(photo.getId(), "01JT0KENMED1A0000000000001",
				"quarantine/x/1", T0.plusSeconds(3600), T0));
		boolean inTime = inTransaction(() -> media.claimIfAwaiting(photo.getId(), "01JT0KENMED1A0000000000001",
				"quarantine/x/1", T0.plusSeconds(60), T0.minusSeconds(3540)));
		boolean twice = inTransaction(() -> media.claimIfAwaiting(photo.getId(), "01JT0KENMED1A0000000000002",
				"quarantine/x/2", T0.plusSeconds(61), T0.minusSeconds(3539)));

		assertFalse(late);
		assertTrue(inTime);
		assertFalse(twice);
		SurveyMedia reloaded = reload(photo.getId());
		assertEquals(MediaStatus.RECEIVING, reloaded.getStatus());
		assertEquals("01JT0KENMED1A0000000000001", reloaded.getReceiveToken());
		assertEquals(1L, reloaded.getVersion());
	}

	@Test
	void claimIfAwaiting_afterDeadline_updatesNothing() {
		SurveyMedia photo = stored(photo(EvidenceCategory.EXTERIOR_ACCESS, MediaPlacement.asset()));

		assertFalse(inTransaction(() -> media.claimIfAwaiting(photo.getId(), "01JT0KENMED1A0000000000001",
				"quarantine/x/1", T0.plusSeconds(3600), T0)));
		assertEquals(MediaStatus.AWAITING_CONTENT, reload(photo.getId()).getStatus());
		assertEquals(0L, reload(photo.getId()).getVersion());
	}

	@Test
	void releaseClaimIfToken_onlyWithTheClaimToken() {
		SurveyMedia photo = stored(photo(EvidenceCategory.EXTERIOR_ACCESS, MediaPlacement.asset()));
		inTransaction(() -> media.claimIfAwaiting(photo.getId(), "01JT0KENMED1A0000000000001", "quarantine/x/1",
				T0.plusSeconds(60), T0.minusSeconds(3540)));

		assertFalse(inTransaction(() -> media.releaseClaimIfToken(photo.getId(), "01JT0KENMED1A0000000000009")));
		assertTrue(inTransaction(() -> media.releaseClaimIfToken(photo.getId(), "01JT0KENMED1A0000000000001")));

		SurveyMedia reloaded = reload(photo.getId());
		assertEquals(MediaStatus.AWAITING_CONTENT, reloaded.getStatus());
		assertNull(reloaded.getReceiveToken());
		assertEquals(2L, reloaded.getVersion());
	}

	@Test
	void conditionalUpdate_bumpsVersionAndClearsContext() {
		SurveyMedia photo = stored(photo(EvidenceCategory.EXTERIOR_ACCESS, MediaPlacement.asset()));

		SurveyMedia after = inTransaction(() -> {
			media.findForChecklist(checklist);
			media.claimIfAwaiting(photo.getId(), "01JT0KENMED1A0000000000001", "quarantine/x/1", T0.plusSeconds(60),
					T0.minusSeconds(3540));
			return media.lockForUpdate(List.of(photo.getId())).get(0);
		});

		assertEquals(MediaStatus.RECEIVING, after.getStatus());
		assertEquals(1L, after.getVersion());
	}

	@Test
	void expireStale_onlyAwaitingCreatedBeforeTheCutoff() {
		SurveyMedia old = stored(photo(EvidenceCategory.EXTERIOR_ACCESS, MediaPlacement.asset()));
		SurveyMedia receiving = stored(photo(EvidenceCategory.DETAIL, MediaPlacement.asset()));
		inTransaction(() -> media.claimIfAwaiting(receiving.getId(), "01JT0KENMED1A0000000000001", "quarantine/x/1",
				T0.plusSeconds(60), T0.minusSeconds(3540)));

		int expired = inTransaction(() -> media.expireStale(checklist, T0, T0.plusSeconds(3601)));

		assertEquals(1, expired);
		assertEquals(MediaStatus.UPLOAD_EXPIRED, reload(old.getId()).getStatus());
		assertEquals(MediaStatusReason.CONTENT_NOT_RECEIVED, reload(old.getId()).getStatusReason());
		assertEquals(1L, reload(old.getId()).getVersion());
		assertEquals(MediaStatus.RECEIVING, reload(receiving.getId()).getStatus());
	}

	// ==================== unique keys (spec 4.4 step 1, A-16) ====================

	@Test
	void insertSameHashSelected_mapsToDuplicateContent() {
		SurveyMedia first = stored(photo(EvidenceCategory.EXTERIOR_ACCESS, MediaPlacement.asset()));
		SurveyMedia twin = SurveyMedia.create(SurveyMediaId.generate(), checklist, "01JUPKEYMED1A9999999999999", 999,
				MediaPlacement.asset(), EvidenceCategory.DETAIL, null, MediaTestRows.SALE, 1000L,
				first.getSourceSha256(), T0);

		assertThrows(MediaDuplicateContentException.class, () -> inTransaction(() -> {
			media.insert(twin);
			return null;
		}));
	}

	@Test
	void insertSameUploadKey_mapsToUploadKeyTaken() {
		SurveyMedia first = stored(photo(EvidenceCategory.EXTERIOR_ACCESS, MediaPlacement.asset()));
		SurveyMedia twin = SurveyMedia.create(SurveyMediaId.generate(), checklist, first.getUploadKey(), 998,
				MediaPlacement.asset(), EvidenceCategory.DETAIL, null, MediaTestRows.SALE, 1000L, sha(9999), T0);

		assertThrows(MediaUploadKeyTakenException.class, () -> inTransaction(() -> {
			media.insert(twin);
			return null;
		}));
	}

	@Test
	void setInsertIgnoringExisting_twiceKeepsOneRow() {
		inTransaction(() -> {
			sets.insertIgnoringExisting(checklist, T0.plusSeconds(5));
			sets.insertIgnoringExisting(checklist, T0.plusSeconds(6));
			return null;
		});

		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM survey_media_sets WHERE checklist_id = ?",
				Integer.class, checklist));
		MediaSet set = inTransaction(() -> sets.findForRead(checklist)).orElseThrow();
		assertEquals(T0, set.getUpdatedAt());
	}

	@Test
	void setUpdate_writesVersionAndNumber() {
		inTransaction(() -> {
			MediaSet set = sets.lockForUpdate(checklist).orElseThrow();
			set.nextMediaNo();
			set.bump(T0.plusSeconds(9));
			sets.update(set);
			return null;
		});

		MediaSet reloaded = inTransaction(() -> sets.findForRead(checklist)).orElseThrow();
		assertEquals(1L, reloaded.getSetVersion());
		assertEquals(1, reloaded.getLastMediaNo());
		assertEquals(T0.plusSeconds(9), reloaded.getUpdatedAt());
	}

	@Test
	void findByUploadKeyAndOtherVisitHashes_findTheirRows() {
		SurveyMedia photo = processing(stored(photo(EvidenceCategory.EXTERIOR_ACCESS, MediaPlacement.asset())));
		String laterChecklist = MediaTestRows.checklist(jdbc, LEAD, 2);

		assertEquals(photo.getId(), inTransaction(() -> media.findByUploadKey(checklist, MediaTestRows.SALE,
				photo.getUploadKey())).orElseThrow().getId());
		assertTrue(inTransaction(() -> media.findByUploadKey(checklist, OTHER_SALE, photo.getUploadKey())).isEmpty());
		assertEquals(List.of(photo.getId()), inTransaction(() -> media.findCommittedHashOnOtherVisits(LEAD,
				laterChecklist, photo.getSourceSha256())).stream().map(SurveyMedia::getId).toList());
		assertEquals(List.of(), inTransaction(() -> media.findCommittedHashOnOtherVisits(LEAD, checklist,
				photo.getSourceSha256())));
		assertEquals(List.of(), inTransaction(() -> media.findCommittedHashOnOtherVisits(
				"01JLEADMED1AOTHER00000000A", laterChecklist, photo.getSourceSha256())));
	}

	// ==================== jobs, slots, counters ====================

	@Test
	void jobs_upsertProcessAndDeleteContent_oneRowPerKind() {
		SurveyMedia photo = processing(stored(photo(EvidenceCategory.EXTERIOR_ACCESS, MediaPlacement.asset())));

		inTransaction(() -> {
			jobs.upsertProcess(photo.getId(), T0.plusSeconds(100), T0.plusSeconds(100));
			jobs.upsertProcess(photo.getId(), T0.plusSeconds(200), T0.plusSeconds(200));
			jobs.upsertDeleteContent(photo.getId(), T0.plusSeconds(700), T0.plusSeconds(100));
			return null;
		});

		List<MediaJob> found = inTransaction(() -> jobs.findByMedia(photo.getId()));
		assertEquals(2, found.size());
		MediaJob process = found.stream().filter(j -> j.kind() == MediaJobKind.PROCESS).findFirst().orElseThrow();
		assertEquals(MediaJobState.PENDING, process.state());
		assertEquals(T0.plusSeconds(200), process.dueAt());
		assertEquals(0, process.attempts());
	}

	@Test
	void bodySlots_takeCountFreeAndExpire() {
		long first = inTransaction(() -> bodySlots.take(MediaTestRows.SALE, "instance-a", T0, T0.plusSeconds(660)));
		inTransaction(() -> bodySlots.take(MediaTestRows.SALE, "instance-b", T0, T0.plusSeconds(60)));

		assertEquals(2, inTransaction(() -> bodySlots.countLive(MediaTestRows.SALE, T0.plusSeconds(30))));
		assertEquals(1, inTransaction(() -> bodySlots.countLive(MediaTestRows.SALE, T0.plusSeconds(61))));
		inTransaction(() -> {
			bodySlots.free(first);
			return null;
		});
		assertEquals(0, inTransaction(() -> bodySlots.countLive(MediaTestRows.SALE, T0.plusSeconds(61))));
		assertEquals(1, inTransaction(() -> bodySlots.deleteExpired(T0.plusSeconds(61))));
	}

	@Test
	void counters_budgetUpsertStopsAtTheLimit_andExceededIsFirstTimeOnly() {
		Instant hour = Instant.parse("2026-11-01T02:00:00Z");

		OptionalLong one = inTransaction(() -> counters.countIfBelow(MediaTestRows.SALE, hour, MediaCounterKind.FULL,
				2));
		OptionalLong two = inTransaction(() -> counters.countIfBelow(MediaTestRows.SALE, hour, MediaCounterKind.FULL,
				2));
		OptionalLong refused = inTransaction(() -> counters.countIfBelow(MediaTestRows.SALE, hour,
				MediaCounterKind.FULL, 2));
		boolean firstMark = inTransaction(() -> counters.markExceededFirst(MediaTestRows.SALE, hour,
				MediaCounterKind.FULL, T0));
		boolean secondMark = inTransaction(() -> counters.markExceededFirst(MediaTestRows.SALE, hour,
				MediaCounterKind.FULL, T0.plusSeconds(5)));
		boolean sameInstant = inTransaction(() -> counters.markExceededFirst(MediaTestRows.SALE, hour,
				MediaCounterKind.FULL, T0));
		boolean otherKind = inTransaction(() -> counters.markExceededFirst(MediaTestRows.SALE, hour,
				MediaCounterKind.LIST, T0.plusSeconds(6)));

		assertEquals(OptionalLong.of(1), one);
		assertEquals(OptionalLong.of(2), two);
		assertTrue(refused.isEmpty());
		assertTrue(firstMark);
		assertFalse(secondMark);
		assertFalse(sameInstant, "a refusal at the same instant is no first refusal either");
		assertTrue(otherKind);
		assertEquals(T0, inTransaction(() -> jdbc.queryForObject("select exceeded_at from survey_media_read_budgets"
				+ " where user_id = ? and hour_start = ? and kind = 'FULL'", java.sql.Timestamp.class,
				MediaTestRows.SALE, java.sql.Timestamp.from(hour)).toInstant()));
		assertEquals(2L, inTransaction(() -> counters.counterValue(MediaTestRows.SALE, hour, MediaCounterKind.FULL)));
		assertEquals(0L, inTransaction(() -> counters.counterValue(MediaTestRows.SALE, hour, MediaCounterKind.LIST)));
		assertEquals(0L, inTransaction(() -> counters.counterValue(MediaTestRows.SALE, hour,
				MediaCounterKind.PREVIEW)));
	}

	@Test
	void counters_addAccumulatesProcessingMillisAndExpensiveOutcomes() {
		Instant hour = Instant.parse("2026-11-01T02:00:00Z");

		inTransaction(() -> {
			counters.add(MediaTestRows.SALE, hour, MediaCounterKind.PROC_MS, 1500);
			counters.add(MediaTestRows.SALE, hour, MediaCounterKind.PROC_MS, 2500);
			counters.add(MediaTestRows.SALE, hour, MediaCounterKind.EXPENSIVE, 1);
			return null;
		});

		assertEquals(4000L, inTransaction(() -> counters.counterValue(MediaTestRows.SALE, hour,
				MediaCounterKind.PROC_MS)));
		assertEquals(1L, inTransaction(() -> counters.counterValue(MediaTestRows.SALE, hour,
				MediaCounterKind.EXPENSIVE)));
	}

	@Test
	void counters_hourlyUsageCountsRecordsAndDeclaredBytesSince() {
		stored(photo(EvidenceCategory.EXTERIOR_ACCESS, MediaPlacement.asset()));
		stored(photo(EvidenceCategory.DETAIL, MediaPlacement.asset()));

		var usage = inTransaction(() -> counters.hourlyUsage(MediaTestRows.SALE, T0.minusSeconds(1)));
		var none = inTransaction(() -> counters.hourlyUsage(MediaTestRows.SALE, T0));

		assertEquals(2, usage.records());
		assertEquals(2000L, usage.bytes());
		assertEquals(T0, usage.oldestOrNull(), "the oldest record of the window gives the Retry-After of a 429");
		assertEquals(0, none.records());
		assertNull(none.oldestOrNull());
	}

	@Test
	void counters_userAdvisoryLockBlocksASecondTransaction() {
		inTransaction(() -> {
			counters.lockUser(MediaTestRows.SALE);
			probe(other -> assertFalse(tryAdvisory(other, MediaTestRows.SALE)));
			probe(other -> assertTrue(tryAdvisory(other, OTHER_SALE)));
			return null;
		});
		probe(other -> assertTrue(tryAdvisory(other, MediaTestRows.SALE)));
	}

	@Test
	void counters_recentListRowAndRecentEvent() {
		jdbc.update("INSERT INTO survey_access_logs (id, lead_id, user_id, accessed_at, resource_type, resource_id,"
				+ " request_id) VALUES (?, ?, ?, ?, 'MEDIA_LIST', ?, ?)", 9_900_001L, LEAD, MediaTestRows.SALE,
				java.sql.Timestamp.from(T0), checklist, "01JREQMED1A000000000000001");
		jdbc.update("INSERT INTO survey_security_events (id, user_id, lead_id, resource_type, resource_id, action,"
				+ " outcome, occurred_at) VALUES (?, ?, ?, 'MEDIA_SET', ?, 'READ', 'NOT_VISIBLE', ?)", 9_900_002L,
				OTHER_SALE, LEAD, checklist, java.sql.Timestamp.from(T0));

		assertTrue(inTransaction(() -> counters.hasRecentMediaListRow(MediaTestRows.SALE, checklist,
				T0.minusSeconds(600))));
		assertFalse(inTransaction(() -> counters.hasRecentMediaListRow(MediaTestRows.SALE, checklist, T0)));
		assertTrue(inTransaction(() -> {
			assertTrue(counters.tryLockEventKey(OTHER_SALE, checklist, SecurityAction.READ,
					SecurityOutcome.NOT_VISIBLE));
			// spec A-19: a second writer of the same key never waits: it gets false and skips its event
			probe(other -> assertFalse(tryEventKey(other, OTHER_SALE + "|" + checklist + "|READ|NOT_VISIBLE")));
			probe(other -> assertTrue(tryEventKey(other, OTHER_SALE + "|" + checklist + "|UPLOAD|NOT_VISIBLE")));
			return counters.hasRecentEvent(OTHER_SALE, checklist, SecurityAction.READ, SecurityOutcome.NOT_VISIBLE,
					T0.minusSeconds(60));
		}));
		assertFalse(inTransaction(() -> counters.hasRecentEvent(OTHER_SALE, checklist, SecurityAction.UPLOAD,
				SecurityOutcome.NOT_VISIBLE, T0.minusSeconds(60))));
	}

	// ==================== request id (SV-052) ====================

	@Test
	void accessLogAndEvent_requestIdRoundTrip() {
		inTransaction(() -> {
			accessLogs.save(SurveyAccessLog.create(9_800_001L, LEAD, MediaTestRows.SALE, T0,
					AccessResourceType.MEDIA_PREVIEW, "01JMED1AREQ000000000000001", "01JREQMED1A000000000000002"));
			securityEvents.save(new SurveySecurityEvent(9_800_002L, MediaTestRows.SALE, LEAD,
					SecurityResourceType.MEDIA, "01JMED1AREQ000000000000001", SecurityAction.HOLD,
					SecurityOutcome.HELD, "corr-1", T0, "01JREQMED1A000000000000003"));
			accessLogs.save(SurveyAccessLog.create(9_800_003L, LEAD, MediaTestRows.SALE, T0.plusSeconds(1)));
			return null;
		});

		List<SurveyAccessLog> logs = accessLogs.findByLeadId(LEAD);
		assertEquals("01JREQMED1A000000000000002", logs.get(0).requestIdOrNull());
		assertEquals(AccessResourceType.MEDIA_PREVIEW, logs.get(0).resourceTypeOrNull());
		assertNull(logs.get(1).requestIdOrNull());
		SurveySecurityEvent event = securityEvents.findByLeadId(LEAD).get(0);
		assertEquals("01JREQMED1A000000000000003", event.requestIdOrNull());
		assertEquals(SecurityOutcome.HELD, event.outcome());
	}

	// ==================== helpers ====================

	private SurveyMedia photo(EvidenceCategory category, MediaPlacement placement) {
		int n = ++sequence;
		return SurveyMedia.create(SurveyMediaId.generate(), checklist, String.format("01JUPKEYMED1A%013d", n), n,
				placement, category, n % 2 == 0 ? "Mặt tiền" : null, MediaTestRows.SALE, 1000L, sha(n), T0);
	}

	private SurveyMedia stored(SurveyMedia photo) {
		inTransaction(() -> {
			media.insert(photo);
			return null;
		});
		assertNotNull(photo.getVersion());
		return photo;
	}

	private SurveyMedia updated(SurveyMedia photo) {
		inTransaction(() -> {
			media.update(photo);
			return null;
		});
		return photo;
	}

	private SurveyMedia processing(SurveyMedia photo) {
		photo.claim("01JT0KENMED1A0000000000001", "quarantine/" + photo.getId() + "/1", T0.plusSeconds(60));
		photo.commit("quarantine/" + photo.getId() + "/1", photo.getDeclaredSizeBytes(), T0.plusSeconds(90));
		return updated(photo);
	}

	private SurveyMedia reload(SurveyMediaId id) {
		return inTransaction(() -> media.lockForUpdate(List.of(id))).get(0);
	}

	private static List<MediaVariant> variantsOf(SurveyMedia photo, int attempt) {
		return List.of(variant(photo, VariantKind.FULL, attempt, 4000, 3000),
				variant(photo, VariantKind.PREVIEW, attempt, 1600, 1200),
				variant(photo, VariantKind.THUMBNAIL, attempt, 400, 300));
	}

	private static MediaVariant variant(SurveyMedia photo, VariantKind kind, int attempt, int width, int height) {
		return new MediaVariant(kind, attempt, "media/" + photo.getId() + "/" + attempt + "/" + kind,
				sha(kind.ordinal() + 7000 + photo.getMediaNo() * 10), 2048L, width, height, "dev-8.15.1",
				T0.plusSeconds(110));
	}

	private static String sha(int n) {
		return String.format("%064x", n);
	}

	private static void assertSame(SurveyMedia expected, SurveyMedia actual) {
		assertEquals(expected.getId(), actual.getId());
		assertEquals(expected.getChecklistId(), actual.getChecklistId());
		assertEquals(expected.getUploadKey(), actual.getUploadKey());
		assertEquals(expected.getMediaNo(), actual.getMediaNo());
		assertEquals(expected.getPlacement(), actual.getPlacement());
		assertEquals(expected.getCategory(), actual.getCategory());
		assertEquals(expected.getCaption(), actual.getCaption());
		assertEquals(expected.getUploadedByUserId(), actual.getUploadedByUserId());
		assertEquals(expected.getCreatedAt(), actual.getCreatedAt());
		assertEquals(expected.getDeclaredSizeBytes(), actual.getDeclaredSizeBytes());
		assertEquals(expected.getSourceSha256(), actual.getSourceSha256());
		assertEquals(expected.getStatus(), actual.getStatus());
		assertEquals(expected.getStatusReason(), actual.getStatusReason());
		assertEquals(expected.getReceiveToken(), actual.getReceiveToken());
		assertEquals(expected.getReceiveKey(), actual.getReceiveKey());
		assertEquals(expected.getReceiveStartedAt(), actual.getReceiveStartedAt());
		assertEquals(expected.getContentReceivedAt(), actual.getContentReceivedAt());
		assertEquals(expected.getSourceStorageKey(), actual.getSourceStorageKey());
		assertEquals(expected.getSourceDeletedAt(), actual.getSourceDeletedAt());
		assertEquals(expected.getSourceFormat(), actual.getSourceFormat());
		assertEquals(expected.getReadyAt(), actual.getReadyAt());
		assertEquals(expected.getFinishedAt(), actual.getFinishedAt());
		assertEquals(expected.getRemoval(), actual.getRemoval());
		assertEquals(expected.getHold(), actual.getHold());
		assertEquals(expected.getContentDeleteRequestedAt(), actual.getContentDeleteRequestedAt());
		assertEquals(expected.getContentDeletedAt(), actual.getContentDeletedAt());
		assertEquals(expected.getContentMissingAt(), actual.getContentMissingAt());
		assertEquals(expected.getVariants(), actual.getVariants());
		assertEquals(expected.getVersion(), actual.getVersion());
	}

	private <T> T inTransaction(Supplier<T> work) {
		return new TransactionTemplate(transactionManager).execute(status -> work.get());
	}

	private void probe(Consumer<Connection> check) {
		try (Connection other = dataSource.getConnection()) {
			other.setAutoCommit(false);
			check.accept(other);
			other.rollback();
		} catch (SQLException e) {
			throw new IllegalStateException(e);
		}
	}

	private static boolean tryAdvisory(Connection connection, String userId) {
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT pg_try_advisory_xact_lock(?, hashtext(?))")) {
			statement.setInt(1, vn.sunvibe.survey.core.infrastructure.persistence.adapter.MediaSql.USER_LOCK_NAMESPACE);
			statement.setString(2, userId);
			try (var result = statement.executeQuery()) {
				result.next();
				return result.getBoolean(1);
			}
		} catch (SQLException e) {
			throw new SqlFailure(e);
		}
	}

	private static void execute(Connection connection, String sql, String id) {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, id);
			statement.executeQuery().close();
		} catch (SQLException e) {
			throw new SqlFailure(e);
		}
	}

	/** An unchecked carrier of the SQL failure, so the probe lambdas can assert its state. */
	private static final class SqlFailure extends RuntimeException {
		SqlFailure(SQLException cause) {
			super(cause);
		}

		String sqlState() {
			return ((SQLException) getCause()).getSQLState();
		}
	}
}
