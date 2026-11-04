package vn.sunvibe.survey;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The rows of a revision returned for documents and of the next revision on its visit, written directly so a test of
 * the media rules needs no submit, decision or start-revision command (survey-checklist submission spec 4.6).
 */
final class ChecklistRevisionRows {

	private final JdbcTemplate jdbc;
	private final Supplier<String> ids;

	ChecklistRevisionRows(JdbcTemplate jdbc, Supplier<String> ids) {
		this.jdbc = jdbc;
		this.ids = ids;
	}

	/**
	 * The revision as returned for documents by the reviewer, and a DRAFT #next on its visit holding the same groups and
	 * carrying the photos; answers the new revision's id.
	 */
	String documentsOnlyReturn(String checklistId, String reviewerId, List<String> carriedMediaIds) {
		Timestamp now = Timestamp.from(Instant.now());
		jdbc.update("update survey_checklists set status = 'NEEDS_CHANGES', submitted_at = ?,"
				+ " submitted_by_user_id = holder_user_id, decided_at = ? where id = ?", now, now, checklistId);
		jdbc.update("insert into survey_checklist_submissions (checklist_id, submitted_by_user_id, submitted_at,"
				+ " template_code, template_version, kind, lead_intent, intent_cycle, visit_appointment_id,"
				+ " visit_performed_by, visit_arrived_at, visit_recorded_by, visit_recorded_at, rental_scope, spaces,"
				+ " group_versions, media_policy, content_sha256)"
				+ " select c.id, c.holder_user_id, ?, c.template_code, c.template_version, c.kind, 'LONG_TERM_RENT',"
				+ " c.intent_cycle, c.visit_appointment_id, c.holder_user_id, ?, c.holder_user_id, ?, 'WHOLE',"
				+ " '[]', '{}', 'MEDIA_V1_NO_PERMISSION', ? from survey_checklists c where c.id = ?", now, now, now,
				"e".repeat(64), checklistId);
		jdbc.update("insert into survey_checklist_decisions (checklist_id, outcome, needs_changes_kind, reason,"
				+ " reviewer_user_id, decided_at, lead_status_before, lead_status_after)"
				+ " values (?, 'NEEDS_CHANGES', 'DOCUMENTS_ONLY', 'Thiếu giấy tờ', ?, ?, 'SURVEY_SCHEDULED',"
				+ " 'SURVEY_SCHEDULED')", checklistId, reviewerId, now);
		for (String mediaId : carriedMediaIds) {
			jdbc.update("insert into survey_checklist_evidence (checklist_id, media_id, category, space_id, space_code,"
					+ " full_sha256) select ?, m.id, m.category, m.space_id, m.space_code_at_upload, ?"
					+ " from survey_media m where m.id = ?", checklistId, "f".repeat(64), mediaId);
		}
		String next = ids.get();
		jdbc.update("insert into survey_checklists (id, lead_id, checklist_no, visit_appointment_id, intent_cycle,"
				+ " holder_user_id, template_code, template_version, kind, status, created_by, created_at, version,"
				+ " previous_checklist_id) select ?, lead_id, checklist_no + 1, visit_appointment_id, intent_cycle,"
				+ " holder_user_id, template_code, template_version, kind, 'DRAFT', holder_user_id, ?, 0, id"
				+ " from survey_checklists where id = ?", next, now, checklistId);
		jdbc.update("insert into survey_checklist_groups (checklist_id, group_key, content, version)"
				+ " select ?, group_key, content, 0 from survey_checklist_groups where checklist_id = ?", next,
				checklistId);
		jdbc.update("insert into survey_media_sets (checklist_id, set_version, last_media_no, updated_at)"
				+ " values (?, 0, 0, ?)", next, now);
		for (String mediaId : carriedMediaIds) {
			jdbc.update("insert into survey_checklist_media_selections (checklist_id, media_id,"
					+ " carried_from_checklist_id, selected_at) values (?, ?, ?, ?)", next, mediaId, checklistId, now);
		}
		return next;
	}
}
