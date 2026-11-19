package vn.sunvibe.brokerage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementPeriod;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementTermination;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementVersion;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.ChargeUnit;
import vn.sunvibe.brokerage.core.domain.model.agreement.DepositBasis;
import vn.sunvibe.brokerage.core.domain.model.agreement.EarlyLeaseEndRefund;
import vn.sunvibe.brokerage.core.domain.model.agreement.EarlyLeaseEndType;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeBasis;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeFormula;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeFormulaType;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeTier;
import vn.sunvibe.brokerage.core.domain.model.agreement.MandatoryFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.PhotoConsent;
import vn.sunvibe.brokerage.core.domain.model.agreement.TermsDeposit;
import vn.sunvibe.brokerage.core.domain.model.agreement.TerminationType;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionTerms;
import vn.sunvibe.brokerage.core.domain.model.agreement.WithdrawalChannel;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementSuspension;
import vn.sunvibe.brokerage.core.domain.model.agreement.SuspensionReasonType;
import vn.sunvibe.brokerage.core.domain.repository.AgreementDomainRepository;
import vn.sunvibe.brokerage.core.domain.repository.AgreementSuspensionDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * The agreement's end columns, their CHECKs, the suspension records and the load paths of final rows on a real
 * PostgreSQL (brokerage-agreement E05-S8 spec S8-D6, S8-D9, 5.1, 5.3; MG-1, LD-1; plan E05S8-P1). The annotations are
 * those of {@link AgreementPersistencePostgresTest}, so both share one context. Needs Docker.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class AgreementEndPersistencePostgresTest {

	private static final String END_CHECK = "ck_brokerage_agreements_end";
	private static final String TERMINATION_CHECK = "ck_brokerage_agreements_termination";
	private static final String OPEN_INDEX = "uq_brokerage_agreement_suspensions_open";
	private static final String RELEASE_CHECK = "ck_brokerage_agreement_suspensions_release";
	private static final String MANAGER = "01JMANAGER0000000000000000";
	private static final AgreementPeriod PERIOD =
			new AgreementPeriod(LocalDate.parse("2027-01-01"), LocalDate.parse("2027-12-31"));
	private static final BrokerageFee FEE_POLICY = new BrokerageFee(
			FeeFormula.of(FeeFormulaType.PERCENT_OF_RENT, List.of(new FeeTier(1, 50), new FeeTier(12, 100)), null, null),
			7, FeeFormula.of(FeeFormulaType.PERCENT_OF_RENT, List.of(new FeeTier(1, 30)), null, null),
			new EarlyLeaseEndRefund(EarlyLeaseEndType.PARTIAL_REFUND, 50, 3), 90);

	@Autowired
	private AgreementDomainRepository agreements;

	@Autowired
	private AgreementSuspensionDomainRepository suspensionRecords;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private IdGenerator<String> ids;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private final List<String> created = new ArrayList<>();

	@AfterEach
	void deleteRows() {
		AgreementFixture.delete(jdbc, created);
		created.clear();
	}

	/** MG-1: both CHECKs of the first file are validated by the second. */
	@Test
	void migrations_endConstraints_validated() {
		List<String> validated = jdbc.query("SELECT conname, convalidated FROM pg_constraint WHERE conname IN (?, ?)"
				+ " ORDER BY conname", (row, index) -> row.getString("conname") + "|" + row.getBoolean("convalidated"),
				END_CHECK, TERMINATION_CHECK);

		assertEquals(List.of(END_CHECK + "|true", TERMINATION_CHECK + "|true"), validated);
	}

	/** S8-D9: an agreement that has not ended holds no end. */
	@Test
	void endCheck_endedAtOnUnfinishedState_refused() {
		for (String state : List.of("DRAFTING", "AWAITING_CONFIRMATION", "ACTIVE", "SUSPENDED")) {
			String agreementId = fixture(state, state.equals("DRAFTING") ? "DRAFT" : "EFFECTIVE");

			assertRefused(END_CHECK, () -> jdbc.update("UPDATE brokerage_agreements SET ended_at = ? WHERE id = ?",
					Timestamp.from(now()), agreementId));
		}
		for (String state : List.of("EXPIRED", "TERMINATED", "CANCELLED")) {
			String agreementId = fixture(state, "CANCELLED");

			assertEquals(1, jdbc.update("UPDATE brokerage_agreements SET ended_at = ? WHERE id = ?",
					Timestamp.from(now()), agreementId), state);
		}
	}

	/** S8-D5, S8-D9: the termination columns of each type, only on a TERMINATED agreement with its end. */
	@Test
	void terminationCheck_eachBranch_refused() {
		String active = fixture("ACTIVE", "EFFECTIVE");
		assertRefused(TERMINATION_CHECK, () -> jdbc.update("UPDATE brokerage_agreements SET termination_type ="
				+ " 'SUNVIBE_TERMINATED', termination_note = 'Dispute.' WHERE id = ?", active));

		String terminated = fixture("TERMINATED", "CANCELLED");
		assertRefused(TERMINATION_CHECK, () -> jdbc.update("UPDATE brokerage_agreements SET termination_type ="
				+ " 'SUNVIBE_TERMINATED', termination_note = 'Dispute.' WHERE id = ?", terminated));
		jdbc.update("UPDATE brokerage_agreements SET ended_at = ? WHERE id = ?", Timestamp.from(now()), terminated);
		assertRefused(TERMINATION_CHECK, () -> jdbc.update("UPDATE brokerage_agreements SET termination_type ="
				+ " 'OWNER_WITHDREW', termination_reported_on = DATE '2026-12-01' WHERE id = ?", terminated));
		assertRefused(TERMINATION_CHECK, () -> jdbc.update("UPDATE brokerage_agreements SET termination_type ="
				+ " 'UNIT_LET', termination_reported_on = DATE '2026-12-01', termination_introduced = 'YES',"
				+ " termination_channel = 'EMAIL' WHERE id = ?", terminated));
		assertRefused(TERMINATION_CHECK, () -> jdbc.update("UPDATE brokerage_agreements SET termination_type ="
				+ " 'SUNVIBE_TERMINATED' WHERE id = ?", terminated));

		assertEquals(1, jdbc.update("UPDATE brokerage_agreements SET termination_type = 'UNIT_LET',"
				+ " termination_reported_on = DATE '2026-12-01', termination_introduced = 'UNKNOWN' WHERE id = ?",
				terminated));
	}

	/** S8-D6: at most one open record per agreement. */
	@Test
	void suspensions_secondOpen_refusedByUniqueIndex() {
		String agreementId = fixture("SUSPENDED", "EFFECTIVE");
		insertSuspension(agreementId, now().minus(2, ChronoUnit.HOURS), null, null, null);

		assertRefused(OPEN_INDEX, () -> insertSuspension(agreementId, now(), null, null, null));
		insertSuspension(fixture("SUSPENDED", "EFFECTIVE"), now(), null, null, null);
	}

	@Test
	void suspensions_partialRelease_refused() {
		String agreementId = fixture("ACTIVE", "EFFECTIVE");
		Instant at = now().minus(1, ChronoUnit.HOURS);

		assertRefused(RELEASE_CHECK, () -> insertSuspension(agreementId, at, MANAGER, null, null));
		assertRefused(RELEASE_CHECK, () -> insertSuspension(agreementId, at, null, now(), null));
		assertRefused(RELEASE_CHECK, () -> insertSuspension(agreementId, at, MANAGER, now(), null));
		assertRefused(RELEASE_CHECK, () -> insertSuspension(agreementId, at, null, null, "Checked."));
		insertSuspension(agreementId, at, MANAGER, now(), "Checked.");
	}

	@Test
	void suspensions_releasedBeforeSuspended_refused() {
		String agreementId = fixture("ACTIVE", "EFFECTIVE");
		Instant at = now();

		assertRefused(RELEASE_CHECK, () -> insertSuspension(agreementId, at, MANAGER, at.minus(1, ChronoUnit.MICROS),
				"Checked."));
		insertSuspension(agreementId, at, MANAGER, at, "Checked.");
	}

	/** S8-D9: a record goes with its agreement. */
	@Test
	void suspensions_agreementDeleted_cascade() {
		String agreementId = fixture("SUSPENDED", "EFFECTIVE");
		insertSuspension(agreementId, now(), null, null, null);
		assertEquals(1, suspensions(agreementId));

		AgreementFixture.delete(jdbc, List.of(agreementId));

		assertEquals(0, suspensions(agreementId));
	}

	/**
	 * MG-1 (testing.md: the save of a loaded aggregate, its child rows seeded first): a loaded agreement with its version,
	 * fee rows, tier rows and the six end columns saves through the guarded update and reloads equal, its child rows kept.
	 */
	@Test
	void terminatedAgreement_savedAndReloaded_equal() {
		BrokerageAgreement agreement = sentAgreement(now().minus(2, ChronoUnit.HOURS));
		String versionId = agreement.getVersions().getFirst().getId();
		List<String> fees = feeRows(versionId);
		List<String> tiers = tierRows(versionId);
		Instant at = now();
		AgreementTermination termination = AgreementTermination.of(TerminationType.OWNER_WITHDREW,
				AgreementVersion.todayInVietnam(at), WithdrawalChannel.MESSAGE, null, "Signed elsewhere.",
				AgreementVersion.todayInVietnam(at), at);

		tx(() -> {
			BrokerageAgreement read = agreements.findById(agreement.getId()).orElseThrow();
			agreements.update(read.effectiveAt(at).terminate(termination, at));
			return null;
		});
		BrokerageAgreement first = tx(() -> agreements.findById(agreement.getId())).orElseThrow();
		tx(() -> {
			agreements.update(agreements.findById(agreement.getId()).orElseThrow());
			return null;
		});
		BrokerageAgreement second = tx(() -> agreements.findById(agreement.getId())).orElseThrow();

		for (BrokerageAgreement read : List.of(first, second)) {
			assertEquals(AgreementStatus.TERMINATED, read.getStatus());
			assertEquals(at, read.getEndedAtOrNull());
			assertEquals(termination, read.getTerminationOrNull());
			AgreementVersion version = read.getVersions().getFirst();
			assertEquals(VersionStatus.CANCELLED, version.getStatus());
			assertEquals(FEE_POLICY, version.getFee());
			assertEquals(agreement.getVersions().getFirst().getSentAtOrNull(), version.getSentAtOrNull());
		}
		assertEquals(first.getVersions().getFirst().getTerms(), second.getVersions().getFirst().getTerms());
		assertEquals(fees, feeRows(versionId));
		assertEquals(tiers, tierRows(versionId));
		Map<String, Object> row = jdbc.queryForMap("SELECT * FROM brokerage_agreements WHERE id = ?", agreement.getId());
		assertEquals("OWNER_WITHDREW", row.get("termination_type"));
		assertEquals(Date.valueOf(AgreementVersion.todayInVietnam(at)), row.get("termination_reported_on"));
		assertEquals("MESSAGE", row.get("termination_channel"));
		assertNull(row.get("termination_introduced"));
		assertEquals("Signed elsewhere.", row.get("termination_note"));
		assertEquals(at, ((Timestamp) row.get("ended_at")).toInstant());
	}

	/** LD-1: final rows stored without the end columns load with null end fields, their status kept, on both paths. */
	@Test
	void finalFixturesWithoutEndColumns_loadWithNullEndFields() {
		List<String> fixtures = List.of(fixture("CANCELLED", "CANCELLED"), fixture("TERMINATED", "CANCELLED"),
				fixture("TERMINATED", "EFFECTIVE"), fixture("EXPIRED", "EFFECTIVE"), fixture("EXPIRED", "CANCELLED"));

		List<BrokerageAgreement> one = fixtures.stream()
				.map(id -> tx(() -> agreements.findById(id)).orElseThrow()).toList();
		List<BrokerageAgreement> set = tx(() -> agreements.findAllById(fixtures));

		assertEquals(fixtures.size(), set.size());
		for (List<BrokerageAgreement> read : List.of(one, set)) {
			for (BrokerageAgreement agreement : read) {
				String stored = jdbc.queryForObject("SELECT status FROM brokerage_agreements WHERE id = ?", String.class,
						agreement.getId());
				assertEquals(stored, agreement.getStatus().name());
				assertNull(agreement.getEndedAtOrNull(), agreement.getId());
				assertNull(agreement.getTerminationOrNull(), agreement.getId());
			}
		}
	}

	// ==================== the suspension records (spec S8-D6, 5.3) ====================

	@Test
	void suspensionRepository_openThenRelease_closedWithWhoWhenReason() {
		String agreementId = fixture("SUSPENDED", "EFFECTIVE");
		Instant suspendedAt = now().minus(2, ChronoUnit.HOURS);
		Instant releasedAt = now();
		AgreementSuspension opened = AgreementSuspension.open(ids.generateId(), agreementId,
				SuspensionReasonType.DISPUTE, "A dispute on the rent.", MANAGER, suspendedAt);

		tx(() -> {
			suspensionRecords.open(opened);
			return null;
		});
		boolean released = tx(() -> suspensionRecords.release(agreementId, "01JRELEASER000000000000000", releasedAt,
				"The owner agreed."));
		List<AgreementSuspension> read = tx(() -> suspensionRecords.newestOf(agreementId, 20));

		assertTrue(released);
		assertEquals(List.of(opened.released("01JRELEASER000000000000000", releasedAt, "The owner agreed.")), read);
	}

	@Test
	void suspensionRepository_releaseWithoutOpen_false() {
		String agreementId = fixture("ACTIVE", "EFFECTIVE");
		insertSuspension(agreementId, now().minus(1, ChronoUnit.HOURS), MANAGER, now(), "Checked.");

		assertFalse(tx(() -> suspensionRecords.release(agreementId, MANAGER, now(), "Again.")));
		assertEquals("Checked.", jdbc.queryForObject("SELECT release_reason FROM brokerage_agreement_suspensions"
				+ " WHERE agreement_id = ?", String.class, agreementId));
	}

	/** S8-D6: the partial unique index refuses a second open record; the adapter answers IllegalStateException. */
	@Test
	void suspensionRepository_secondOpen_illegalState() {
		String agreementId = fixture("SUSPENDED", "EFFECTIVE");
		insertSuspension(agreementId, now().minus(1, ChronoUnit.HOURS), null, null, null);

		assertThrows(IllegalStateException.class, () -> tx(() -> {
			suspensionRecords.open(AgreementSuspension.open(ids.generateId(), agreementId,
					SuspensionReasonType.COMPLAINT, null, MANAGER, now()));
			return null;
		}));
		assertEquals(1, suspensions(agreementId));
	}

	@Test
	void suspensionRepository_newestOf_newestFirstBounded() {
		String agreementId = fixture("SUSPENDED", "EFFECTIVE");
		Instant first = now().minus(3, ChronoUnit.HOURS);
		insertSuspension(agreementId, first, MANAGER, first.plus(10, ChronoUnit.MINUTES), "First lifted.");
		insertSuspension(agreementId, first.plus(1, ChronoUnit.HOURS), MANAGER, first.plus(70, ChronoUnit.MINUTES),
				"Second lifted.");
		insertSuspension(agreementId, first.plus(2, ChronoUnit.HOURS), null, null, null);

		List<AgreementSuspension> two = tx(() -> suspensionRecords.newestOf(agreementId, 2));

		assertEquals(List.of(first.plus(2, ChronoUnit.HOURS), first.plus(1, ChronoUnit.HOURS)),
				two.stream().map(AgreementSuspension::suspendedAt).toList());
		assertTrue(two.getFirst().isOpen());
		assertEquals("Second lifted.", two.get(1).releaseReasonOrNull());
	}

	/** persistence.md: two records of one microsecond keep one order, by the greater id first. */
	@Test
	void suspensionRepository_newestOf_sameSuspendedAt_idDescending() {
		String agreementId = fixture("ACTIVE", "EFFECTIVE");
		Instant at = now().minus(1, ChronoUnit.HOURS);
		insertSuspension(agreementId, at, MANAGER, at.plus(1, ChronoUnit.MINUTES), "One.");
		insertSuspension(agreementId, at, MANAGER, at.plus(2, ChronoUnit.MINUTES), "Two.");
		List<String> expected = jdbc.queryForList("SELECT id FROM brokerage_agreement_suspensions WHERE agreement_id = ?"
				+ " ORDER BY id DESC", String.class, agreementId);

		List<String> first = tx(() -> suspensionRecords.newestOf(agreementId, 20)).stream()
				.map(AgreementSuspension::id).toList();
		List<String> second = tx(() -> suspensionRecords.newestOf(agreementId, 20)).stream()
				.map(AgreementSuspension::id).toList();

		assertEquals(expected, first);
		assertEquals(expected, second);
	}

	/** A fixture agreement of a new unit and lead in that state, with its version 1 in that state. */
	private String fixture(String agreementStatus, String versionStatus) {
		String agreementId = AgreementFixture.insert(jdbc, ids, ids.generateId(), ids.generateId(), agreementStatus,
				versionStatus);
		created.add(agreementId);
		return agreementId;
	}

	private void insertSuspension(String agreementId, Instant suspendedAt, String releasedBy, Instant releasedAt,
			String releaseReason) {
		jdbc.update("INSERT INTO brokerage_agreement_suspensions (id, agreement_id, reason_type, note, suspended_by,"
				+ " suspended_at, released_by, released_at, release_reason) VALUES (?, ?, 'COMPLAINT', NULL, ?, ?, ?, ?, ?)",
				ids.generateId(), agreementId, MANAGER, Timestamp.from(suspendedAt), releasedBy,
				releasedAt == null ? null : Timestamp.from(releasedAt), releaseReason);
	}

	private int suspensions(String agreementId) {
		Integer count = jdbc.queryForObject("SELECT count(*) FROM brokerage_agreement_suspensions WHERE agreement_id = ?",
				Integer.class, agreementId);
		return count == null ? 0 : count;
	}

	/** An agreement created, its draft completed with fees and tiers and sent at {@code sent}, through the aggregate. */
	private BrokerageAgreement sentAgreement(Instant sent) {
		BrokerageAgreement agreement = BrokerageAgreement.create(ids.generateId(), ids.generateId(), "P101",
				ids.generateId(), TransactionType.LONG_TERM_RENT, ids.generateId(), ids.generateId(), ids.generateId(),
				ids.generateId(), PERIOD, ids.generateId(), sent.minus(1, ChronoUnit.HOURS));
		created.add(agreement.getId());
		VersionTerms terms = new VersionTerms(8_000_000L, new TermsDeposit(DepositBasis.MONTHS, 2, null), 12, 1, List.of(
				new MandatoryFee("Electricity", FeeBasis.AMOUNT, 3_500L, ChargeUnit.PER_KWH)));
		tx(() -> {
			agreements.create(agreement);
			return null;
		});
		tx(() -> {
			BrokerageAgreement read = agreements.findById(agreement.getId()).orElseThrow();
			agreements.update(read.editDraft(PERIOD, new PhotoConsent(true, false), terms, FEE_POLICY,
					sent.minus(30, ChronoUnit.MINUTES)));
			return null;
		});
		tx(() -> {
			agreements.update(agreements.findById(agreement.getId()).orElseThrow().sendDraft(sent));
			return null;
		});
		return tx(() -> agreements.findById(agreement.getId())).orElseThrow();
	}

	private List<String> tierRows(String versionId) {
		return jdbc.query("SELECT renewal, tier_no, from_months, percent FROM brokerage_agreement_version_fee_tiers"
				+ " WHERE version_id = ? ORDER BY renewal, tier_no", (row, index) -> row.getBoolean("renewal") + "|"
						+ row.getInt("tier_no") + "|" + row.getInt("from_months") + "|" + row.getInt("percent"),
				versionId);
	}

	private List<String> feeRows(String versionId) {
		return jdbc.query("SELECT fee_no, name, basis, amount, charge_unit FROM brokerage_agreement_version_fees"
				+ " WHERE version_id = ? ORDER BY fee_no", (row, index) -> row.getInt("fee_no") + "|"
						+ row.getString("name") + "|" + row.getString("basis") + "|" + row.getBigDecimal("amount")
						+ "|" + row.getString("charge_unit"), versionId);
	}

	private static void assertRefused(String constraint, Runnable write) {
		DataIntegrityViolationException refused = assertThrows(DataIntegrityViolationException.class, write::run,
				constraint);
		assertTrue(refused.getMessage().contains(constraint), refused.getMessage());
	}

	private <T> T tx(Supplier<T> work) {
		return new TransactionTemplate(transactionManager).execute(status -> work.get());
	}

	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}
}
