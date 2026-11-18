package vn.sunvibe.brokerage;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.jdbc.core.JdbcTemplate;

import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * Fixture agreements of the agreement list and its history, written by JDBC (E05-S7 plan E05S7-P4): an agreement in a
 * chosen state with a chosen creation time and chosen versions, each on a unit of its own, and history rows as the
 * trail writes them. Owner, creator and basis intake are those of {@link AgreementFixture}; cleanup through
 * {@link AgreementFixture#delete}.
 */
public final class AgreementListFixture {

	/** One version of a fixture agreement, its dates calendar dates in Vietnam. */
	public record Version(int versionNo, String status, LocalDate startDate, LocalDate endDate) {
	}

	private AgreementListFixture() {
	}

	/**
	 * Inserts an agreement on a new unit, type LONG_TERM_RENT, property "P-FIXTURE", in the given status and created at
	 * the given time, with the given versions, every one resting on the basis lead.
	 *
	 * @return the agreement id
	 */
	public static String insert(JdbcTemplate jdbc, IdGenerator<String> ids, String basisLeadId, String unitCodeOrNull,
			String agreementStatus, Instant createdAt, Version... versions) {
		String agreementId = ids.generateId();
		Timestamp created = Timestamp.from(createdAt);
		jdbc.update("INSERT INTO brokerage_agreements (id, unit_id, unit_code, property_id, transaction_type,"
				+ " owner_user_id, basis_lead_id, status, created_by_user_id, created_at, updated_at, version)"
				+ " VALUES (?, ?, ?, 'P-FIXTURE', 'LONG_TERM_RENT', ?, ?, ?, ?, ?, ?, 0)", agreementId, ids.generateId(),
				unitCodeOrNull, AgreementFixture.FIXTURE_USER, basisLeadId, agreementStatus, AgreementFixture.FIXTURE_USER,
				created, created);
		for (Version version : versions) {
			jdbc.update("INSERT INTO brokerage_agreement_versions (id, agreement_id, version_no, status, start_date,"
					+ " end_date, basis_lead_id, basis_intake_id, created_by_user_id, created_at, updated_at)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", ids.generateId(), agreementId, version.versionNo(),
					version.status(), Date.valueOf(version.startDate()), Date.valueOf(version.endDate()), basisLeadId,
					AgreementFixture.FIXTURE_INTAKE, AgreementFixture.FIXTURE_USER, created, created);
		}
		return agreementId;
	}

	/** Inserts one history row of the agreement, as the trail's recordEvent writes it. */
	public static void event(JdbcTemplate jdbc, String agreementId, Integer versionNoOrNull, String eventType,
			String actorUserIdOrNull, Instant occurredAt) {
		jdbc.update("INSERT INTO brokerage_agreement_events (agreement_id, version_no, event_type, actor_user_id,"
				+ " occurred_at) VALUES (?, ?, ?, ?, ?)", agreementId, versionNoOrNull, eventType, actorUserIdOrNull,
				Timestamp.from(occurredAt));
	}
}
