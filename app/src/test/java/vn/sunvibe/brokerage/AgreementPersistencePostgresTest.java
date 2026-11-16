package vn.sunvibe.brokerage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.brokerage.core.domain.exception.AgreementExistsException;
import vn.sunvibe.brokerage.core.domain.model.agreement.AdvertisingScope;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementAccessType;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementEventType;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementPeriod;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
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
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionTerms;
import vn.sunvibe.brokerage.core.domain.repository.AgreementDomainRepository;
import vn.sunvibe.brokerage.core.domain.repository.AgreementDomainRepository.UnfinishedAgreement;
import vn.sunvibe.brokerage.core.domain.repository.AgreementTrailDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * The agreement tables, their constraints and the repositories on a real PostgreSQL (E05-S2 spec 5.3, AD-4, AD-13;
 * brokerage-agreement BR-1, BR-5, BR-6, BR-17). The annotations are those of {@link vn.sunvibe.FlywayPostgresMigrationTest},
 * so both share one context. Needs Docker.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class AgreementPersistencePostgresTest {

	private static final String UNFINISHED_INDEX = "uq_brokerage_agreements_unfinished_unit";
	private static final String PENDING_INDEX = "uq_brokerage_agreement_versions_pending";
	private static final AgreementPeriod PERIOD =
			new AgreementPeriod(LocalDate.parse("2027-01-01"), LocalDate.parse("2027-12-31"));

	@Autowired
	private AgreementDomainRepository agreements;

	@Autowired
	private AgreementTrailDomainRepository trail;

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

	@Test
	void create_thenFindById_roundTripsToTheMicrosecond() {
		Instant now = Instant.parse("2026-11-20T03:15:00.123456789Z").truncatedTo(ChronoUnit.MICROS);
		BrokerageAgreement agreement = draft(ids.generateId(), "P101", now);

		tx(() -> {
			agreements.create(agreement);
			return null;
		});
		BrokerageAgreement read = tx(() -> agreements.findById(agreement.getId())).orElseThrow();

		assertEquals(agreement.getId(), read.getId());
		assertEquals(agreement.getUnitId(), read.getUnitId());
		assertEquals("P101", read.getUnitCodeOrNull());
		assertEquals(agreement.getPropertyId(), read.getPropertyId());
		assertEquals(TransactionType.LONG_TERM_RENT, read.getTransactionType());
		assertEquals(agreement.getOwnerUserId(), read.getOwnerUserId());
		assertEquals(agreement.getBasisLeadId(), read.getBasisLeadId());
		assertEquals(agreement.getStatus(), read.getStatus());
		assertEquals(agreement.getCreatedByUserId(), read.getCreatedByUserId());
		assertEquals(now, read.getCreatedAt());
		assertEquals(now, read.getUpdatedAt());
		assertEquals(0L, read.getRowVersionOrNull());
		assertEquals(1, read.getVersions().size());
		AgreementVersion written = agreement.getVersions().get(0);
		AgreementVersion version = read.getVersions().get(0);
		assertEquals(written.getId(), version.getId());
		assertEquals(1, version.getVersionNo());
		assertEquals(written.getStatus(), version.getStatus());
		assertEquals(PERIOD, version.getPeriod());
		assertEquals(written.getBasisLeadId(), version.getBasisLeadId());
		assertEquals(written.getBasisIntakeId(), version.getBasisIntakeId());
		assertEquals(written.getCreatedByUserId(), version.getCreatedByUserId());
		assertEquals(now, version.getCreatedAt());
		assertEquals(now, version.getUpdatedAt());
	}

	@Test
	void create_unitWithoutCode_keepsNullCode() {
		BrokerageAgreement agreement = draft(ids.generateId(), null, now());

		tx(() -> {
			agreements.create(agreement);
			return null;
		});

		assertNull(tx(() -> agreements.findById(agreement.getId())).orElseThrow().getUnitCodeOrNull());
		assertNull(jdbc.queryForObject("SELECT unit_code FROM brokerage_agreements WHERE id = ?", String.class,
				agreement.getId()));
	}

	@Test
	void create_secondUnfinishedOnTheUnit_agreementExistsException() {
		String unit = ids.generateId();
		BrokerageAgreement first = draft(unit, "P101", now());
		BrokerageAgreement second = draft(unit, "P101", now());
		tx(() -> {
			agreements.create(first);
			return null;
		});

		assertThrows(AgreementExistsException.class, () -> tx(() -> {
			agreements.create(second);
			return null;
		}));

		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM brokerage_agreements WHERE unit_id = ?",
				Integer.class, unit));
		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM brokerage_agreement_versions v"
				+ " JOIN brokerage_agreements a ON a.id = v.agreement_id WHERE a.unit_id = ?", Integer.class, unit));
		assertEquals(0, AgreementFixture.rows(jdbc, "brokerage_agreements", second.getId()));
	}

	@Test
	void unfinishedIndex_finalStatesFreeTheSlot() {
		Map<String, String> unfinished = Map.of("DRAFTING", "DRAFT", "AWAITING_CONFIRMATION", "AWAITING_OWNER",
				"ACTIVE", "EFFECTIVE", "SUSPENDED", "EFFECTIVE");
		for (Map.Entry<String, String> state : unfinished.entrySet()) {
			String unit = ids.generateId();
			created.add(AgreementFixture.insert(jdbc, ids, unit, ids.generateId(), state.getKey(), state.getValue()));

			DataIntegrityViolationException refused = assertThrows(DataIntegrityViolationException.class,
					() -> AgreementFixture.insert(jdbc, ids, unit, ids.generateId(), "DRAFTING", "DRAFT"),
					state.getKey());
			assertTrue(refused.getMessage().contains(UNFINISHED_INDEX), refused.getMessage());
		}
		for (String finalState : List.of("EXPIRED", "TERMINATED", "CANCELLED")) {
			String unit = ids.generateId();
			created.add(AgreementFixture.insert(jdbc, ids, unit, ids.generateId(), finalState, "CANCELLED"));

			created.add(assertDoesNotThrow(
					() -> AgreementFixture.insert(jdbc, ids, unit, ids.generateId(), "DRAFTING", "DRAFT"), finalState));
		}
	}

	@Test
	void pendingIndex_lapsedEffectiveCancelledFreeTheSlot() {
		for (String pending : List.of("DRAFT", "AWAITING_OWNER", "OWNER_CONFIRMED", "ACTIVATED")) {
			String agreementId = AgreementFixture.insert(jdbc, ids, ids.generateId(), ids.generateId(), "DRAFTING",
					pending);
			created.add(agreementId);

			DataIntegrityViolationException refused = assertThrows(DataIntegrityViolationException.class,
					() -> insertVersion(agreementId, 2, "DRAFT", "2027-01-01", "2027-12-31"), pending);
			assertTrue(refused.getMessage().contains(PENDING_INDEX), refused.getMessage());
		}
		for (String done : List.of("LAPSED", "EFFECTIVE", "CANCELLED", "SUPERSEDED")) {
			String agreementId = AgreementFixture.insert(jdbc, ids, ids.generateId(), ids.generateId(), "ACTIVE", done);
			created.add(agreementId);

			assertDoesNotThrow(() -> insertVersion(agreementId, 2, "DRAFT", "2027-01-01", "2027-12-31"), done);
		}
	}

	@Test
	void checks_refuseUnknownCodesAndPeriods() {
		String agreementId = AgreementFixture.insert(jdbc, ids, ids.generateId(), ids.generateId(), "CANCELLED",
				"CANCELLED");
		created.add(agreementId);

		assertRefused("ck_brokerage_agreements_status", () -> insertAgreement(ids.generateId(), "UNKNOWN",
				"LONG_TERM_RENT"));
		assertRefused("ck_brokerage_agreements_transaction_type", () -> insertAgreement(ids.generateId(), "DRAFTING",
				"SALE"));
		assertRefused("ck_brokerage_agreement_versions_status",
				() -> insertVersion(agreementId, 2, "UNKNOWN", "2027-01-01", "2027-12-31"));
		assertRefused("ck_brokerage_agreement_versions_number",
				() -> insertVersion(agreementId, 0, "CANCELLED", "2027-01-01", "2027-12-31"));
		assertRefused("ck_brokerage_agreement_versions_period",
				() -> insertVersion(agreementId, 2, "CANCELLED", "2027-01-02", "2027-01-01"));
	}

	@Test
	void periodCheck_twelveMonthBoundaries_asPlusMonths() {
		String agreementId = AgreementFixture.insert(jdbc, ids, ids.generateId(), ids.generateId(), "CANCELLED",
				"CANCELLED");
		created.add(agreementId);

		assertRefused("ck_brokerage_agreement_versions_period",
				() -> insertVersion(agreementId, 2, "SUPERSEDED", "2028-02-29", "2029-03-01"));
		assertDoesNotThrow(() -> insertVersion(agreementId, 2, "SUPERSEDED", "2028-02-29", "2029-02-28"));
		assertDoesNotThrow(() -> insertVersion(agreementId, 3, "SUPERSEDED", "2027-01-01", "2028-01-01"));
		assertRefused("ck_brokerage_agreement_versions_period",
				() -> insertVersion(agreementId, 4, "SUPERSEDED", "2027-01-01", "2028-01-02"));
	}

	@Test
	void unfinishedOf_onlyUnfinishedOfTheType() {
		String u1 = ids.generateId();
		String u2 = ids.generateId();
		String u3 = ids.generateId();
		String u4 = ids.generateId();
		String l1 = ids.generateId();
		String l3 = ids.generateId();
		String id1 = AgreementFixture.insert(jdbc, ids, u1, l1, "DRAFTING", "DRAFT");
		String id2 = AgreementFixture.insert(jdbc, ids, u2, ids.generateId(), "CANCELLED", "CANCELLED");
		String id3 = AgreementFixture.insert(jdbc, ids, u3, l3, "ACTIVE", "EFFECTIVE");
		created.addAll(List.of(id1, id2, id3));

		List<UnfinishedAgreement> found = tx(() -> agreements.unfinishedOf(List.of(u1, u2, u3, u4),
				TransactionType.LONG_TERM_RENT));

		assertEquals(Set.of(new UnfinishedAgreement(id1, u1, l1), new UnfinishedAgreement(id3, u3, l3)),
				Set.copyOf(found));
		assertEquals(2, found.size());
		assertEquals(List.of(), tx(() -> agreements.unfinishedOf(List.of(), TransactionType.LONG_TERM_RENT)));
	}

	@Test
	void trail_recordsEventAndAccessRows() {
		String agreementId = AgreementFixture.insert(jdbc, ids, ids.generateId(), ids.generateId(), "DRAFTING",
				"DRAFT");
		created.add(agreementId);
		Instant at = now();

		tx(() -> {
			trail.recordEvent(agreementId, 1, AgreementEventType.CREATED, "01JACTOR0000000000000000AA", at);
			trail.recordAccess("01JUSER00000000000000000AA", agreementId, AgreementAccessType.DETAIL_READ, at);
			return null;
		});

		List<Map<String, Object>> events = jdbc.queryForList("SELECT version_no, event_type, actor_user_id, occurred_at"
				+ " FROM brokerage_agreement_events WHERE agreement_id = ?", agreementId);
		assertEquals(1, events.size());
		assertEquals(1, events.get(0).get("version_no"));
		assertEquals("CREATED", events.get(0).get("event_type"));
		assertEquals("01JACTOR0000000000000000AA", events.get(0).get("actor_user_id"));
		assertEquals(at, ((Timestamp) events.get(0).get("occurred_at")).toInstant());
		List<Map<String, Object>> accesses = jdbc.queryForList("SELECT user_id, action, accessed_at"
				+ " FROM brokerage_agreement_access_logs WHERE agreement_id = ?", agreementId);
		assertEquals(1, accesses.size());
		assertEquals("01JUSER00000000000000000AA", accesses.get(0).get("user_id"));
		assertEquals("DETAIL_READ", accesses.get(0).get("action"));
		assertEquals(at, ((Timestamp) accesses.get(0).get("accessed_at")).toInstant());
	}

	// ==================== E05-S3: the version content, its fee rows and the guarded update (ED-15) ====================

	@Test
	void update_contentAndThreeFees_roundTripsToTheMicrosecond() {
		Instant created = Instant.parse("2026-11-20T03:15:00.123456Z");
		Instant edited = Instant.parse("2026-11-21T04:00:00.654321Z");
		BrokerageAgreement agreement = draft(ids.generateId(), "P101", created);
		tx(() -> {
			agreements.create(agreement);
			return null;
		});
		PhotoConsent consent = new PhotoConsent(true, true);
		VersionTerms terms = new VersionTerms(8_000_000L, new TermsDeposit(DepositBasis.MONTHS, 2, null), 12, 1,
				List.of(new MandatoryFee("Electricity", FeeBasis.AMOUNT, 3_500L, ChargeUnit.PER_KWH),
						new MandatoryFee("Water", FeeBasis.PROVIDER_TARIFF, null, null),
						new MandatoryFee("Internet", FeeBasis.INCLUDED, null, null)));
		AgreementPeriod period = new AgreementPeriod(LocalDate.parse("2027-02-01"), LocalDate.parse("2028-01-31"));

		tx(() -> {
			BrokerageAgreement read = agreements.findById(agreement.getId()).orElseThrow();
			agreements.update(read.editDraft(period, consent, terms, BrokerageFee.NONE, edited));
			return null;
		});
		BrokerageAgreement read = tx(() -> agreements.findById(agreement.getId())).orElseThrow();

		assertEquals(agreement.getId(), read.getId());
		assertEquals(agreement.getUnitId(), read.getUnitId());
		assertEquals("P101", read.getUnitCodeOrNull());
		assertEquals(agreement.getPropertyId(), read.getPropertyId());
		assertEquals(agreement.getOwnerUserId(), read.getOwnerUserId());
		assertEquals(agreement.getBasisLeadId(), read.getBasisLeadId());
		assertEquals(agreement.getStatus(), read.getStatus());
		assertEquals(agreement.getCreatedByUserId(), read.getCreatedByUserId());
		assertEquals(created, read.getCreatedAt());
		assertEquals(edited, read.getUpdatedAt());
		assertEquals(1L, read.getRowVersionOrNull());
		assertEquals(1, read.getVersions().size());
		AgreementVersion written = agreement.getVersions().get(0);
		AgreementVersion version = read.getVersions().get(0);
		assertEquals(written.getId(), version.getId());
		assertEquals(1, version.getVersionNo());
		assertEquals(written.getStatus(), version.getStatus());
		assertEquals(written.getBasisLeadId(), version.getBasisLeadId());
		assertEquals(written.getBasisIntakeId(), version.getBasisIntakeId());
		assertEquals(written.getCreatedByUserId(), version.getCreatedByUserId());
		assertEquals(created, version.getCreatedAt());
		assertEquals(edited, version.getUpdatedAt());
		assertEquals(period, version.getPeriod());
		assertEquals(consent, version.getPhotoConsent());
		assertEquals(terms, version.getTerms());
		assertEquals(List.of("Electricity", "Water", "Internet"),
				version.getTerms().mandatoryFeesOrNull().stream().map(MandatoryFee::name).toList());
		assertEquals(1L, jdbc.queryForObject("SELECT version FROM brokerage_agreements WHERE id = ?", Long.class,
				agreement.getId()));
		assertEquals(List.of("1|Electricity|AMOUNT|3500|PER_KWH", "2|Water|PROVIDER_TARIFF|null|null",
				"3|Internet|INCLUDED|null|null"), feeRows(written.getId()));
	}

	/** testing.md: the save of a loaded aggregate, its child rows seeded first. */
	@Test
	void update_versionWithFeeRows_replacesThemKeepsTheRest() {
		BrokerageAgreement agreement = draft(ids.generateId(), "P101", now().minusSeconds(60));
		BrokerageAgreement other = draft(ids.generateId(), "P102", now().minusSeconds(60));
		tx(() -> {
			agreements.create(agreement);
			agreements.create(other);
			return null;
		});
		String versionId = agreement.getVersions().get(0).getId();
		String otherVersionId = other.getVersions().get(0).getId();
		for (String id : List.of(versionId, otherVersionId)) {
			jdbc.update("UPDATE brokerage_agreement_versions SET mandatory_fees_stated = TRUE WHERE id = ?", id);
			insertFee(id, 1, "Electricity", "AMOUNT", 3_500L, "PER_KWH");
			insertFee(id, 2, "Water", "PROVIDER_TARIFF", null, null);
			insertFee(id, 3, "Internet", "INCLUDED", null, null);
		}
		Map<String, Object> agreementBefore = agreementRow(agreement.getId());
		Map<String, Object> versionBefore = versionIdentity(versionId);
		Map<String, Object> otherBefore = agreementRow(other.getId());
		List<String> otherFees = feeRows(otherVersionId);

		edit(agreement.getId(), List.of(new MandatoryFee("Power", FeeBasis.AMOUNT, 4_000L, ChargeUnit.PER_KWH),
				new MandatoryFee("Water", FeeBasis.PROVIDER_TARIFF, null, null)));
		assertEquals(List.of("1|Power|AMOUNT|4000|PER_KWH", "2|Water|PROVIDER_TARIFF|null|null"), feeRows(versionId));

		edit(agreement.getId(), List.of(new MandatoryFee("Power", FeeBasis.INCLUDED, null, null),
				new MandatoryFee("Water", FeeBasis.AMOUNT, 15_000L, ChargeUnit.PER_M3),
				new MandatoryFee("Parking", FeeBasis.AMOUNT, 100_000L, ChargeUnit.PER_VEHICLE_PER_MONTH),
				new MandatoryFee("Cleaning", FeeBasis.AMOUNT, 50_000L, ChargeUnit.PER_PERSON_PER_MONTH)));
		assertEquals(List.of("1|Power|INCLUDED|null|null", "2|Water|AMOUNT|15000|PER_M3",
				"3|Parking|AMOUNT|100000|PER_VEHICLE_PER_MONTH", "4|Cleaning|AMOUNT|50000|PER_PERSON_PER_MONTH"),
				feeRows(versionId));
		assertTrue(feesStated(versionId));

		edit(agreement.getId(), null);
		assertEquals(List.of(), feeRows(versionId));
		assertEquals(false, feesStated(versionId));
		assertNull(tx(() -> agreements.findById(agreement.getId())).orElseThrow().getVersions().get(0).getTerms()
				.mandatoryFeesOrNull());

		edit(agreement.getId(), List.of());
		assertEquals(List.of(), feeRows(versionId));
		assertTrue(feesStated(versionId));
		assertEquals(List.of(), tx(() -> agreements.findById(agreement.getId())).orElseThrow().getVersions().get(0)
				.getTerms().mandatoryFeesOrNull());

		Map<String, Object> agreementAfter = agreementRow(agreement.getId());
		assertEquals(4L, agreementAfter.remove("version"));
		agreementAfter.remove("updated_at");
		agreementBefore.remove("version");
		agreementBefore.remove("updated_at");
		assertEquals(agreementBefore, agreementAfter);
		assertEquals(versionBefore, versionIdentity(versionId));
		assertEquals(otherBefore, agreementRow(other.getId()));
		assertEquals(otherFees, feeRows(otherVersionId));
	}

	@Test
	void update_staleRowVersion_concurrencyFailure() {
		BrokerageAgreement agreement = draft(ids.generateId(), "P101", now().minusSeconds(60));
		tx(() -> {
			agreements.create(agreement);
			return null;
		});
		BrokerageAgreement first = tx(() -> agreements.findById(agreement.getId())).orElseThrow();
		BrokerageAgreement second = tx(() -> agreements.findById(agreement.getId())).orElseThrow();
		VersionTerms firstTerms = new VersionTerms(8_000_000L, null, null, null, null);
		VersionTerms secondTerms = new VersionTerms(9_000_000L, null, null, null, null);

		tx(() -> {
			agreements.update(first.editDraft(PERIOD, PhotoConsent.NONE, firstTerms, BrokerageFee.NONE, now()));
			return null;
		});

		assertThrows(ConcurrencyFailureException.class, () -> tx(() -> {
			agreements.update(second.editDraft(PERIOD, PhotoConsent.NONE, secondTerms, BrokerageFee.NONE, now()));
			return null;
		}));
		BrokerageAgreement stored = tx(() -> agreements.findById(agreement.getId())).orElseThrow();
		assertEquals(firstTerms, stored.getVersions().get(0).getTerms());
		assertEquals(1L, stored.getRowVersionOrNull());
	}

	@Test
	void contentChecks_refuseValuesOutsideTheRules() {
		String agreementId = AgreementFixture.insert(jdbc, ids, ids.generateId(), ids.generateId(), "DRAFTING",
				"DRAFT");
		created.add(agreementId);
		String versionId = jdbc.queryForObject("SELECT id FROM brokerage_agreement_versions WHERE agreement_id = ?",
				String.class, agreementId);

		assertRefused("ck_brokerage_agreement_versions_exterior_photo", () -> setVersion(versionId,
				"photo_consent = NULL, exterior_photo_consent = TRUE"));
		assertRefused("ck_brokerage_agreement_versions_exterior_photo", () -> setVersion(versionId,
				"photo_consent = FALSE, exterior_photo_consent = TRUE"));
		assertRefused("ck_brokerage_agreement_versions_monthly_rent", () -> setVersion(versionId,
				"monthly_rent_amount = 0"));
		assertRefused("ck_brokerage_agreement_versions_deposit", () -> setVersion(versionId,
				"deposit_basis = 'MONTHS', deposit_months = 2, deposit_amount = 1000"));
		assertRefused("ck_brokerage_agreement_versions_deposit", () -> setVersion(versionId,
				"deposit_basis = 'MONTHS', deposit_months = 13"));
		assertRefused("ck_brokerage_agreement_versions_deposit", () -> setVersion(versionId,
				"deposit_basis = 'AMOUNT', deposit_months = 2, deposit_amount = 1000"));
		assertRefused("ck_brokerage_agreement_versions_deposit", () -> setVersion(versionId,
				"deposit_basis = 'AMOUNT', deposit_amount = -1"));
		assertRefused("ck_brokerage_agreement_versions_deposit", () -> setVersion(versionId,
				"deposit_basis = 'MONTHS'"));
		assertRefused("ck_brokerage_agreement_versions_deposit", () -> setVersion(versionId,
				"deposit_basis = 'AMOUNT'"));
		assertRefused("ck_brokerage_agreement_versions_deposit", () -> setVersion(versionId, "deposit_months = 2"));
		assertRefused("ck_brokerage_agreement_versions_deposit", () -> setVersion(versionId,
				"deposit_basis = 'WEEKS', deposit_months = 2"));
		assertRefused("ck_brokerage_agreement_versions_minimum_term", () -> setVersion(versionId,
				"minimum_term_months = 121"));
		assertRefused("ck_brokerage_agreement_versions_minimum_term", () -> setVersion(versionId,
				"minimum_term_months = 0"));
		assertRefused("ck_brokerage_agreement_versions_payment_period", () -> setVersion(versionId,
				"payment_period_months = 13"));
		assertRefused("ck_brokerage_agreement_versions_payment_period", () -> setVersion(versionId,
				"payment_period_months = 0"));
		assertRefused("ck_brokerage_agreement_version_fees_amount",
				() -> insertFee(versionId, 1, "Electricity", "AMOUNT", 3_500L, null));
		assertRefused("ck_brokerage_agreement_version_fees_amount",
				() -> insertFee(versionId, 1, "Electricity", "AMOUNT", 0L, "PER_KWH"));
		assertRefused("ck_brokerage_agreement_version_fees_amount",
				() -> insertFee(versionId, 1, "Electricity", "AMOUNT", null, "PER_KWH"));
		assertRefused("ck_brokerage_agreement_version_fees_amount",
				() -> insertFee(versionId, 1, "Water", "INCLUDED", 3_500L, null));
		assertRefused("ck_brokerage_agreement_version_fees_amount",
				() -> insertFee(versionId, 1, "Water", "PROVIDER_TARIFF", null, "PER_M3"));
		assertRefused("ck_brokerage_agreement_version_fees_number",
				() -> insertFee(versionId, 21, "Water", "INCLUDED", null, null));
		assertRefused("ck_brokerage_agreement_version_fees_number",
				() -> insertFee(versionId, 0, "Water", "INCLUDED", null, null));
		assertRefused("ck_brokerage_agreement_version_fees_basis",
				() -> insertFee(versionId, 1, "Parking", "NOT_OFFERED", null, null));
		assertRefused("ck_brokerage_agreement_version_fees_charge_unit",
				() -> insertFee(versionId, 1, "Water", "AMOUNT", 3_500L, "PER_WEEK"));
		assertDoesNotThrow(() -> setVersion(versionId, "photo_consent = TRUE, exterior_photo_consent = TRUE,"
				+ " monthly_rent_amount = 999999999999999, deposit_basis = 'AMOUNT', deposit_amount = 0,"
				+ " minimum_term_months = 120, payment_period_months = 12"));
		assertDoesNotThrow(() -> insertFee(versionId, 20, "Water", "AMOUNT", 999_999_999_999_999L, "ONE_OFF"));
	}

	@Test
	void versionDelete_cascadesToItsFees() {
		String agreementId = AgreementFixture.insert(jdbc, ids, ids.generateId(), ids.generateId(), "DRAFTING",
				"DRAFT");
		created.add(agreementId);
		String versionId = jdbc.queryForObject("SELECT id FROM brokerage_agreement_versions WHERE agreement_id = ?",
				String.class, agreementId);
		insertFee(versionId, 1, "Water", "INCLUDED", null, null);
		insertFee(versionId, 2, "Internet", "PROVIDER_TARIFF", null, null);

		jdbc.update("DELETE FROM brokerage_agreement_versions WHERE id = ?", versionId);

		assertEquals(List.of(), feeRows(versionId));
	}

	/** spec S3-D9: a version stored before E05-S3 (E05-S2's drafts on staging) reads as nothing entered yet. */
	@Test
	void storedRowWithoutContent_readsNothingEntered() {
		String agreementId = AgreementFixture.insert(jdbc, ids, ids.generateId(), ids.generateId(), "DRAFTING",
				"DRAFT");
		created.add(agreementId);

		AgreementVersion version = tx(() -> agreements.findById(agreementId)).orElseThrow().getVersions().get(0);

		assertEquals(PhotoConsent.NONE, version.getPhotoConsent());
		assertEquals(VersionTerms.NONE, version.getTerms());
		assertEquals(AdvertisingScope.SUNVIBE_CHANNELS, version.getAdvertisingScope());
	}

	// ==================== E05-S4: the fee policy, its tiers and the sending time (SD-12) ====================

	private static final BrokerageFee FEE_POLICY = new BrokerageFee(
			FeeFormula.of(FeeFormulaType.PERCENT_OF_RENT, List.of(new FeeTier(1, 50), new FeeTier(12, 100)), null, null),
			7, FeeFormula.of(FeeFormulaType.PERCENT_OF_RENT, List.of(new FeeTier(1, 30)), null, null),
			new EarlyLeaseEndRefund(EarlyLeaseEndType.PARTIAL_REFUND, 50, 3), 90);

	@Test
	void feeChecks_refuseOutOfRuleRows() {
		String agreementId = AgreementFixture.insert(jdbc, ids, ids.generateId(), ids.generateId(), "DRAFTING",
				"DRAFT");
		created.add(agreementId);
		String versionId = jdbc.queryForObject("SELECT id FROM brokerage_agreement_versions WHERE agreement_id = ?",
				String.class, agreementId);

		String formula = "ck_brokerage_agreement_versions_fee_formula";
		assertRefused(formula, () -> setVersion(versionId,
				"fee_formula_type = 'PERCENT_OF_RENT', fee_fixed_amount = 1000"));
		assertRefused(formula, () -> setVersion(versionId,
				"fee_formula_type = 'PERCENT_OF_RENT', fee_free_reason = 'Reason'"));
		assertRefused(formula, () -> setVersion(versionId, "fee_formula_type = 'FIXED_AMOUNT', fee_fixed_amount = 0"));
		assertRefused(formula, () -> setVersion(versionId, "fee_formula_type = 'FIXED_AMOUNT'"));
		assertRefused(formula, () -> setVersion(versionId,
				"fee_formula_type = 'FIXED_AMOUNT', fee_fixed_amount = 1000, fee_free_reason = 'Reason'"));
		assertRefused(formula, () -> setVersion(versionId, "fee_formula_type = 'FREE'"));
		assertRefused(formula, () -> setVersion(versionId,
				"fee_formula_type = 'FREE', fee_free_reason = 'Reason', fee_fixed_amount = 1000"));
		assertRefused(formula, () -> setVersion(versionId, "fee_formula_type = 'NOT_CHARGED'"));
		assertRefused(formula, () -> setVersion(versionId, "fee_formula_type = 'MONTHLY'"));
		assertRefused(formula, () -> setVersion(versionId, "fee_fixed_amount = 1000"));
		assertRefused(formula, () -> setVersion(versionId, "fee_free_reason = 'Reason'"));
		assertRefused("ck_brokerage_agreement_versions_fee_due_days", () -> setVersion(versionId, "fee_due_days = 31"));
		assertRefused("ck_brokerage_agreement_versions_fee_due_days", () -> setVersion(versionId, "fee_due_days = -1"));
		String renewal = "ck_brokerage_agreement_versions_renewal_fee";
		assertRefused(renewal, () -> setVersion(versionId, "renewal_fee_type = 'FIXED_AMOUNT'"));
		assertRefused(renewal, () -> setVersion(versionId,
				"renewal_fee_type = 'FIXED_AMOUNT', renewal_fee_fixed_amount = 0"));
		assertRefused(renewal, () -> setVersion(versionId,
				"renewal_fee_type = 'NOT_CHARGED', renewal_fee_fixed_amount = 1000"));
		assertRefused(renewal, () -> setVersion(versionId,
				"renewal_fee_type = 'PERCENT_OF_RENT', renewal_fee_fixed_amount = 1000"));
		assertRefused(renewal, () -> setVersion(versionId, "renewal_fee_type = 'FREE'"));
		assertRefused(renewal, () -> setVersion(versionId, "renewal_fee_fixed_amount = 1000"));
		String early = "ck_brokerage_agreement_versions_early_lease_end";
		assertRefused(early, () -> setVersion(versionId,
				"early_lease_end_type = 'PARTIAL_REFUND', early_lease_end_refund_percent = 50"));
		assertRefused(early, () -> setVersion(versionId,
				"early_lease_end_type = 'PARTIAL_REFUND', early_lease_end_within_months = 3"));
		assertRefused(early, () -> setVersion(versionId, "early_lease_end_type = 'PARTIAL_REFUND',"
				+ " early_lease_end_refund_percent = 101, early_lease_end_within_months = 3"));
		assertRefused(early, () -> setVersion(versionId, "early_lease_end_type = 'PARTIAL_REFUND',"
				+ " early_lease_end_refund_percent = 50, early_lease_end_within_months = 13"));
		assertRefused(early, () -> setVersion(versionId,
				"early_lease_end_type = 'NO_REFUND', early_lease_end_refund_percent = 50"));
		assertRefused(early, () -> setVersion(versionId, "early_lease_end_within_months = 3"));
		assertRefused("ck_brokerage_agreement_versions_protection_days",
				() -> setVersion(versionId, "protection_days = 181"));
		assertRefused("ck_brokerage_agreement_versions_protection_days",
				() -> setVersion(versionId, "protection_days = -1"));
		assertRefused("ck_brokerage_agreement_version_fee_tiers_percent", () -> insertTier(versionId, false, 1, 1, 0));
		assertRefused("ck_brokerage_agreement_version_fee_tiers_percent", () -> insertTier(versionId, false, 1, 1, 301));
		assertRefused("ck_brokerage_agreement_version_fee_tiers_from_months",
				() -> insertTier(versionId, false, 1, 0, 50));
		assertRefused("ck_brokerage_agreement_version_fee_tiers_from_months",
				() -> insertTier(versionId, false, 1, 121, 50));
		assertRefused("ck_brokerage_agreement_version_fee_tiers_number", () -> insertTier(versionId, false, 0, 1, 50));
		assertRefused("ck_brokerage_agreement_version_fee_tiers_number", () -> insertTier(versionId, true, 6, 1, 50));
		assertRefused("ck_brokerage_agreement_versions_sent", () -> setVersion(versionId, "sent_at = now()"));

		assertDoesNotThrow(() -> setVersion(versionId, "fee_formula_type = 'FIXED_AMOUNT',"
				+ " fee_fixed_amount = 999999999999999, fee_due_days = 0, renewal_fee_type = 'FIXED_AMOUNT',"
				+ " renewal_fee_fixed_amount = 1, early_lease_end_type = 'PARTIAL_REFUND',"
				+ " early_lease_end_refund_percent = 100, early_lease_end_within_months = 12, protection_days = 180"));
		assertDoesNotThrow(() -> setVersion(versionId, "fee_formula_type = 'FREE', fee_fixed_amount = NULL,"
				+ " fee_free_reason = 'First unit', fee_due_days = 30, renewal_fee_type = 'NOT_CHARGED',"
				+ " renewal_fee_fixed_amount = NULL, early_lease_end_type = 'NO_REFUND',"
				+ " early_lease_end_refund_percent = NULL, early_lease_end_within_months = NULL, protection_days = 0"));
		assertDoesNotThrow(() -> insertTier(versionId, false, 5, 120, 300));
		assertDoesNotThrow(() -> insertTier(versionId, true, 1, 1, 1));
		// no unique index over the starts (spec S4-D4): the aggregate holds their order
		assertDoesNotThrow(() -> insertTier(versionId, false, 4, 120, 10));
		assertDoesNotThrow(() -> setVersion(versionId, "status = 'AWAITING_OWNER', sent_at = now()"));
	}

	@Test
	void feeTiers_deletedWithTheirVersion() {
		String agreementId = AgreementFixture.insert(jdbc, ids, ids.generateId(), ids.generateId(), "DRAFTING",
				"DRAFT");
		created.add(agreementId);
		String versionId = jdbc.queryForObject("SELECT id FROM brokerage_agreement_versions WHERE agreement_id = ?",
				String.class, agreementId);
		insertTier(versionId, false, 1, 1, 50);
		insertTier(versionId, false, 2, 12, 100);
		insertTier(versionId, true, 1, 1, 30);

		jdbc.update("DELETE FROM brokerage_agreement_versions WHERE id = ?", versionId);

		assertEquals(List.of(), tierRows(versionId));
	}

	@Test
	void update_feePolicyWithTiers_readsBackEqual() {
		Instant created = Instant.parse("2026-11-20T03:15:00.123456Z");
		Instant edited = Instant.parse("2026-11-21T04:00:00.654321Z");
		Instant sent = Instant.parse("2026-11-22T05:30:00.987654Z");
		BrokerageAgreement agreement = draft(ids.generateId(), "P101", created);
		tx(() -> {
			agreements.create(agreement);
			return null;
		});
		VersionTerms terms = new VersionTerms(8_000_000L, new TermsDeposit(DepositBasis.MONTHS, 2, null), 12, 1, List.of(
				new MandatoryFee("Electricity", FeeBasis.AMOUNT, 3_500L, ChargeUnit.PER_KWH)));
		PhotoConsent consent = new PhotoConsent(true, false);

		tx(() -> {
			BrokerageAgreement read = agreements.findById(agreement.getId()).orElseThrow();
			agreements.update(read.editDraft(PERIOD, consent, terms, FEE_POLICY, edited));
			return null;
		});
		AgreementVersion version = tx(() -> agreements.findById(agreement.getId())).orElseThrow().getVersions().get(0);
		String versionId = version.getId();

		assertEquals(FEE_POLICY, version.getFee());
		assertEquals(terms, version.getTerms());
		assertEquals(edited, version.getUpdatedAt());
		assertNull(version.getSentAtOrNull());
		assertEquals(List.of("false|1|1|50", "false|2|12|100", "true|1|1|30"), tierRows(versionId));
		Map<String, Object> row = jdbc.queryForMap("SELECT * FROM brokerage_agreement_versions WHERE id = ?", versionId);
		assertEquals("PERCENT_OF_RENT", row.get("fee_formula_type"));
		assertNull(row.get("fee_fixed_amount"));
		assertNull(row.get("fee_free_reason"));
		assertEquals(7, row.get("fee_due_days"));
		assertEquals("PERCENT_OF_RENT", row.get("renewal_fee_type"));
		assertNull(row.get("renewal_fee_fixed_amount"));
		assertEquals("PARTIAL_REFUND", row.get("early_lease_end_type"));
		assertEquals(50, row.get("early_lease_end_refund_percent"));
		assertEquals(3, row.get("early_lease_end_within_months"));
		assertEquals(90, row.get("protection_days"));
		assertNull(row.get("sent_at"));

		BrokerageFee other = new BrokerageFee(FeeFormula.of(FeeFormulaType.FREE, null, null, "First unit"), 0,
				FeeFormula.of(FeeFormulaType.FIXED_AMOUNT, null, 999_999_999_999_999L, null),
				new EarlyLeaseEndRefund(EarlyLeaseEndType.NO_REFUND, null, null), 0);
		editFee(agreement.getId(), other);
		assertEquals(other, tx(() -> agreements.findById(agreement.getId())).orElseThrow().getVersions().get(0).getFee());
		assertEquals(List.of(), tierRows(versionId));

		editFee(agreement.getId(), FEE_POLICY);
		tx(() -> {
			agreements.update(agreements.findById(agreement.getId()).orElseThrow().sendDraft(sent));
			return null;
		});
		BrokerageAgreement read = tx(() -> agreements.findById(agreement.getId())).orElseThrow();
		AgreementVersion sentVersion = read.getVersions().get(0);

		assertEquals(AgreementStatus.AWAITING_CONFIRMATION, read.getStatus());
		assertEquals(sent, read.getUpdatedAt());
		assertEquals(VersionStatus.AWAITING_OWNER, sentVersion.getStatus());
		assertEquals(sent, sentVersion.getSentAtOrNull());
		assertEquals(sent, sentVersion.getUpdatedAt());
		assertEquals(FEE_POLICY, sentVersion.getFee());
		assertEquals(terms, sentVersion.getTerms());
		assertEquals(consent, sentVersion.getPhotoConsent());
		assertEquals(List.of("false|1|1|50", "false|2|12|100", "true|1|1|30"), tierRows(versionId));
		assertEquals(List.of("1|Electricity|AMOUNT|3500|PER_KWH"), feeRows(versionId));
		assertEquals(sent, jdbc.queryForObject("SELECT sent_at FROM brokerage_agreement_versions WHERE id = ?",
				Timestamp.class, versionId).toInstant());
	}

	/** testing.md: the save of a loaded aggregate, its child rows seeded first. */
	@Test
	void update_seededTiersAndFees_replacesTiersKeepsFees() {
		BrokerageAgreement agreement = draft(ids.generateId(), "P101", now().minusSeconds(60));
		BrokerageAgreement other = draft(ids.generateId(), "P102", now().minusSeconds(60));
		tx(() -> {
			agreements.create(agreement);
			agreements.create(other);
			return null;
		});
		String versionId = agreement.getVersions().get(0).getId();
		String otherVersionId = other.getVersions().get(0).getId();
		for (String id : List.of(versionId, otherVersionId)) {
			seedPercentPolicy(id, List.of(1, 12, 24), List.of(1, 6));
			jdbc.update("UPDATE brokerage_agreement_versions SET mandatory_fees_stated = TRUE WHERE id = ?", id);
			insertFee(id, 1, "Electricity", "AMOUNT", 3_500L, "PER_KWH");
			insertFee(id, 2, "Water", "PROVIDER_TARIFF", null, null);
		}
		AgreementVersion seeded = tx(() -> agreements.findById(agreement.getId())).orElseThrow().getVersions().get(0);
		assertEquals(List.of(1, 12, 24), seeded.getFee().formulaOrNull().tiers().stream().map(FeeTier::fromMonths)
				.toList());
		assertEquals(List.of(1, 6), seeded.getFee().renewalOrNull().tiers().stream().map(FeeTier::fromMonths).toList());
		Map<String, Object> versionBefore = versionIdentity(versionId);
		Map<String, Object> otherBefore = agreementRow(other.getId());
		List<String> otherTiers = tierRows(otherVersionId);
		List<String> otherFees = feeRows(otherVersionId);

		editFee(agreement.getId(), new BrokerageFee(
				FeeFormula.of(FeeFormulaType.PERCENT_OF_RENT, List.of(new FeeTier(1, 60)), null, null), 7,
				FeeFormula.of(FeeFormulaType.NOT_CHARGED, null, null, null), null, null));

		assertEquals(List.of("false|1|1|60"), tierRows(versionId));
		assertEquals(List.of("1|Electricity|AMOUNT|3500|PER_KWH", "2|Water|PROVIDER_TARIFF|null|null"),
				feeRows(versionId));
		assertEquals(versionBefore, versionIdentity(versionId));
		assertEquals(otherBefore, agreementRow(other.getId()));
		assertEquals(otherTiers, tierRows(otherVersionId));
		assertEquals(otherFees, feeRows(otherVersionId));
	}

	/** spec S4-D4: an ordinary edit moves a start from one row to another inside one flush; no unique index refuses it. */
	@Test
	void update_tierStartsMovedBetweenRows_exactlyTheNewList() {
		List<List<List<Integer>>> cases = List.of(
				List.of(List.of(1, 12), List.of(1, 6, 12)),
				List.of(List.of(1, 6, 12), List.of(1, 12, 24)),
				List.of(List.of(1, 6, 12), List.of(1, 12)));
		for (List<List<Integer>> movement : cases) {
			BrokerageAgreement agreement = draft(ids.generateId(), "P101", now().minusSeconds(60));
			tx(() -> {
				agreements.create(agreement);
				return null;
			});
			String versionId = agreement.getVersions().get(0).getId();
			seedPercentPolicy(versionId, movement.get(0), List.of());
			List<FeeTier> saved = movement.get(1).stream().map(start -> new FeeTier(start, start + 40)).toList();

			editFee(agreement.getId(), new BrokerageFee(FeeFormula.of(FeeFormulaType.PERCENT_OF_RENT, saved, null, null),
					7, null, null, null));

			List<String> expected = new ArrayList<>();
			for (int index = 0; index < saved.size(); index++) {
				expected.add("false|" + (index + 1) + "|" + saved.get(index).fromMonths() + "|"
						+ saved.get(index).percent());
			}
			assertEquals(expected, tierRows(versionId), movement.toString());
			assertEquals(saved, tx(() -> agreements.findById(agreement.getId())).orElseThrow().getVersions().get(0)
					.getFee().formulaOrNull().tiers(), movement.toString());
		}
	}

	@Test
	void update_feePolicyStaleRowVersion_fails() {
		BrokerageAgreement agreement = draft(ids.generateId(), "P101", now().minusSeconds(60));
		tx(() -> {
			agreements.create(agreement);
			return null;
		});
		BrokerageAgreement first = tx(() -> agreements.findById(agreement.getId())).orElseThrow();
		BrokerageAgreement second = tx(() -> agreements.findById(agreement.getId())).orElseThrow();
		BrokerageFee secondFee = new BrokerageFee(FeeFormula.of(FeeFormulaType.FIXED_AMOUNT, null, 2_000_000L, null),
				null, null, null, null);

		tx(() -> {
			agreements.update(first.editDraft(PERIOD, PhotoConsent.NONE, VersionTerms.NONE, FEE_POLICY, now()));
			return null;
		});

		assertThrows(ConcurrencyFailureException.class, () -> tx(() -> {
			agreements.update(second.editDraft(PERIOD, PhotoConsent.NONE, VersionTerms.NONE, secondFee, now()));
			return null;
		}));
		BrokerageAgreement stored = tx(() -> agreements.findById(agreement.getId())).orElseThrow();
		assertEquals(FEE_POLICY, stored.getVersions().get(0).getFee());
		assertEquals(1L, stored.getRowVersionOrNull());
		assertEquals(List.of("false|1|1|50", "false|2|12|100", "true|1|1|30"),
				tierRows(agreement.getVersions().get(0).getId()));
	}

	/** spec S4-D4: a version stored before E05-S4 reads as no fee policy entered and never sent. */
	@Test
	void storedRowWithoutFeePolicy_readsNothingEnteredNotSent() {
		String agreementId = AgreementFixture.insert(jdbc, ids, ids.generateId(), ids.generateId(), "DRAFTING",
				"DRAFT");
		created.add(agreementId);

		AgreementVersion version = tx(() -> agreements.findById(agreementId)).orElseThrow().getVersions().get(0);

		assertEquals(BrokerageFee.NONE, version.getFee());
		assertNull(version.getSentAtOrNull());
	}

	/** Reads the agreement, replaces its draft's fee policy (the rest as stored) and writes it in one transaction. */
	private void editFee(String agreementId, BrokerageFee fee) {
		tx(() -> {
			BrokerageAgreement read = agreements.findById(agreementId).orElseThrow();
			AgreementVersion draft = read.draft().orElseThrow();
			agreements.update(read.editDraft(draft.getPeriod(), draft.getPhotoConsent(), draft.getTerms(), fee, now()));
			return null;
		});
	}

	/** Seeds a percentage fee with these starts (percent = start + 10) and, when given, a percentage renewal. */
	private void seedPercentPolicy(String versionId, List<Integer> feeStarts, List<Integer> renewalStarts) {
		setVersion(versionId, "fee_formula_type = 'PERCENT_OF_RENT', fee_due_days = 7, renewal_fee_type = "
				+ (renewalStarts.isEmpty() ? "NULL" : "'PERCENT_OF_RENT'") + ", early_lease_end_type = 'NO_REFUND',"
				+ " protection_days = 30");
		for (int index = 0; index < feeStarts.size(); index++) {
			insertTier(versionId, false, index + 1, feeStarts.get(index), feeStarts.get(index) + 10);
		}
		for (int index = 0; index < renewalStarts.size(); index++) {
			insertTier(versionId, true, index + 1, renewalStarts.get(index), renewalStarts.get(index) + 5);
		}
	}

	private void insertTier(String versionId, boolean renewal, int tierNo, int fromMonths, int percent) {
		jdbc.update("INSERT INTO brokerage_agreement_version_fee_tiers (version_id, renewal, tier_no, from_months,"
				+ " percent) VALUES (?, ?, ?, ?, ?)", versionId, renewal, tierNo, fromMonths, percent);
	}

	/** The version's tier rows as "renewal|tier_no|from_months|percent", the fee's first, by number. */
	private List<String> tierRows(String versionId) {
		return jdbc.query("SELECT renewal, tier_no, from_months, percent FROM brokerage_agreement_version_fee_tiers"
				+ " WHERE version_id = ? ORDER BY renewal, tier_no", (row, index) -> row.getBoolean("renewal") + "|"
						+ row.getInt("tier_no") + "|" + row.getInt("from_months") + "|" + row.getInt("percent"),
				versionId);
	}

	/** Reads the agreement, replaces its draft's fee list (the rest as stored) and writes it in one transaction. */
	private void edit(String agreementId, List<MandatoryFee> feesOrNull) {
		tx(() -> {
			BrokerageAgreement read = agreements.findById(agreementId).orElseThrow();
			AgreementVersion draft = read.draft().orElseThrow();
			agreements.update(read.editDraft(draft.getPeriod(), draft.getPhotoConsent(),
					new VersionTerms(null, null, null, null, feesOrNull), BrokerageFee.NONE, now()));
			return null;
		});
	}

	private void insertFee(String versionId, int feeNo, String name, String basis, Long amount, String chargeUnit) {
		jdbc.update("INSERT INTO brokerage_agreement_version_fees (version_id, fee_no, name, basis, amount, charge_unit)"
				+ " VALUES (?, ?, ?, ?, ?, ?)", versionId, feeNo, name, basis,
				amount == null ? null : BigDecimal.valueOf(amount), chargeUnit);
	}

	private void setVersion(String versionId, String assignments) {
		jdbc.update("UPDATE brokerage_agreement_versions SET " + assignments + " WHERE id = ?", versionId);
	}

	/** The version's fee rows as "fee_no|name|basis|amount|charge_unit", by number. */
	private List<String> feeRows(String versionId) {
		return jdbc.query("SELECT fee_no, name, basis, amount, charge_unit FROM brokerage_agreement_version_fees"
				+ " WHERE version_id = ? ORDER BY fee_no", (row, index) -> row.getInt("fee_no") + "|"
						+ row.getString("name") + "|" + row.getString("basis") + "|"
						+ (row.getBigDecimal("amount") == null ? "null" : row.getBigDecimal("amount").toPlainString())
						+ "|" + row.getString("charge_unit"), versionId);
	}

	private boolean feesStated(String versionId) {
		return jdbc.queryForObject("SELECT mandatory_fees_stated FROM brokerage_agreement_versions WHERE id = ?",
				Boolean.class, versionId);
	}

	private Map<String, Object> agreementRow(String agreementId) {
		return new HashMap<>(jdbc.queryForMap("SELECT * FROM brokerage_agreements WHERE id = ?",
				agreementId));
	}

	private Map<String, Object> versionIdentity(String versionId) {
		return jdbc.queryForMap("SELECT id, agreement_id, version_no, status, basis_lead_id, basis_intake_id,"
				+ " created_by_user_id, created_at FROM brokerage_agreement_versions WHERE id = ?", versionId);
	}

	private BrokerageAgreement draft(String unitId, String unitCodeOrNull, Instant now) {
		String lead = ids.generateId();
		BrokerageAgreement agreement = BrokerageAgreement.create(ids.generateId(), unitId, unitCodeOrNull,
				ids.generateId(), TransactionType.LONG_TERM_RENT, ids.generateId(), lead, ids.generateId(),
				ids.generateId(), PERIOD, ids.generateId(), now);
		created.add(agreement.getId());
		return agreement;
	}

	private void insertAgreement(String unitId, String status, String transactionType) {
		String agreementId = ids.generateId();
		Timestamp now = Timestamp.from(now());
		created.add(agreementId);
		jdbc.update("INSERT INTO brokerage_agreements (id, unit_id, property_id, transaction_type, owner_user_id,"
				+ " basis_lead_id, status, created_by_user_id, created_at, updated_at) VALUES (?, ?, 'P-FIXTURE', ?, ?,"
				+ " ?, ?, ?, ?, ?)", agreementId, unitId, transactionType, AgreementFixture.FIXTURE_USER,
				ids.generateId(), status, AgreementFixture.FIXTURE_USER, now, now);
	}

	private void insertVersion(String agreementId, int versionNo, String status, String start, String end) {
		Timestamp now = Timestamp.from(now());
		jdbc.update("INSERT INTO brokerage_agreement_versions (id, agreement_id, version_no, status, start_date,"
				+ " end_date, basis_lead_id, basis_intake_id, created_by_user_id, created_at, updated_at)"
				+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", ids.generateId(), agreementId, versionNo, status,
				Date.valueOf(start), Date.valueOf(end), ids.generateId(), AgreementFixture.FIXTURE_INTAKE,
				AgreementFixture.FIXTURE_USER, now, now);
	}

	private static void assertRefused(String constraint, Runnable insert) {
		DataIntegrityViolationException refused = assertThrows(DataIntegrityViolationException.class, insert::run,
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
