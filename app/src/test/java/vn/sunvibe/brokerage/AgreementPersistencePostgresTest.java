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
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementVersion;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.ChargeUnit;
import vn.sunvibe.brokerage.core.domain.model.agreement.DepositBasis;
import vn.sunvibe.brokerage.core.domain.model.agreement.FeeBasis;
import vn.sunvibe.brokerage.core.domain.model.agreement.MandatoryFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.PhotoConsent;
import vn.sunvibe.brokerage.core.domain.model.agreement.TermsDeposit;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
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
