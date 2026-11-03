package vn.sunvibe.survey;

import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

import com.github.f4b6a3.ulid.UlidCreator;

/**
 * Raw rows for the survey-media persistence tests: a VISITED appointment and a DRAFT checklist of a lead, written
 * directly, so a photo row has its checklist without the API flow. Remove them with
 * {@link SurveyApiTestSupport#deleteSurveyRows}.
 */
final class MediaTestRows {

	static final String SALE = "01JSA1EMED1A00000000000001";

	private MediaTestRows() {
	}

	/** A new checklist of the lead on a new VISITED appointment; returns the checklist id. */
	static String checklist(JdbcTemplate jdbc, String leadId, int no) {
		String appointmentId = UlidCreator.getMonotonicUlid().toString();
		String checklistId = UlidCreator.getMonotonicUlid().toString();
		Instant at = Instant.parse("2026-10-30T02:00:00Z");
		jdbc.update("INSERT INTO survey_appointments (id, lead_id, appointment_no, kind, intent, intent_cycle, status,"
				+ " start_at, duration_minutes, meeting_address, meeting_province_code, meeting_province_name,"
				+ " meeting_ward_code, meeting_ward_name, current_revision_no, lead_address_detail, lead_province_code,"
				+ " lead_province_name, lead_ward_code, lead_ward_name, surveyor_user_id, confirmation_state,"
				+ " ever_confirmed, confirmation_changed_by, confirmation_changed_at, visit_record_kind, performed_by,"
				+ " arrived_at, visit_revision_no, visit_note, visit_recorded_by, visit_recorded_at, created_by,"
				+ " created_at, updated_at, version)"
				+ " VALUES (?, ?, ?, 'FIRST', 'LONG_TERM_RENT', 1, 'VISITED', ?, 60, '12 Bach Dang', '48', 'Da Nang',"
				+ " '20194', 'Hai Chau', 1, '12 Bach Dang', '48', 'Da Nang', '20194', 'Hai Chau', ?, 'CONFIRMED', true,"
				+ " ?, ?, 'OWN', ?, ?, 1, 'Da den', ?, ?, ?, ?, ?, 0)",
				appointmentId, leadId, no, java.sql.Timestamp.from(at), SALE, SALE, java.sql.Timestamp.from(at), SALE,
				java.sql.Timestamp.from(at), SALE, java.sql.Timestamp.from(at), SALE, java.sql.Timestamp.from(at),
				java.sql.Timestamp.from(at));
		jdbc.update("INSERT INTO survey_checklists (id, lead_id, checklist_no, visit_appointment_id, intent_cycle,"
				+ " holder_user_id, template_code, template_version, kind, status, created_by, created_at, version)"
				+ " VALUES (?, ?, ?, ?, 1, ?, 'LONG_TERM_RENT', 1, 'FULL_ASSESSMENT', 'DRAFT', ?, ?, 0)",
				checklistId, leadId, no, appointmentId, SALE, SALE, java.sql.Timestamp.from(at));
		return checklistId;
	}

	/** Removes every survey row of the leads, media rows first. */
	static void delete(JdbcTemplate jdbc, List<String> leadIds) {
		SurveyApiTestSupport.deleteSurveyRows(jdbc, leadIds);
	}
}
