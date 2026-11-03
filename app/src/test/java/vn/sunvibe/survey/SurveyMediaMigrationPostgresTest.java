package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The survey-media migrations (survey-media spec 5.2, 7.4; SM-35) on a real PostgreSQL holding W15/W16 data. The
 * database is migrated to the W16 schema, filled with rows of every existing enum value of the survey tables (leads,
 * appointments with revisions and events, checklists with groups, saves with corrections, takeovers of every kind,
 * access-log rows with and without a resource, security events), then migrated to the last media-a version. Every
 * constraint must end validated, every old row unchanged, each widened CHECK an exact superset of the old one and the
 * previous server's inserts still accepted. The new tables' CHECKs are NULL-safe: each wrong row is refused (spec 5.2).
 * Needs Docker, like {@code FlywayPostgresMigrationTest}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SurveyMediaMigrationPostgresTest {

	/** The W16 schema: the last version before survey-media. */
	private static final String W16_VERSION = "202610241731";
	/** The last media-a migration (spec 5.2 file 5); pinned so a later migration cannot change what is tested here. */
	private static final String LAST_MEDIA_VERSION = "202611021404";
	private static final String MEDIA_SCRIPTS = "classpath*:db/migration/V20261102140?__*.sql";

	private static final List<String> MEDIA_TABLES = List.of("survey_media_sets", "survey_media",
			"survey_media_variants", "survey_media_jobs", "survey_media_health", "survey_media_incidents",
			"survey_media_body_slots", "survey_media_read_budgets", "survey_media_listing_runs");
	/** The tables of W15/W16 and the acquisition rows they name; the media migrations must leave their rows as they are. */
	private static final List<String> OLD_TABLES = List.of("consent_records", "owner_leads", "survey_appointments",
			"survey_appointment_revisions", "survey_appointment_events", "survey_resumptions", "survey_checklists",
			"survey_checklist_groups", "survey_checklist_saves", "survey_checklist_takeovers", "survey_access_logs",
			"survey_security_events");

	/** Constraint name to the values the media migrations add (spec 5.2 "Widened CHECKs"). */
	private static final Map<String, List<String>> WIDENED = new LinkedHashMap<>();

	static {
		WIDENED.put("chk_survey_security_events_resource_type", List.of("MEDIA", "MEDIA_SET"));
		WIDENED.put("chk_survey_security_events_action", List.of("UPLOAD", "REMOVE", "SCAN", "HOLD", "RELEASE"));
		WIDENED.put("chk_survey_security_events_outcome",
				List.of("MALWARE_DETECTED", "HELD", "RELEASED", "BUDGET_EXCEEDED"));
		WIDENED.put("chk_survey_access_logs_resource_type",
				List.of("MEDIA_LIST", "MEDIA_PREVIEW", "MEDIA_FULL", "MEDIA_DOWNLOAD", "MEDIA_HELD_READ"));
	}

	/** Spec 5.2: the columns listed as NULL; every other column of the media tables is NOT NULL. */
	private static final Map<String, Set<String>> NULLABLE = new LinkedHashMap<>();

	static {
		NULLABLE.put("survey_media_sets", Set.of());
		NULLABLE.put("survey_media", Set.of("space_id", "space_code_at_upload", "caption", "status_reason",
				"receive_token", "receive_key", "receive_started_at", "content_received_at", "source_storage_key",
				"source_deleted_at", "source_format", "ready_at", "finished_at", "removed_at", "removed_by_user_id",
				"removal_kind", "removal_correction_reason", "removal_correction_basis_on",
				"removal_correction_basis_text", "removed_by_save_id", "held_at", "held_by_user_id", "hold_reason",
				"hold_released_at", "hold_released_by_user_id", "content_delete_requested_at", "content_deleted_at",
				"content_missing_at"));
		NULLABLE.put("survey_media_variants", Set.of());
		NULLABLE.put("survey_media_jobs", Set.of("lease_token", "last_error_code"));
		NULLABLE.put("survey_media_health", Set.of("last_sweep_ok_at", "last_probe_ok_at", "last_failure_at",
				"last_worker_beat_at", "last_deletion_beat_at", "scan_blocked_since", "intake_paused_since",
				"pause_reason"));
		NULLABLE.put("survey_media_incidents", Set.of("resolved_at"));
		NULLABLE.put("survey_media_body_slots", Set.of());
		NULLABLE.put("survey_media_read_budgets", Set.of("exceeded_at"));
		NULLABLE.put("survey_media_listing_runs", Set.of("lease_token", "lease_until", "start_after_key",
				"finished_at"));
	}

	private static final List<String> STATUSES = List.of("AWAITING_CONTENT", "RECEIVING", "PROCESSING", "READY",
			"REJECTED", "FAILED", "CANCELLED", "UPLOAD_EXPIRED");
	private static final List<String> REJECTED_REASONS = List.of("UNSUPPORTED_FORMAT", "UNREADABLE", "ANIMATED",
			"MOTION_PHOTO", "COMPOSITE_FILE", "TOO_MANY_PIXELS", "MALWARE_DETECTED", "SCAN_LIMIT_EXCEEDED");
	/** Status to the reasons it accepts (spec 5.2 "status and reason pairs exactly"); an empty list: reason NULL. */
	private static final Map<String, List<String>> REASONS_OF = new LinkedHashMap<>();

	static {
		REASONS_OF.put("AWAITING_CONTENT", List.of());
		REASONS_OF.put("RECEIVING", List.of());
		REASONS_OF.put("PROCESSING", List.of());
		REASONS_OF.put("READY", List.of());
		REASONS_OF.put("REJECTED", REJECTED_REASONS);
		REASONS_OF.put("FAILED", List.of("PROCESSING_UNAVAILABLE"));
		REASONS_OF.put("CANCELLED", List.of("REMOVED"));
		REASONS_OF.put("UPLOAD_EXPIRED", List.of("CONTENT_NOT_RECEIVED"));
	}

	private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 11, 1, 9, 0, 0, 0, ZoneOffset.UTC);
	private static final String SALE = "01MIGSALE00000000000000001";
	private static final String OTHER_SALE = "01MIGSALE00000000000000002";
	private static final String ADMIN = "01MIGADMIN0000000000000001";
	private static final String LEAD_1 = "01MIGLEAD00000000000000001";
	private static final String LEAD_2 = "01MIGLEAD00000000000000002";
	private static final String LEAD_3 = "01MIGLEAD00000000000000003";
	private static final String VISIT_1 = "01MIGAPPT00000000000000101";
	private static final String VISIT_2 = "01MIGAPPT00000000000000102";
	private static final String CHECKLIST_1 = "01MIGCHKL00000000000000001";
	private static final String CHECKLIST_2 = "01MIGCHKL00000000000000002";
	private static final String READY_MEDIA = "01MIGMEDIA0000000000000000";

	private final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.6-alpine"));
	private final AtomicInteger sequence = new AtomicInteger(1000);
	private JdbcTemplate jdbc;
	private Map<String, List<Map<String, Object>>> oldRowsBefore;
	/** The same tables read right after the migration, before any test adds rows. */
	private Map<String, List<Map<String, Object>>> oldRowsAfter;
	private Map<String, String> widenedDefinitionsBefore;

	@BeforeAll
	void migrateW16DatabaseWithRowsThenMedia() {
		postgres.start();
		DriverManagerDataSource dataSource = new DriverManagerDataSource(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		jdbc = new JdbcTemplate(dataSource);

		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(W16_VERSION).load()
				.migrate();
		insertW16Rows();
		oldRowsBefore = oldRows();
		widenedDefinitionsBefore = widenedDefinitions();

		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(LAST_MEDIA_VERSION)
				.load().migrate();
		oldRowsAfter = oldRows();

		jdbc.update("INSERT INTO survey_media_sets (checklist_id, set_version, last_media_no, updated_at)"
				+ " VALUES (?, 0, 0, ?)", CHECKLIST_1, T0);
		Map<String, Object> ready = media("READY");
		ready.put("id", READY_MEDIA);
		insert("survey_media", ready);
	}

	@AfterAll
	void stop() {
		postgres.stop();
	}

	// ==================== populated data (spec 5.2 "Migration test on populated data") ====================

	@Test
	void migrate_populatedW16Schema_keepsRowsAndValidatesEveryConstraint() {
		assertEquals(List.of(), jdbc.queryForList("SELECT conrelid::regclass::text || '.' || conname"
				+ " FROM pg_constraint WHERE NOT convalidated", String.class));

		Map<String, List<Map<String, Object>>> after = oldRowsAfter;
		for (String table : List.of("survey_access_logs", "survey_security_events")) {
			for (Map<String, Object> row : after.get(table)) {
				assertTrue(row.containsKey("request_id"), table + " has no request_id");
				assertNull(row.remove("request_id"), table + ": an old row got a request id");
			}
		}
		assertEquals(oldRowsBefore, after);
		for (String table : OLD_TABLES) {
			assertTrue(!oldRowsBefore.get(table).isEmpty(), "no row in " + table + ": the comparison proves nothing");
		}
	}

	@Test
	void widenedChecks_areExactSupersets() {
		Map<String, String> after = widenedDefinitions();
		for (Map.Entry<String, List<String>> widened : WIDENED.entrySet()) {
			String name = widened.getKey();
			Set<String> expected = new TreeSet<>(quotedValues(widenedDefinitionsBefore.get(name)));
			assertTrue(Collections.disjoint(expected, widened.getValue()), name + " already held a new value");
			expected.addAll(widened.getValue());

			assertEquals(expected, new TreeSet<>(quotedValues(after.get(name))), name);
		}
		assertTrue(after.get("chk_survey_access_logs_resource_type").contains("resource_type IS NULL"),
				"the NULL resource of old access-log rows is no longer allowed");
		assertEquals(widenedDefinitionsBefore.keySet(), after.keySet());
	}

	@Test
	void previousServerColumnLists_stillInsert() {
		// The column lists of the 75ff460 entities: the previous server keeps running during the roll-out (spec 7.4)
		jdbc.update("INSERT INTO survey_access_logs (id, lead_id, user_id, accessed_at, resource_type, resource_id)"
				+ " VALUES (?, ?, ?, ?, 'CHECKLIST', ?)", nextId(), LEAD_1, SALE, T0, CHECKLIST_1);
		jdbc.update("INSERT INTO survey_access_logs (id, lead_id, user_id, accessed_at, resource_type, resource_id)"
				+ " VALUES (?, ?, ?, ?, NULL, NULL)", nextId(), LEAD_1, SALE, T0);
		jdbc.update("INSERT INTO survey_security_events (id, user_id, lead_id, resource_type, resource_id, action,"
				+ " outcome, correlation_id, occurred_at) VALUES (?, ?, ?, 'CHECKLIST', ?, 'SAVE', 'NOT_ASSIGNEE', 'c-1', ?)",
				nextId(), SALE, LEAD_1, CHECKLIST_1, T0);
	}

	@Test
	void mediaValues_insertIntoTheSharedTables() {
		for (String type : WIDENED.get("chk_survey_access_logs_resource_type")) {
			jdbc.update("INSERT INTO survey_access_logs (id, lead_id, user_id, accessed_at, resource_type, resource_id,"
					+ " request_id) VALUES (?, ?, ?, ?, ?, ?, ?)", nextId(), LEAD_1, SALE, T0, type, READY_MEDIA,
					"01MIGREQUEST00000000000001");
		}
		for (String type : WIDENED.get("chk_survey_security_events_resource_type")) {
			for (String action : WIDENED.get("chk_survey_security_events_action")) {
				for (String outcome : WIDENED.get("chk_survey_security_events_outcome")) {
					jdbc.update("INSERT INTO survey_security_events (id, user_id, lead_id, resource_type, resource_id,"
							+ " action, outcome, correlation_id, occurred_at, request_id)"
							+ " VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)", nextId(), SALE, LEAD_1, type, READY_MEDIA,
							action, outcome, T0, "01MIGREQUEST00000000000002");
				}
			}
		}
	}

	@Test
	void healthRow_seededPausedNotStarted() {
		List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM survey_media_health");

		assertEquals(1, rows.size());
		Map<String, Object> row = rows.get(0);
		assertEquals((short) 1, ((Number) row.get("id")).shortValue());
		assertEquals("NOT_STARTED", row.get("pause_reason"));
		assertNotNull(row.get("intake_paused_since"));
		assertEquals(0L, ((Number) row.get("failure_seq")).longValue());
		assertEquals(0L, ((Number) row.get("state_epoch")).longValue());
		for (String column : List.of("last_sweep_ok_at", "last_probe_ok_at", "last_failure_at",
				"last_worker_beat_at", "last_deletion_beat_at", "scan_blocked_since")) {
			assertNull(row.get(column), column);
		}
	}

	// ==================== NULL-safe CHECKs of survey_media ====================

	@Test
	void validRowsOfEveryStatus_insert() {
		for (String status : STATUSES) {
			for (String reason : REASONS_OF.get(status).isEmpty() ? Collections.<String>singletonList(null)
					: REASONS_OF.get(status)) {
				Map<String, Object> row = media(status);
				row.put("status_reason", reason);
				assertEquals(null, sqlStateOfInsert("survey_media", row), status + " " + reason);
			}
		}
	}

	@Test
	void statusReasonCheck_refusesEveryWrongPair() {
		for (String status : STATUSES) {
			if (!REASONS_OF.get(status).isEmpty()) {
				Map<String, Object> row = media(status);
				row.put("status_reason", null);
				assertRefused(row, status + " without a reason");
			}
			for (List<String> reasons : REASONS_OF.values()) {
				for (String reason : reasons) {
					if (!REASONS_OF.get(status).contains(reason)) {
						Map<String, Object> row = media(status);
						row.put("status_reason", reason);
						assertRefused(row, status + " with " + reason);
					}
				}
			}
		}
		Map<String, Object> unknown = media("REJECTED");
		unknown.put("status_reason", "BLURRY");
		assertRefused(unknown, "unknown reason");
		Map<String, Object> cancelledNotRemoved = media("CANCELLED");
		cancelledNotRemoved.put("removed_at", null);
		cancelledNotRemoved.put("removed_by_user_id", null);
		cancelledNotRemoved.put("removal_kind", null);
		assertRefused(cancelledNotRemoved, "CANCELLED without a removal");
	}

	@Test
	void valueChecks_refuseUnknownValues() {
		assertRefused(with(media("AWAITING_CONTENT"), "status", "UPLOADED"), "status");
		assertRefused(with(media("AWAITING_CONTENT"), "category", "ROOF"), "category");
		assertRefused(with(media("READY"), "source_format", "GIF"), "source_format");
		assertRefused(with(media("CANCELLED"), "removal_kind", "ADMIN"), "removal_kind");
		Map<String, Object> held = media("READY");
		held.putAll(hold("ABUSIVE_CONTENT"));
		assertRefused(held, "hold_reason");
		assertRefused(with(media("AWAITING_CONTENT"), "media_no", 0), "media_no 0");
		assertRefused(with(media("AWAITING_CONTENT"), "version", -1L), "version -1");
		assertRefused(with(media("AWAITING_CONTENT"), "declared_size_bytes", 0L), "size 0");
		assertRefused(with(media("AWAITING_CONTENT"), "declared_size_bytes", 20_971_521L), "size above 20 MiB");
		assertEquals(null, sqlStateOfInsert("survey_media",
				with(media("AWAITING_CONTENT"), "declared_size_bytes", 20_971_520L)), "20 MiB exactly");
		assertRefused(with(media("AWAITING_CONTENT"), "source_sha256", "A".repeat(64)), "upper-case hash");
		assertRefused(with(media("AWAITING_CONTENT"), "source_sha256", "a".repeat(63)), "short hash");
	}

	@Test
	void lifecycleChecks_refuseBrokenRows() {
		assertRefused(with(media("RECEIVING"), "receive_token", null), "RECEIVING without a token");
		assertRefused(with(media("RECEIVING"), "receive_key", null), "RECEIVING without a key");
		assertRefused(with(media("RECEIVING"), "receive_started_at", null), "RECEIVING without a start");
		assertRefused(with(media("AWAITING_CONTENT"), "receive_token", "01MIGTOKEN0000000000000001"),
				"a token outside RECEIVING");
		assertRefused(with(media("PROCESSING"), "receive_started_at", T0), "a receive start outside RECEIVING");
		Map<String, Object> processingWithoutReceipt = media("PROCESSING");
		processingWithoutReceipt.put("content_received_at", null);
		processingWithoutReceipt.put("source_storage_key", null);
		assertRefused(processingWithoutReceipt, "PROCESSING without receipt");
		Map<String, Object> awaitingWithReceipt = media("AWAITING_CONTENT");
		awaitingWithReceipt.put("content_received_at", T0);
		awaitingWithReceipt.put("source_storage_key", "quarantine/x");
		assertRefused(awaitingWithReceipt, "a receipt while AWAITING_CONTENT");
		assertRefused(with(media("PROCESSING"), "source_storage_key", null), "a receipt without a source key");
		assertRefused(with(media("UPLOAD_EXPIRED"), "source_deleted_at", T0), "a source deletion without a source");
		assertRefused(with(media("READY"), "ready_at", null), "READY without ready_at");
		assertRefused(with(media("PROCESSING"), "ready_at", T0), "ready_at outside READY");
		assertRefused(with(media("READY"), "source_format", null), "READY without a format");
		assertRefused(with(media("READY"), "finished_at", null), "READY without finished_at");
		assertRefused(with(media("UPLOAD_EXPIRED"), "finished_at", null), "UPLOAD_EXPIRED without finished_at");
		assertRefused(with(media("PROCESSING"), "finished_at", T0), "finished_at before an outcome");
	}

	@Test
	void placementCheck_refusesAssetCategoryWithSpace() {
		for (String asset : List.of("EXTERIOR_ACCESS", "SHARED_KITCHEN", "SHARED_BATHROOM", "SHARED_AMENITY")) {
			assertRefused(onSpace(with(media("AWAITING_CONTENT"), "category", asset)), asset + " on a space");
			assertEquals(null, sqlStateOfInsert("survey_media", with(media("AWAITING_CONTENT"), "category", asset)),
					asset + " on the asset");
		}
		for (String space : List.of("MAIN_SPACE", "KITCHEN", "BATHROOM")) {
			assertRefused(with(media("AWAITING_CONTENT"), "category", space), space + " on the asset");
			assertEquals(null, sqlStateOfInsert("survey_media", onSpace(with(media("AWAITING_CONTENT"), "category",
					space))), space + " on a space");
		}
		assertEquals(null, sqlStateOfInsert("survey_media", with(media("AWAITING_CONTENT"), "category", "DETAIL")));
		assertEquals(null, sqlStateOfInsert("survey_media",
				onSpace(with(media("AWAITING_CONTENT"), "category", "DETAIL"))));
		assertRefused(with(onSpace(with(media("AWAITING_CONTENT"), "category", "KITCHEN")), "space_code_at_upload",
				null), "a space without its code");
		assertRefused(with(media("AWAITING_CONTENT"), "space_code_at_upload", "P101"), "a code without a space");
	}

	@Test
	void removalAndHoldChecks_refuseHalfSetColumns() {
		Map<String, Object> removed = removed(media("READY"), "HOLDER");
		assertEquals(null, sqlStateOfInsert("survey_media", copy(removed)), "a valid holder removal");
		assertRefused(with(copy(removed), "removed_by_user_id", null), "removed without a remover");
		assertRefused(with(copy(removed), "removal_kind", null), "removed without a kind");
		assertRefused(with(media("READY"), "removed_by_user_id", SALE), "a remover without a removal");
		assertRefused(with(media("READY"), "removal_kind", "HOLDER"), "a kind without a removal");
		Map<String, Object> corrected = withCorrection(copy(removed));
		assertEquals(null, sqlStateOfInsert("survey_media", copy(corrected)), "a valid corrected removal");
		assertRefused(with(copy(corrected), "removal_correction_basis_on", null), "a correction without its date");
		assertRefused(with(copy(corrected), "removal_correction_basis_text", null), "a correction without its text");
		assertRefused(with(copy(corrected), "removal_correction_reason", null), "a correction without its reason");
		assertRefused(withCorrection(media("READY")), "a correction without a removal");
		Map<String, Object> cascade = with(removed(media("READY"), "CASCADE"), "removed_by_save_id", 77L);
		assertEquals(null, sqlStateOfInsert("survey_media", copy(cascade)), "a valid cascade removal");
		assertRefused(with(copy(cascade), "removed_by_save_id", null), "a cascade without its save");
		assertRefused(with(copy(removed), "removed_by_save_id", 77L), "a save on a holder removal");
		assertRefused(withCorrection(copy(cascade)), "a cascade carrying its own correction");

		Map<String, Object> held = media("READY");
		held.putAll(hold("SECURITY_INCIDENT"));
		assertEquals(null, sqlStateOfInsert("survey_media", copy(held)), "a valid hold");
		assertRefused(with(copy(held), "held_by_user_id", null), "held without an actor");
		assertRefused(with(copy(held), "hold_reason", null), "held without a reason");
		assertRefused(with(media("READY"), "hold_reason", "SECURITY_INCIDENT"), "a reason without a hold");
		Map<String, Object> released = with(with(copy(held), "hold_released_at", T0.plusMinutes(5)),
				"hold_released_by_user_id", ADMIN);
		assertEquals(null, sqlStateOfInsert("survey_media", copy(released)), "a valid release");
		assertEquals(null, sqlStateOfInsert("survey_media", with(copy(released), "hold_released_at",
				T0.plusMinutes(5))), "a release at the hold time");
		assertRefused(with(copy(released), "hold_released_by_user_id", null), "a release without an actor");
		assertRefused(with(copy(held), "hold_released_by_user_id", ADMIN), "a release actor without a time");
		assertRefused(with(copy(released), "hold_released_at", T0.plusMinutes(4)), "a release before the hold");
		Map<String, Object> releasedNeverHeld = with(with(media("READY"), "hold_released_at", T0),
				"hold_released_by_user_id", ADMIN);
		assertRefused(releasedNeverHeld, "a release without a hold");
		Map<String, Object> heldAwaiting = media("AWAITING_CONTENT");
		heldAwaiting.putAll(hold("SECURITY_INCIDENT"));
		assertRefused(heldAwaiting, "a hold before content was received");
	}

	@Test
	void deletionCheck_heldRemovedReadyWithRequest_isRefused() {
		Map<String, Object> heldRemoved = removed(media("READY"), "HOLDER");
		heldRemoved.putAll(hold("SECURITY_INCIDENT"));
		heldRemoved.put("content_delete_requested_at", null);
		assertEquals(null, sqlStateOfInsert("survey_media", copy(heldRemoved)), "held and removed, no request");
		assertRefused(with(copy(heldRemoved), "content_delete_requested_at", T0), "held and removed with a request");

		Map<String, Object> removedReady = removed(media("READY"), "HOLDER");
		assertRefused(with(copy(removedReady), "content_delete_requested_at", null), "removed READY without request");
		assertRefused(with(media("READY"), "content_delete_requested_at", T0), "a request on a selected photo");
		assertRefused(with(removed(media("CANCELLED"), "HOLDER"), "content_delete_requested_at", T0),
				"a request on a CANCELLED photo");
		Map<String, Object> releasedRemoved = removed(media("READY"), "HOLDER");
		releasedRemoved.putAll(hold("CREDENTIAL_EXPOSURE"));
		releasedRemoved.put("hold_released_at", T0.plusMinutes(6));
		releasedRemoved.put("hold_released_by_user_id", ADMIN);
		assertEquals(null, sqlStateOfInsert("survey_media", copy(releasedRemoved)), "released and removed, requested");
		assertRefused(with(copy(releasedRemoved), "content_delete_requested_at", null),
				"released and removed without request");

		assertRefused(with(media("READY"), "content_deleted_at", T0), "deleted without a request");
		assertEquals(null, sqlStateOfInsert("survey_media", with(copy(removedReady), "content_deleted_at", T0)),
				"deleted after its request");
		assertEquals(null, sqlStateOfInsert("survey_media", with(media("READY"), "content_missing_at", T0)),
				"missing on READY");
		assertRefused(with(media("REJECTED"), "content_missing_at", T0), "missing on a photo that is not READY");
	}

	@Test
	void uniqueKeys_refuseSecondNumberKeyOrSelectedHash() {
		Map<String, Object> first = media("AWAITING_CONTENT");
		insert("survey_media", first);

		assertEquals("23505", sqlStateOfInsert("survey_media", with(media("AWAITING_CONTENT"), "media_no",
				first.get("media_no"))), "media_no twice");
		assertEquals("23505", sqlStateOfInsert("survey_media", with(media("AWAITING_CONTENT"), "upload_key",
				first.get("upload_key"))), "upload key twice for one uploader");
		assertEquals(null, sqlStateOfInsert("survey_media", with(with(media("AWAITING_CONTENT"), "upload_key",
				first.get("upload_key")), "uploaded_by_user_id", OTHER_SALE)), "the key of another uploader");
		assertEquals("23505", sqlStateOfInsert("survey_media", with(media("PROCESSING"), "source_sha256",
				first.get("source_sha256"))), "a second selected photo with one hash");
		assertEquals(null, sqlStateOfInsert("survey_media", with(media("REJECTED"), "source_sha256",
				first.get("source_sha256"))), "a not accepted photo with the hash");
		assertEquals(null, sqlStateOfInsert("survey_media", with(removed(media("READY"), "HOLDER"), "source_sha256",
				first.get("source_sha256"))), "a removed photo with the hash");
	}

	@Test
	void variantChecks_refuseWrongValues() {
		assertEquals(null, sqlStateOfInsert("survey_media_variants", variant("FULL", 12_582_912L)), "FULL at its cap");
		assertRefused("survey_media_variants", variant("PREVIEW", 2_097_153L), "PREVIEW above 2 MiB");
		assertRefused("survey_media_variants", variant("THUMBNAIL", 262_145L), "THUMBNAIL above 256 KiB");
		assertRefused("survey_media_variants", variant("FULL", 12_582_913L), "FULL above 12 MiB");
		assertRefused("survey_media_variants", variant("ORIGINAL", 10L), "variant");
		assertRefused("survey_media_variants", with(variant("PREVIEW", 10L), "size_bytes", 0L), "size 0");
		assertRefused("survey_media_variants", with(variant("PREVIEW", 10L), "attempt", 0), "attempt 0");
		assertRefused("survey_media_variants", with(variant("PREVIEW", 10L), "width", 0), "width 0");
		assertRefused("survey_media_variants", with(variant("PREVIEW", 10L), "height", 0), "height 0");
		assertRefused("survey_media_variants", with(variant("PREVIEW", 10L), "sha256", "x".repeat(64)), "hash");
		assertRefused("survey_media_variants", with(variant("PREVIEW", 10L), "processor_version", "-dev"),
				"version starting with a sign");
		assertRefused("survey_media_variants", with(variant("PREVIEW", 10L), "processor_version", "dev 8"),
				"version with a space");
		assertEquals(null, sqlStateOfInsert("survey_media_variants", with(variant("PREVIEW", 10L),
				"processor_version", "sha256:abc/def@x+y-z.1")), "a digest-like version");
	}

	@Test
	void jobChecks_refuseWrongValues() {
		assertEquals(null, sqlStateOfInsert("survey_media_jobs", job("PROCESS")));
		assertEquals("23505", sqlStateOfInsert("survey_media_jobs", job("PROCESS")), "two jobs of one kind");
		assertRefused("survey_media_jobs", job("RESIZE"), "kind");
		assertRefused("survey_media_jobs", with(job("DELETE_CONTENT"), "state", "RUNNING"), "state");
		assertRefused("survey_media_jobs", with(job("DELETE_CONTENT"), "attempts", -1), "attempts");
		assertRefused("survey_media_jobs", with(job("DELETE_CONTENT"), "limit_hits", -1), "limit hits");
	}

	@Test
	void listingRunChecks_refuseTokenWithoutLeaseAndFinishedWithLease() {
		assertRefused("survey_media_listing_runs", with(listingRun(), "lease_token", "01MIGLEASE0000000000000001"),
				"a token without a lease time");
		assertRefused("survey_media_listing_runs", with(listingRun(), "lease_until", T0), "a lease without a token");
		Map<String, Object> finishedWithLease = listingRun();
		finishedWithLease.put("lease_token", "01MIGLEASE0000000000000001");
		finishedWithLease.put("lease_until", T0.plusMinutes(5));
		finishedWithLease.put("finished_at", T0.plusMinutes(1));
		assertRefused("survey_media_listing_runs", finishedWithLease, "finished while leased");
		assertRefused("survey_media_listing_runs", with(listingRun(), "finished_at", T0.minusMinutes(1)),
				"finished before it started");
		assertRefused("survey_media_listing_runs", with(listingRun(), "keys_seen", -1L), "keys seen");
		assertRefused("survey_media_listing_runs", with(listingRun(), "undeleted_count", -1), "undeleted count");
		Map<String, Object> finished = with(listingRun(), "finished_at", T0.plusHours(1));
		finished.remove("id");
		insert("survey_media_listing_runs", finished);
		Map<String, Object> open = listingRun();
		open.remove("id");
		assertEquals(null, sqlStateOfInsert("survey_media_listing_runs", copy(open)), "one open run");
		assertEquals("23505", sqlStateOfInsert("survey_media_listing_runs", copy(open)), "a second open run");
		jdbc.update("DELETE FROM survey_media_listing_runs");
	}

	@Test
	void incidents_secondOpenOfOneKind_isRefused() {
		Map<String, Object> incident = incident("ORPHAN_OVERDUE");
		incident.remove("id");
		assertEquals(null, sqlStateOfInsert("survey_media_incidents", copy(incident)));
		assertEquals("23505", sqlStateOfInsert("survey_media_incidents", copy(incident)), "a second open incident");
		assertEquals(null, sqlStateOfInsert("survey_media_incidents", with(copy(incident), "resolved_at",
				T0.plusHours(1))), "a resolved one beside it");
		assertRefused("survey_media_incidents", with(copy(incident), "resolved_at", T0.minusHours(1)),
				"resolved before it opened");
		assertRefused("survey_media_incidents", with(incident("LOST_OBJECT"), "id", 99L), "kind");
		assertRefused("survey_media_incidents", with(incident("SOURCE_OVERDUE"), "media_count", 0), "count 0");
		jdbc.update("DELETE FROM survey_media_incidents");
	}

	@Test
	void health_secondRow_isRefused() {
		assertRefused("survey_media_health", with(health(), "id", (short) 2), "a row other than 1");
		assertEquals("23505", sqlStateOfInsert("survey_media_health", health()), "a second row 1");
		assertRefused("survey_media_health", with(with(health(), "id", (short) 3), "pause_reason", "STORE"),
				"a reason without a pause");
		jdbc.update("UPDATE survey_media_health SET pause_reason = 'UNKNOWN' WHERE false");
		assertEquals("23514", sqlStateOf(() -> jdbc.update(
				"UPDATE survey_media_health SET intake_paused_since = NULL WHERE id = 1")), "a pause without its time");
		assertEquals("23514", sqlStateOf(() -> jdbc.update(
				"UPDATE survey_media_health SET pause_reason = 'TIRED' WHERE id = 1")), "an unknown reason");
		for (String reason : List.of("NOT_STARTED", "STORE", "SCANNER", "PROCESSOR", "SWEEP_STALE", "WORKER_STALE",
				"DELETION_STALE", "PROCESSING_OFF", "DELETION_OVERDUE")) {
			assertEquals(null, sqlStateOf(() -> jdbc.update(
					"UPDATE survey_media_health SET pause_reason = ? WHERE id = 1", reason)), reason);
		}
		jdbc.update("UPDATE survey_media_health SET pause_reason = 'NOT_STARTED' WHERE id = 1");
	}

	@Test
	void readBudgetChecks_refuseWrongValues() {
		assertEquals(null, sqlStateOfInsert("survey_media_read_budgets", budget("PROC_MS")));
		assertEquals("23505", sqlStateOfInsert("survey_media_read_budgets", budget("PROC_MS")), "one row per hour");
		assertRefused("survey_media_read_budgets", budget("ORIGINAL"), "kind");
		assertRefused("survey_media_read_budgets", with(budget("LIST"), "read_count", -1), "count");
		jdbc.update("DELETE FROM survey_media_read_budgets");
	}

	@Test
	void everyNotNullColumn_refusesNull() {
		Map<String, Map<String, Object>> validRows = new LinkedHashMap<>();
		validRows.put("survey_media_sets", set());
		validRows.put("survey_media", media("AWAITING_CONTENT"));
		validRows.put("survey_media_variants", variant("FULL", 10L));
		validRows.put("survey_media_jobs", with(job("PROCESS"), "id", 99L));
		validRows.put("survey_media_health", with(health(), "id", (short) 1));
		validRows.put("survey_media_incidents", incident("SOURCE_OVERDUE"));
		validRows.put("survey_media_body_slots", bodySlot());
		validRows.put("survey_media_read_budgets", budget("FULL"));
		validRows.put("survey_media_listing_runs", listingRun());

		for (String table : MEDIA_TABLES) {
			List<Map<String, Object>> columns = jdbc.queryForList("SELECT column_name, is_nullable"
					+ " FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?", table);
			Set<String> nullable = new TreeSet<>();
			for (Map<String, Object> column : columns) {
				String name = (String) column.get("column_name");
				if ("YES".equals(column.get("is_nullable"))) {
					nullable.add(name);
				} else {
					assertEquals("23502", sqlStateOfInsert(table, with(copy(validRows.get(table)), name, null)),
							table + "." + name + " accepted NULL");
				}
			}
			assertEquals(new TreeSet<>(NULLABLE.get(table)), nullable, table + ": nullable columns");
			assertEquals(new TreeSet<>(validRows.get(table).keySet()),
					new TreeSet<>(columns.stream().map(c -> (String) c.get("column_name")).toList()),
					table + ": the test's valid row does not name every column");
		}
	}

	@Test
	void constraintAndIndexNames_areAtMost63BytesAndPresent() throws IOException {
		Set<String> expected = new TreeSet<>();
		Pattern constraint = Pattern.compile("CONSTRAINT\\s+(?!IF\\b)(\\w+)");
		Pattern index = Pattern.compile(
				"CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+(?:CONCURRENTLY\\s+)?(?:IF\\s+NOT\\s+EXISTS\\s+)?(\\w+)");
		Resource[] scripts = new PathMatchingResourcePatternResolver().getResources(MEDIA_SCRIPTS);
		assertEquals(5, scripts.length, "the five media-a migrations");
		for (Resource script : scripts) {
			String sql = script.getContentAsString(StandardCharsets.UTF_8).replaceAll("--[^\\n]*", "");
			for (Pattern pattern : List.of(constraint, index)) {
				Matcher matcher = pattern.matcher(sql);
				while (matcher.find()) {
					expected.add(matcher.group(1));
				}
			}
		}
		for (String name : expected) {
			assertTrue(name.getBytes(StandardCharsets.UTF_8).length <= 63, name + " is longer than 63 bytes");
		}

		Set<String> present = new TreeSet<>(jdbc.queryForList("SELECT conname FROM pg_constraint"
				+ " WHERE contype IN ('c', 'f', 'p', 'u', 'x') AND (conrelid::regclass::text = ANY (?::text[])"
				+ " OR conname = ANY (?::text[]))", String.class, MEDIA_TABLES.toArray(String[]::new),
				WIDENED.keySet().toArray(String[]::new)));
		present.addAll(jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname = 'public'"
				+ " AND (tablename = ANY (?::text[]) OR indexname = 'idx_survey_access_logs_media_list')", String.class,
				(Object) MEDIA_TABLES.toArray(String[]::new)));
		assertEquals(expected, present);
		assertTrue(present.contains("idx_survey_access_logs_media_list"), "the MEDIA_LIST index is missing");
		assertEquals(List.of(), jdbc.queryForList("SELECT indexrelid::regclass::text FROM pg_index"
				+ " WHERE NOT indisvalid OR NOT indisready", String.class), "an invalid index");
	}

	// ==================== row builders ====================

	/** A row of every column of survey_media, valid for the status (spec 5.2), on CHECKLIST_1. */
	private Map<String, Object> media(String status) {
		int n = sequence.incrementAndGet();
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", String.format("01MIGMEDIA%016d", n));
		row.put("checklist_id", CHECKLIST_1);
		row.put("upload_key", String.format("01MIGUPKEY%016d", n));
		row.put("media_no", n);
		row.put("version", 0L);
		row.put("space_id", null);
		row.put("space_code_at_upload", null);
		row.put("category", "EXTERIOR_ACCESS");
		row.put("caption", null);
		row.put("uploaded_by_user_id", SALE);
		row.put("created_at", T0);
		row.put("declared_size_bytes", 1000L);
		row.put("source_sha256", String.format("%064x", n));
		row.put("status", status);
		row.put("status_reason", REASONS_OF.get(status).isEmpty() ? null : REASONS_OF.get(status).get(0));
		boolean receiving = status.equals("RECEIVING");
		row.put("receive_token", receiving ? "01MIGTOKEN0000000000000001" : null);
		row.put("receive_key", receiving ? "quarantine/" + n + "/1" : null);
		row.put("receive_started_at", receiving ? T0.plusMinutes(1) : null);
		boolean received = List.of("PROCESSING", "READY", "REJECTED", "FAILED", "CANCELLED").contains(status);
		row.put("content_received_at", received ? T0.plusMinutes(2) : null);
		row.put("source_storage_key", received ? "quarantine/" + n + "/1" : null);
		row.put("source_deleted_at", received && !status.equals("PROCESSING") ? T0.plusMinutes(4) : null);
		row.put("source_format", status.equals("READY") ? "JPEG" : null);
		row.put("ready_at", status.equals("READY") ? T0.plusMinutes(3) : null);
		boolean finished = List.of("READY", "REJECTED", "FAILED", "CANCELLED", "UPLOAD_EXPIRED").contains(status);
		row.put("finished_at", finished ? T0.plusMinutes(3) : null);
		boolean cancelled = status.equals("CANCELLED");
		row.put("removed_at", cancelled ? T0.plusMinutes(3) : null);
		row.put("removed_by_user_id", cancelled ? SALE : null);
		row.put("removal_kind", cancelled ? "HOLDER" : null);
		row.put("removal_correction_reason", null);
		row.put("removal_correction_basis_on", null);
		row.put("removal_correction_basis_text", null);
		row.put("removed_by_save_id", null);
		row.put("held_at", null);
		row.put("held_by_user_id", null);
		row.put("hold_reason", null);
		row.put("hold_released_at", null);
		row.put("hold_released_by_user_id", null);
		row.put("content_delete_requested_at", null);
		row.put("content_deleted_at", null);
		row.put("content_missing_at", null);
		return row;
	}

	/** A holder or cascade removal; a READY photo gets its deletion request (spec 4.7). */
	private static Map<String, Object> removed(Map<String, Object> row, String kind) {
		row.put("removed_at", T0.plusMinutes(10));
		row.put("removed_by_user_id", SALE);
		row.put("removal_kind", kind);
		if ("READY".equals(row.get("status"))) {
			row.put("content_delete_requested_at", T0.plusMinutes(10));
		}
		return row;
	}

	private static Map<String, Object> withCorrection(Map<String, Object> row) {
		row.put("removal_correction_reason", "Sai vị trí");
		row.put("removal_correction_basis_on", LocalDate.of(2026, 10, 31));
		row.put("removal_correction_basis_text", "Chủ nhà xác nhận");
		return row;
	}

	private static Map<String, Object> hold(String reason) {
		Map<String, Object> hold = new LinkedHashMap<>();
		hold.put("held_at", T0.plusMinutes(5));
		hold.put("held_by_user_id", ADMIN);
		hold.put("hold_reason", reason);
		return hold;
	}

	private static Map<String, Object> onSpace(Map<String, Object> row) {
		row.put("space_id", "01MIGSPACE0000000000000001");
		row.put("space_code_at_upload", "P101");
		return row;
	}

	private static Map<String, Object> set() {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("checklist_id", CHECKLIST_2);
		row.put("set_version", 0L);
		row.put("last_media_no", 0);
		row.put("updated_at", T0);
		return row;
	}

	private Map<String, Object> variant(String kind, long size) {
		int n = sequence.incrementAndGet();
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("media_id", READY_MEDIA);
		row.put("variant", kind);
		row.put("attempt", 1);
		row.put("storage_key", "media/" + READY_MEDIA + "/1/" + kind + "-" + n);
		row.put("sha256", String.format("%064x", n));
		row.put("size_bytes", size);
		row.put("width", 100);
		row.put("height", 80);
		row.put("processor_version", "dev-8.15.1");
		row.put("created_at", T0);
		return row;
	}

	private static Map<String, Object> job(String kind) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("media_id", READY_MEDIA);
		row.put("kind", kind);
		row.put("state", "DONE");
		row.put("attempts", 1);
		row.put("limit_hits", 0);
		row.put("lease_token", null);
		row.put("due_at", T0);
		row.put("last_error_code", null);
		row.put("created_at", T0);
		row.put("updated_at", T0);
		return row;
	}

	private static Map<String, Object> health() {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", (short) 1);
		row.put("failure_seq", 0L);
		row.put("state_epoch", 0L);
		for (String column : List.of("last_sweep_ok_at", "last_probe_ok_at", "last_failure_at", "last_worker_beat_at",
				"last_deletion_beat_at", "scan_blocked_since", "intake_paused_since", "pause_reason")) {
			row.put(column, null);
		}
		return row;
	}

	private static Map<String, Object> incident(String kind) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", 99L);
		row.put("kind", kind);
		row.put("opened_at", T0);
		row.put("first_overdue_at", T0);
		row.put("media_count", 1);
		row.put("resolved_at", null);
		return row;
	}

	private static Map<String, Object> bodySlot() {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", 99L);
		row.put("user_id", SALE);
		row.put("instance_id", "01MIGINSTANCE0000000000001");
		row.put("taken_at", T0);
		row.put("expires_at", T0.plusMinutes(11));
		return row;
	}

	private static Map<String, Object> budget(String kind) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("user_id", SALE);
		row.put("hour_start", T0);
		row.put("kind", kind);
		row.put("read_count", 0);
		row.put("exceeded_at", null);
		return row;
	}

	private static Map<String, Object> listingRun() {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", 99L);
		row.put("started_at", T0);
		row.put("lease_token", null);
		row.put("lease_until", null);
		row.put("start_after_key", null);
		row.put("keys_seen", 0L);
		row.put("undeleted_count", 0);
		row.put("finished_at", null);
		return row;
	}

	private static Map<String, Object> with(Map<String, Object> row, String column, Object value) {
		row.put(column, value);
		return row;
	}

	/** A copy; a photo row gets a fresh id, number, upload key and hash, so each copy can be inserted. */
	private Map<String, Object> copy(Map<String, Object> row) {
		Map<String, Object> copy = new LinkedHashMap<>(row);
		if (copy.containsKey("media_no")) {
			Map<String, Object> fresh = media("AWAITING_CONTENT");
			for (String column : List.of("id", "media_no", "upload_key", "source_sha256")) {
				copy.put(column, fresh.get(column));
			}
		}
		return copy;
	}

	// ==================== W15/W16 rows of every existing enum value ====================

	private void insertW16Rows() {
		for (String lead : List.of(LEAD_1, LEAD_2, LEAD_3)) {
			insertLead(lead);
		}
		// Lead 1: a first visit (OWN record, two revisions), a re-survey (PROXY record) and a scheduled re-survey
		insert("survey_appointments", visited(appointment(VISIT_1, LEAD_1, 1, "SCHEDULED"), "OWN", SALE));
		Map<String, Object> resurvey = visited(appointment(VISIT_2, LEAD_1, 2, "SCHEDULED"), "PROXY", OTHER_SALE);
		resurvey.put("kind", "RESURVEY");
		resurvey.put("previous_visit_id", VISIT_1);
		resurvey.put("resurvey_reason", "Thiếu ảnh mặt tiền");
		insert("survey_appointments", resurvey);
		Map<String, Object> scheduled = appointment("01MIGAPPT00000000000000103", LEAD_1, 3, "SCHEDULED");
		scheduled.put("kind", "RESURVEY");
		scheduled.put("previous_visit_id", VISIT_2);
		scheduled.put("resurvey_reason", "Đo lại");
		scheduled.put("intent", "SHORT_TERM_RENT");
		insert("survey_appointments", unconfirmed(scheduled, "NEW"));
		// Lead 2: one cancelled appointment per reason, unconfirmed by each cause in turn
		List<String> causes = List.of("NEW", "TIME_CHANGED", "LOCATION_CHANGED", "REASSIGNED", "USER");
		List<String> cancelReasons = List.of("OWNER_CANCELLED", "STAFF_CANCELLED", "OWNER_ABSENT", "NO_ACCESS", "OTHER",
				"LEAD_ON_HOLD", "LEAD_CLOSED", "INTENT_CHANGED");
		for (int i = 0; i < cancelReasons.size(); i++) {
			Map<String, Object> cancelled = appointment(String.format("01MIGAPPT%017d", 201 + i), LEAD_2, i + 1,
					"CANCELLED");
			cancelled.put("cancel_reason", cancelReasons.get(i));
			cancelled.put("cancel_description", "Lý do " + i);
			cancelled.put("cancelled_by", SALE);
			cancelled.put("cancelled_at", T0.minusDays(1));
			insert("survey_appointments", unconfirmed(cancelled, causes.get(i % causes.size())));
		}
		// Lead 3: a confirmed first appointment without pins or notes
		Map<String, Object> plain = appointment("01MIGAPPT00000000000000301", LEAD_3, 1, "SCHEDULED");
		for (String column : List.of("meeting_latitude", "meeting_longitude", "lead_latitude", "lead_longitude",
				"access_note", "on_site_contact_name", "on_site_contact_role")) {
			plain.put(column, null);
		}
		plain.put("location_reason", "Gặp ở quán cà phê");
		insert("survey_appointments", plain);

		for (String appointmentId : jdbc.queryForList("SELECT id FROM survey_appointments ORDER BY id", String.class)) {
			jdbc.update("INSERT INTO survey_appointment_revisions (id, appointment_id, revision_no, valid_from,"
					+ " start_at, duration_minutes, meeting_address, meeting_province_code, meeting_province_name,"
					+ " meeting_ward_code, meeting_ward_name, meeting_latitude, meeting_longitude, access_note,"
					+ " on_site_contact_name, on_site_contact_role, reason, actor_user_id)"
					+ " VALUES (?, ?, 1, ?, ?, 60, '1 Bạch Đằng', '48', 'Đà Nẵng', '20242', 'Hải Châu', 16.067800,"
					+ " 108.220800, 'Bấm chuông', 'Anh Tuấn', 'Bảo vệ', NULL, ?)",
					nextId(), appointmentId, T0.minusDays(3), T0.minusDays(2), SALE);
		}
		jdbc.update("INSERT INTO survey_appointment_revisions (id, appointment_id, revision_no, valid_from, start_at,"
				+ " duration_minutes, meeting_address, meeting_province_code, meeting_province_name, meeting_ward_code,"
				+ " meeting_ward_name, meeting_latitude, meeting_longitude, access_note, on_site_contact_name,"
				+ " on_site_contact_role, reason, actor_user_id) VALUES (?, ?, 2, ?, ?, 90, '2 Bạch Đằng', '48',"
				+ " 'Đà Nẵng', '20242', 'Hải Châu', NULL, NULL, NULL, NULL, NULL, 'Chủ nhà đổi giờ', ?)",
				nextId(), VISIT_1, T0.minusDays(2), T0.minusDays(1), SALE);

		List<String> eventTypes = List.of("BOOKED", "CHANGED", "CONFIRMED", "UNCONFIRMED", "SURVEYOR_CHANGED",
				"CANCELLED", "VISITED");
		for (int i = 0; i < eventTypes.size(); i++) {
			String type = eventTypes.get(i);
			boolean holder = type.equals("BOOKED") || type.equals("SURVEYOR_CHANGED");
			jdbc.update("INSERT INTO survey_appointment_events (id, appointment_id, type, actor_user_id, occurred_at,"
					+ " revision_no, from_surveyor_id, to_surveyor_id, confirmation_cause, note)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", nextId(), VISIT_1, type, i == 3 ? null : SALE,
					T0.minusDays(3).plusMinutes(i), type.equals("CHANGED") ? 2 : null,
					type.equals("SURVEYOR_CHANGED") ? OTHER_SALE : null, holder ? SALE : null,
					type.equals("UNCONFIRMED") ? causes.get(i % causes.size()) : null, i % 2 == 0 ? "Ghi chú" : null);
		}
		for (String cause : causes) {
			jdbc.update("INSERT INTO survey_appointment_events (id, appointment_id, type, actor_user_id, occurred_at,"
					+ " confirmation_cause) VALUES (?, ?, 'UNCONFIRMED', ?, ?, ?)", nextId(), VISIT_2, SALE, T0, cause);
		}
		jdbc.update("INSERT INTO survey_resumptions (id, lead_id, visit_appointment_id, reason, basis_confirmed_on,"
				+ " basis_text, actor_user_id, occurred_at) VALUES (?, ?, ?, 'Chủ nhà đồng ý', ?, 'Gọi điện', ?, ?)",
				nextId(), LEAD_1, VISIT_1, LocalDate.of(2026, 10, 30), SALE, T0);

		insertChecklist(CHECKLIST_1, 1, VISIT_1, SALE);
		insertChecklist(CHECKLIST_2, 2, VISIT_2, OTHER_SALE);
		jdbc.update("INSERT INTO survey_checklist_saves (id, checklist_id, group_key, group_version, actor_user_id,"
				+ " saved_at, correction_reason, correction_basis_confirmed_on, correction_basis_text, corrected_units)"
				+ " VALUES (?, ?, 'IDENTIFICATION', 1, ?, ?, NULL, NULL, NULL, NULL)", nextId(), CHECKLIST_1, SALE, T0);
		jdbc.update("INSERT INTO survey_checklist_saves (id, checklist_id, group_key, group_version, actor_user_id,"
				+ " saved_at, correction_reason, correction_basis_confirmed_on, correction_basis_text, corrected_units)"
				+ " VALUES (?, ?, 'PARTS', 2, ?, ?, 'Sửa số phòng', ?, 'Chủ nhà xác nhận',"
				+ " '[{\"unitKey\":\"spaces/01MIGSPACE0000000000000001\",\"spaceCode\":\"P101\"}]')",
				nextId(), CHECKLIST_2, OTHER_SALE, T0, LocalDate.of(2026, 10, 31));
		jdbc.update("INSERT INTO survey_checklist_takeovers (id, checklist_id, takeover_no, kind, from_user_id,"
				+ " to_user_id, reason, basis_confirmed_on, basis_text, snapshot, occurred_at)"
				+ " VALUES (?, ?, 1, 'START_BY_OTHER', NULL, ?, 'Người khảo sát nghỉ', ?, 'Trưởng nhóm giao', NULL, ?)",
				nextId(), CHECKLIST_2, OTHER_SALE, LocalDate.of(2026, 10, 30), T0);
		long takeoverWithSnapshot = nextId();
		jdbc.update("INSERT INTO survey_checklist_takeovers (id, checklist_id, takeover_no, kind, from_user_id,"
				+ " to_user_id, reason, basis_confirmed_on, basis_text, snapshot, occurred_at)"
				+ " VALUES (?, ?, 2, 'TAKEOVER', ?, ?, 'Đổi người', ?, 'Trưởng nhóm giao', '{\"schema\":2}', ?)",
				takeoverWithSnapshot, CHECKLIST_2, OTHER_SALE, SALE, LocalDate.of(2026, 10, 31), T0.plusMinutes(1));
		jdbc.update("INSERT INTO survey_checklist_takeovers (id, checklist_id, takeover_no, kind, from_user_id,"
				+ " to_user_id, reason, basis_confirmed_on, basis_text, snapshot, occurred_at)"
				+ " VALUES (?, ?, 3, 'TAKEOVER', ?, ?, 'Đổi lại', ?, 'Trưởng nhóm giao', NULL, ?)",
				nextId(), CHECKLIST_2, SALE, OTHER_SALE, LocalDate.of(2026, 10, 31), T0.plusMinutes(2));

		jdbc.update("INSERT INTO survey_access_logs (id, lead_id, user_id, accessed_at) VALUES (?, ?, ?, ?)",
				nextId(), LEAD_3, SALE, T0);
		jdbc.update("INSERT INTO survey_access_logs (id, lead_id, user_id, accessed_at, resource_type, resource_id)"
				+ " VALUES (?, ?, ?, ?, 'SURVEY_BLOCK', ?)", nextId(), LEAD_1, SALE, T0, LEAD_1);
		jdbc.update("INSERT INTO survey_access_logs (id, lead_id, user_id, accessed_at, resource_type, resource_id)"
				+ " VALUES (?, ?, ?, ?, 'CHECKLIST', ?)", nextId(), LEAD_1, SALE, T0, CHECKLIST_1);
		jdbc.update("INSERT INTO survey_access_logs (id, lead_id, user_id, accessed_at, resource_type, resource_id)"
				+ " VALUES (?, ?, ?, ?, 'CHECKPOINT', ?)", nextId(), LEAD_1, ADMIN, T0,
				Long.toString(takeoverWithSnapshot));
		int n = 0;
		for (String type : List.of("LEAD", "APPOINTMENT", "CHECKLIST", "CHECKPOINT")) {
			for (String action : List.of("READ", "START", "SAVE", "TAKEOVER")) {
				for (String outcome : List.of("NOT_VISIBLE", "NOT_ASSIGNEE")) {
					jdbc.update("INSERT INTO survey_security_events (id, user_id, lead_id, resource_type, resource_id,"
							+ " action, outcome, correlation_id, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
							nextId(), OTHER_SALE, LEAD_1, type, CHECKLIST_1, action, outcome,
							n++ % 2 == 0 ? null : "corr-" + n, T0);
				}
			}
		}
	}

	private void insertLead(String leadId) {
		String consentId = "01MIGCONSENT" + leadId.substring(12);
		jdbc.update("INSERT INTO consent_records (id, notice_version, purpose, channel, accepted_at)"
				+ " VALUES (?, 'owner-intake-2026-10', 'OWNER_INTAKE', 'WEB', ?)", consentId, T0.minusDays(10));
		jdbc.update("INSERT INTO owner_leads (id, request_code, status, intent, source, contact_name,"
				+ " phone_normalized, email, province_code, province_name, ward_code, ward_name, address_detail,"
				+ " address_key, latitude, longitude, property_type, asking_price_amount, asking_price_unit,"
				+ " available_from, notes, preferred_callback_time, consent_record_id, submission_snapshot,"
				+ " assigned_user_id, next_action_at, duplicate_of_lead_id, close_reason, created_at, updated_at,"
				+ " version, intent_cycle)"
				+ " VALUES (?, ?, 'SURVEY_SCHEDULED', 'LONG_TERM_RENT', 'WEB', 'Chủ nhà', ?, 'chu.nha@example.vn', '48',"
				+ " 'Đà Nẵng', '20242', 'Hải Châu', '1 Bạch Đằng', ?, 16.067800, 108.220800, 'APARTMENT', 15000000,"
				+ " 'MONTH', DATE '2026-12-01', 'Có nội thất', 'Buổi tối', ?, '{}', ?, ?, NULL, NULL, ?, ?, 3, 1)",
				leadId, "SV-MIGM-000" + leadId.charAt(25), "+849000000" + leadId.charAt(25), "1 bach dang " + leadId,
				consentId, SALE, T0.plusDays(1), T0.minusDays(10), T0.minusDays(1));
	}

	private Map<String, Object> appointment(String id, String leadId, int no, String status) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", id);
		row.put("lead_id", leadId);
		row.put("appointment_no", no);
		row.put("kind", "FIRST");
		row.put("previous_visit_id", null);
		row.put("resurvey_reason", null);
		row.put("intent", "LONG_TERM_RENT");
		row.put("intent_cycle", 1);
		row.put("status", status);
		row.put("start_at", T0.minusDays(2));
		row.put("duration_minutes", 60);
		row.put("meeting_address", "1 Bạch Đằng");
		row.put("meeting_province_code", "48");
		row.put("meeting_province_name", "Đà Nẵng");
		row.put("meeting_ward_code", "20242");
		row.put("meeting_ward_name", "Hải Châu");
		row.put("meeting_latitude", new BigDecimal("16.067800"));
		row.put("meeting_longitude", new BigDecimal("108.220800"));
		row.put("access_note", "Bấm chuông");
		row.put("on_site_contact_name", "Anh Tuấn");
		row.put("on_site_contact_role", "Bảo vệ");
		row.put("current_revision_no", 1);
		row.put("lead_address_detail", "1 Bạch Đằng");
		row.put("lead_province_code", "48");
		row.put("lead_province_name", "Đà Nẵng");
		row.put("lead_ward_code", "20242");
		row.put("lead_ward_name", "Hải Châu");
		row.put("lead_latitude", new BigDecimal("16.067800"));
		row.put("lead_longitude", new BigDecimal("108.220800"));
		row.put("location_reason", null);
		row.put("surveyor_user_id", SALE);
		row.put("confirmation_state", "CONFIRMED");
		row.put("confirmation_cause", null);
		row.put("ever_confirmed", true);
		row.put("confirmation_changed_by", SALE);
		row.put("confirmation_changed_at", T0.minusDays(3));
		row.put("confirmation_note", "Đã gọi");
		row.put("created_by", SALE);
		row.put("created_at", T0.minusDays(3));
		row.put("updated_at", T0.minusDays(1));
		row.put("version", 2L);
		return row;
	}

	private static Map<String, Object> visited(Map<String, Object> row, String recordKind, String performedBy) {
		row.put("status", "VISITED");
		row.put("visit_record_kind", recordKind);
		row.put("performed_by", performedBy);
		row.put("arrived_at", T0.minusDays(2));
		row.put("visit_revision_no", 1);
		row.put("visit_note", "Đã khảo sát");
		row.put("visit_recorded_by", "OWN".equals(recordKind) ? performedBy : SALE);
		row.put("visit_recorded_at", T0.minusDays(2).plusHours(1));
		if ("PROXY".equals(recordKind)) {
			row.put("proxy_reason", "Ghi hộ");
			row.put("basis_confirmed_on", LocalDate.of(2026, 10, 29));
			row.put("basis_text", "Tin nhắn Zalo");
		}
		return row;
	}

	private static Map<String, Object> unconfirmed(Map<String, Object> row, String cause) {
		row.put("confirmation_state", "UNCONFIRMED");
		row.put("confirmation_cause", cause);
		row.put("ever_confirmed", false);
		row.put("confirmation_note", null);
		return row;
	}

	private void insertChecklist(String id, int no, String visitId, String holder) {
		jdbc.update("INSERT INTO survey_checklists (id, lead_id, checklist_no, visit_appointment_id, intent_cycle,"
				+ " holder_user_id, template_code, template_version, kind, status, created_by, created_at, version)"
				+ " VALUES (?, ?, ?, ?, 1, ?, 'LONG_TERM_RENT', 1, 'FULL_ASSESSMENT', 'DRAFT', ?, ?, ?)",
				id, LEAD_1, no, visitId, holder, holder, T0.minusDays(1), (long) no);
		int i = 0;
		for (String group : List.of("IDENTIFICATION", "PARTS", "DIMENSIONS", "CONDITION", "SAFETY", "AMENITIES",
				"TERMS")) {
			boolean saved = i++ % 2 == 0;
			jdbc.update("INSERT INTO survey_checklist_groups (checklist_id, group_key, content, version, updated_by,"
					+ " updated_at) VALUES (?, ?, ?, ?, ?, ?)", id, group,
					saved ? "{\"fields\":{\"note\":{\"value\":\"x\"}}}" : "{}", saved ? 1L : 0L,
					saved ? holder : null, saved ? T0 : null);
		}
	}

	// ==================== helpers ====================

	private void insert(String table, Map<String, Object> row) {
		jdbc.update("INSERT INTO " + table + " (" + String.join(", ", row.keySet()) + ") VALUES ("
				+ String.join(", ", Collections.nCopies(row.size(), "?")) + ")", row.values().toArray());
	}

	/** The SQLSTATE of the insert, or null when it succeeded. */
	private String sqlStateOfInsert(String table, Map<String, Object> row) {
		return sqlStateOf(() -> insert(table, row));
	}

	private static String sqlStateOf(Runnable statement) {
		try {
			statement.run();
			return null;
		} catch (DataAccessException e) {
			Throwable cause = e;
			while (cause != null && !(cause instanceof SQLException)) {
				cause = cause.getCause();
			}
			return cause == null ? e.toString() : ((SQLException) cause).getSQLState();
		}
	}

	private void assertRefused(Map<String, Object> row, String what) {
		assertRefused("survey_media", row, what);
	}

	private void assertRefused(String table, Map<String, Object> row, String what) {
		assertEquals("23514", sqlStateOfInsert(table, row), what + " was not refused by a CHECK");
	}

	private long nextId() {
		return sequence.incrementAndGet();
	}

	private Map<String, List<Map<String, Object>>> oldRows() {
		Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<>();
		for (String table : OLD_TABLES) {
			rows.put(table, new ArrayList<>(jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1, 2")));
		}
		return rows;
	}

	private Map<String, String> widenedDefinitions() {
		Map<String, String> definitions = new LinkedHashMap<>();
		for (String name : WIDENED.keySet()) {
			definitions.put(name, jdbc.queryForObject(
					"SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?", String.class, name));
		}
		return definitions;
	}

	private static Set<String> quotedValues(String definition) {
		Set<String> values = new LinkedHashSet<>();
		Matcher matcher = Pattern.compile("'([A-Z_]+)'").matcher(definition);
		while (matcher.find()) {
			values.add(matcher.group(1));
		}
		return values;
	}
}
