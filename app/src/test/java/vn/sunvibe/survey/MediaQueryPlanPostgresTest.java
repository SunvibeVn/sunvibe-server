package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import vn.sunvibe.survey.core.infrastructure.persistence.support.MediaSql;

/**
 * The sweep, clean-up, job and grouping queries use their partial indexes (survey-media spec 5.2; plan Task A4, SM-35
 * EXPLAIN): each query of the adapters is prepared under {@code plan_cache_mode = force_generic_plan} with
 * {@code enable_seqscan = off}, so an empty table cannot hide a missing index, and its plan must name the index. A
 * partial index is usable only when the query repeats its predicate as literals, never as bound parameters.
 */
class MediaQueryPlanPostgresTest extends MediaApiTestBase {

	private static final Pattern NAMED = Pattern.compile(":([a-zA-Z]+)");
	private static final Map<String, String> VALUES = Map.of(
			"cutoff", "'2026-11-01T00:00:00Z'",
			"now", "'2026-11-01T00:00:00Z'",
			"since", "'2026-11-01T00:00:00Z'",
			"limit", "100",
			"kind", "'PROCESS'",
			"userId", "'01JSA1EMED1A00000000000001'",
			"checklistId", "'01JCHECKMED1A0000000000001'");

	@Autowired
	private DataSource dataSource;

	@Test
	void expireAwaiting_usesPartialCreatedAtIndex() {
		assertUses(MediaSql.FIND_AWAITING_CREATED_BEFORE, "idx_survey_media_awaiting_created");
	}

	@Test
	void staleReceiving_usesPartialReceiveIndex() {
		assertUses(MediaSql.FIND_RECEIVING_STARTED_BEFORE, "idx_survey_media_receiving_started");
	}

	@Test
	void processingOverdue_usesPartialIndex() {
		assertUses(MediaSql.FIND_PROCESSING_RECEIVED_BEFORE, "idx_survey_media_processing_received");
	}

	@Test
	void sourceNotDeleted_usesPartialIndex() {
		assertUses(MediaSql.FIND_SOURCES_NOT_DELETED_BEFORE, "idx_survey_media_source_pending");
	}

	@Test
	void deletionDue_usesPartialIndex() {
		assertUses(MediaSql.FIND_DELETIONS_REQUESTED_BEFORE, "idx_survey_media_deletion_due");
	}

	@Test
	void expiredRecordsCleanup_usesPartialIndex() {
		assertUses(MediaSql.FIND_EXPIRED_FINISHED_BEFORE, "idx_survey_media_expired_finished");
	}

	@Test
	void pendingJobs_usesPartialKindDueIndex() {
		assertUses(MediaSql.FIND_PENDING_JOBS_DUE, "idx_survey_media_jobs_pending_kind_due");
	}

	@Test
	void mediaListRow_usesPartialIndex() {
		assertUses(MediaSql.HAS_RECENT_MEDIA_LIST_ROW, "idx_survey_access_logs_media_list");
	}

	private void assertUses(String namedSql, String index) {
		List<String> names = new ArrayList<>();
		Map<String, Integer> positions = new LinkedHashMap<>();
		Matcher matcher = NAMED.matcher(namedSql);
		StringBuilder prepared = new StringBuilder();
		while (matcher.find()) {
			String name = matcher.group(1);
			Integer position = positions.get(name);
			if (position == null) {
				names.add(name);
				position = names.size();
				positions.put(name, position);
			}
			matcher.appendReplacement(prepared, "\\$" + position);
		}
		matcher.appendTail(prepared);
		List<String> arguments = names.stream().map(name -> {
			String value = VALUES.get(name);
			if (value == null) {
				throw new IllegalArgumentException("no test value for :" + name);
			}
			return value;
		}).toList();

		String plan = explain(prepared.toString(), arguments);

		assertTrue(plan.contains(index), "expected " + index + " in the generic plan of\n" + namedSql + "\n" + plan);
	}

	private String explain(String sql, List<String> arguments) {
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (Statement statement = connection.createStatement()) {
				statement.execute("SET LOCAL enable_seqscan = off");
				statement.execute("SET LOCAL plan_cache_mode = force_generic_plan");
				statement.execute("PREPARE media_plan AS " + sql);
				StringBuilder plan = new StringBuilder();
				try (ResultSet rows = statement.executeQuery("EXPLAIN EXECUTE media_plan("
						+ String.join(", ", arguments) + ")")) {
					while (rows.next()) {
						plan.append(rows.getString(1)).append('\n');
					}
				}
				statement.execute("DEALLOCATE media_plan");
				return plan.toString();
			} finally {
				connection.rollback();
			}
		} catch (SQLException e) {
			throw new IllegalStateException(e);
		}
	}
}
