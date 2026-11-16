package vn.sunvibe.brokerage;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;

import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * Fixture brokerage agreements written by JDBC, for the tests that need an agreement in a given state, and their
 * cleanup. A test deletes only the agreement ids it created: the PostgreSQL context is shared with other classes.
 */
public final class AgreementFixture {

	/** The owner, creator and drafter of every fixture agreement. */
	public static final String FIXTURE_USER = "01JFIXTUREUSER000000000000";
	/** The basis intake of every fixture version. */
	public static final String FIXTURE_INTAKE = "01JFIXTUREINTAKE0000000000";

	private static final Set<String> TABLES = Set.of("brokerage_agreements", "brokerage_agreement_versions",
			"brokerage_agreement_events", "brokerage_agreement_access_logs");

	private AgreementFixture() {
	}

	/**
	 * Inserts an agreement of the unit, type LONG_TERM_RENT, property "P-FIXTURE", in the given status, with its version
	 * 1 of 2027-01-01..2027-12-31 in the given status, both resting on the basis lead.
	 *
	 * @return the agreement id
	 */
	public static String insert(JdbcTemplate jdbc, IdGenerator<String> ids, String unitId, String basisLeadId,
			String agreementStatus, String versionStatus) {
		String agreementId = ids.generateId();
		Timestamp now = Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MICROS));
		jdbc.update("INSERT INTO brokerage_agreements (id, unit_id, unit_code, property_id, transaction_type,"
				+ " owner_user_id, basis_lead_id, status, created_by_user_id, created_at, updated_at, version)"
				+ " VALUES (?, ?, NULL, 'P-FIXTURE', 'LONG_TERM_RENT', ?, ?, ?, ?, ?, ?, 0)", agreementId, unitId,
				FIXTURE_USER, basisLeadId, agreementStatus, FIXTURE_USER, now, now);
		jdbc.update("INSERT INTO brokerage_agreement_versions (id, agreement_id, version_no, status, start_date,"
				+ " end_date, basis_lead_id, basis_intake_id, created_by_user_id, created_at, updated_at)"
				+ " VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?)", ids.generateId(), agreementId, versionStatus,
				Date.valueOf(LocalDate.of(2027, 1, 1)), Date.valueOf(LocalDate.of(2027, 12, 31)), basisLeadId,
				FIXTURE_INTAKE, FIXTURE_USER, now, now);
		return agreementId;
	}

	/** Moves the agreement to CANCELLED and its pending versions to CANCELLED, which frees the unit's slot. */
	public static void cancel(JdbcTemplate jdbc, String agreementId) {
		jdbc.update("UPDATE brokerage_agreement_versions SET status = 'CANCELLED' WHERE agreement_id = ?"
				+ " AND status IN ('DRAFT', 'AWAITING_OWNER', 'OWNER_CONFIRMED', 'ACTIVATED')", agreementId);
		jdbc.update("UPDATE brokerage_agreements SET status = 'CANCELLED', version = version + 1 WHERE id = ?",
				agreementId);
	}

	/** Deletes every row of those agreements, children first: access logs, events, versions, agreements. */
	public static void delete(JdbcTemplate jdbc, Collection<String> agreementIds) {
		for (String agreementId : agreementIds) {
			jdbc.update("DELETE FROM brokerage_agreement_access_logs WHERE agreement_id = ?", agreementId);
			jdbc.update("DELETE FROM brokerage_agreement_events WHERE agreement_id = ?", agreementId);
			jdbc.update("DELETE FROM brokerage_agreement_versions WHERE agreement_id = ?", agreementId);
			jdbc.update("DELETE FROM brokerage_agreements WHERE id = ?", agreementId);
		}
	}

	/** The number of rows of one of the four brokerage tables that belong to the agreement. */
	public static int rows(JdbcTemplate jdbc, String table, String agreementId) {
		if (!TABLES.contains(table)) {
			throw new IllegalArgumentException("not a brokerage agreement table: " + table);
		}
		String column = "brokerage_agreements".equals(table) ? "id" : "agreement_id";
		Integer count = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?",
				Integer.class, agreementId);
		return count == null ? 0 : count;
	}
}
