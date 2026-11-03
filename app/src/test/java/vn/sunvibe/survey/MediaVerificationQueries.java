package vn.sunvibe.survey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The verification queries of survey-media spec 5.2 (V1..V14; SM-35), each expecting no rows. V1..V8 look at the given
 * checklists only (other test classes write rows in deliberately partial shapes); V9, V10 and V12..V14 look at the
 * whole database; V11 (W16 rows unchanged by a sweep or deletion run) is MediaSweeperPostgresTest
 * .w16RowsUntouchedBySweepAndDeletion.
 */
final class MediaVerificationQueries {

	private static final String SELECTED = "('AWAITING_CONTENT', 'RECEIVING', 'PROCESSING', 'READY')";
	private static final String HELD = "(m.held_at IS NOT NULL AND m.hold_released_at IS NULL)";

	private MediaVerificationQueries() {
	}

	/** The named queries for the checklists (an SQL list of quoted ids). */
	static Map<String, String> queries(String checklists) {
		String scope = "m.checklist_id IN (" + checklists + ")";
		Map<String, String> queries = new LinkedHashMap<>();
		queries.put("V1 more than 100 selected or 20 pending", "SELECT m.checklist_id FROM survey_media m WHERE "
				+ scope + " GROUP BY m.checklist_id HAVING count(*) FILTER (WHERE m.removed_at IS NULL AND m.status IN "
				+ SELECTED + ") > 100 OR count(*) FILTER (WHERE m.removed_at IS NULL AND m.status IN"
				+ " ('AWAITING_CONTENT', 'RECEIVING', 'PROCESSING')) > 20");
		queries.put("V2 two selected photos with one hash", "SELECT m.checklist_id FROM survey_media m WHERE " + scope
				+ " AND m.removed_at IS NULL AND m.status IN " + SELECTED + " GROUP BY m.checklist_id, m.source_sha256"
				+ " HAVING count(*) > 1");
		queries.put("V3 READY without three variants, or variants of a photo not READY", "SELECT m.id FROM"
				+ " survey_media m WHERE " + scope + " AND m.status = 'READY' AND (SELECT count(*) FROM"
				+ " survey_media_variants v WHERE v.media_id = m.id) <> 3 UNION SELECT m.id FROM survey_media m JOIN"
				+ " survey_media_variants v ON v.media_id = m.id WHERE " + scope + " AND m.status <> 'READY'");
		queries.put("V4 PROCESSING without a pending PROCESS job, or the reverse", "SELECT m.id FROM survey_media m"
				+ " WHERE " + scope + " AND m.status = 'PROCESSING' AND NOT EXISTS (SELECT 1 FROM survey_media_jobs j"
				+ " WHERE j.media_id = m.id AND j.kind = 'PROCESS' AND j.state = 'PENDING') UNION SELECT m.id FROM"
				+ " survey_media m JOIN survey_media_jobs j ON j.media_id = m.id WHERE " + scope + " AND j.kind ="
				+ " 'PROCESS' AND j.state = 'PENDING' AND m.status <> 'PROCESSING'");
		queries.put("V5 deletion overdue, removed READY not held without a job, held with a request or job",
				"SELECT m.id FROM survey_media m WHERE " + scope + " AND m.content_delete_requested_at < now() -"
				+ " interval '24 hours' AND m.content_deleted_at IS NULL UNION SELECT m.id FROM survey_media m WHERE "
				+ scope + " AND m.removed_at IS NOT NULL AND m.status = 'READY' AND NOT " + HELD + " AND NOT EXISTS"
				+ " (SELECT 1 FROM survey_media_jobs j WHERE j.media_id = m.id AND j.kind = 'DELETE_CONTENT') UNION"
				+ " SELECT m.id FROM survey_media m WHERE " + scope + " AND " + HELD + " AND (m.content_delete_requested_at"
				+ " IS NOT NULL OR EXISTS (SELECT 1 FROM survey_media_jobs j WHERE j.media_id = m.id AND j.kind ="
				+ " 'DELETE_CONTENT' AND j.state = 'PENDING'))");
		queries.put("V6 a source not deleted 60 minutes after receipt", "SELECT m.id FROM survey_media m WHERE " + scope
				+ " AND m.source_storage_key IS NOT NULL AND m.source_deleted_at IS NULL AND m.content_received_at <"
				+ " now() - interval '60 minutes'");
		queries.put("V7 UPLOAD_EXPIRED 25 hours past finished_at", "SELECT m.id FROM survey_media m WHERE " + scope
				+ " AND m.status = 'UPLOAD_EXPIRED' AND m.finished_at < now() - interval '25 hours'");
		queries.put("V8 last_media_no below a photo's number", "SELECT s.checklist_id FROM survey_media_sets s JOIN"
				+ " survey_media m ON m.checklist_id = s.checklist_id WHERE " + scope + " GROUP BY s.checklist_id,"
				+ " s.last_media_no HAVING max(m.media_no) > s.last_media_no");
		queries.put("V9 not one health row, two open incidents of a kind, two open listing runs", "SELECT 'health'"
				+ " WHERE (SELECT count(*) FROM survey_media_health) <> 1 UNION SELECT kind FROM survey_media_incidents"
				+ " WHERE resolved_at IS NULL GROUP BY kind HAVING count(*) > 1 UNION SELECT 'runs' WHERE (SELECT"
				+ " count(*) FROM survey_media_listing_runs WHERE finished_at IS NULL) > 1");
		queries.put("V10 unvalidated constraints, invalid indexes", "SELECT conname::text FROM pg_constraint WHERE NOT"
				+ " convalidated UNION SELECT indexrelid::regclass::text FROM pg_index WHERE NOT indisvalid");
		queries.put("V12 budget rows older than 3 hours", "SELECT user_id FROM survey_media_read_budgets WHERE"
				+ " hour_start < now() - interval '3 hours'");
		queries.put("V13 an open listing run older than 24 hours or stuck", "SELECT id::text FROM"
				+ " survey_media_listing_runs WHERE finished_at IS NULL AND (started_at < now() - interval '24 hours'"
				+ " OR lease_until < now() - interval '15 minutes')");
		queries.put("V14 body slots expired more than 15 minutes ago", "SELECT id::text FROM survey_media_body_slots"
				+ " WHERE expires_at < now() - interval '15 minutes'");
		return queries;
	}

	/** The names of the queries that return rows, with their first rows. */
	static List<String> violations(JdbcTemplate jdbc, List<String> checklistIds) {
		String list = checklistIds.isEmpty() ? "''" : String.join(", ", checklistIds.stream()
				.map(id -> "'" + id + "'").toList());
		List<String> failing = new ArrayList<>();
		queries(list).forEach((name, sql) -> {
			List<String> rows = jdbc.queryForList(sql + " LIMIT 5", String.class);
			if (!rows.isEmpty()) {
				failing.add(name + ": " + rows);
			}
		});
		return failing;
	}
}
