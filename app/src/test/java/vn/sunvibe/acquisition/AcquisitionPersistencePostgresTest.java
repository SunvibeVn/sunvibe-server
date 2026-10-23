package vn.sunvibe.acquisition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManagerFactory;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.acquisition.core.application.shared.lock.IntakeLockPort;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivity;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentChannel;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentPurpose;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentRecord;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentRecordId;
import vn.sunvibe.acquisition.core.domain.model.idempotency.IdempotencyRecord;
import vn.sunvibe.acquisition.core.domain.model.lead.AskingPrice;
import vn.sunvibe.acquisition.core.domain.model.lead.GeoPoint;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadPropertyType;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadSource;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.acquisition.core.domain.model.lead.PhoneNumber;
import vn.sunvibe.acquisition.core.domain.model.lead.PriceUnit;
import vn.sunvibe.acquisition.core.domain.model.lead.RequestCode;
import vn.sunvibe.acquisition.core.domain.model.territory.SaleTerritory;
import vn.sunvibe.acquisition.core.domain.repository.ConsentRecordDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.DuplicateCandidateDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.IdempotencyKeyDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadActivityDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.SaleTerritoryDomainRepository;

/**
 * The acquisition adapters on a real PostgreSQL 17 (acquisition-owner-intake spec 5.2, lead-queue spec 4): every
 * column of every table written through its domain repository and read back through the repository and with plain
 * SQL, the lookups of the duplicate check, the optimistic lock, the idempotency cleanup and the advisory locks. The
 * schema comes from Flyway only and Hibernate validates it; the annotations are those of
 * {@link vn.sunvibe.FlywayPostgresMigrationTest}, so both share one context. Needs Docker, like that test.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class AcquisitionPersistencePostgresTest {

	/** Whole microseconds: PostgreSQL keeps no more, so a stored instant can be compared with the one saved. */
	private static final Instant T0 = Instant.parse("2026-10-16T08:00:00.123456Z");
	private static final Instant T1 = Instant.parse("2026-10-17T09:30:00.654321Z");
	private static final String ASSIGNEE = "01SALE00000000000000000000";
	private static final String OTHER_USER = "01LEADER000000000000000000";
	private static final String HASH = "a".repeat(64);
	private static final SecureRandom RANDOM = new SecureRandom();

	@Autowired
	private OwnerLeadDomainRepository leads;

	@Autowired
	private ConsentRecordDomainRepository consents;

	@Autowired
	private OwnerLeadActivityDomainRepository activities;

	@Autowired
	private DuplicateCandidateDomainRepository candidates;

	@Autowired
	private IdempotencyKeyDomainRepository idempotencyKeys;

	@Autowired
	private SaleTerritoryDomainRepository territories;

	@Autowired
	private IntakeLockPort locks;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@BeforeEach
	@AfterEach
	void cleanAcquisitionTables() {
		jdbc.update("DELETE FROM owner_intake_idempotency_keys");
		jdbc.update("DELETE FROM owner_lead_duplicate_candidates");
		jdbc.update("DELETE FROM owner_lead_intent_changes");
		jdbc.update("DELETE FROM owner_lead_activities");
		jdbc.update("DELETE FROM owner_lead_access_logs");
		jdbc.update("DELETE FROM owner_leads");
		jdbc.update("DELETE FROM consent_records");
		jdbc.update("DELETE FROM sale_territories");
	}

	@AfterEach
	void stopStatistics() {
		entityManagerFactory.unwrap(SessionFactory.class).getStatistics().setStatisticsEnabled(false);
	}

	// ==================== lead ====================

	@Test
	void save_leadWithEveryOptionalField_roundTripsEveryColumn() {
		ConsentRecordId consentId = saveConsent();
		OwnerLeadId duplicateOf = saveLead(plainLead("0900000001", "10 a", OwnerLeadStatus.NEW, T0)).getId();
		String snapshot = "{\"contactName\":\"Nguyễn Văn Á\",\"notes\":\"" + "x".repeat(3000) + "\"}";
		OwnerLead lead = OwnerLead.load(OwnerLeadId.generate(), newCode(), OwnerLeadStatus.ASSIGNED,
				OwnerLeadIntent.LONG_TERM_RENT, OwnerLeadSource.REFERRAL, "Nguyễn Văn Á",
				PhoneNumber.parse("0901234567"), "owner@example.com", "48", "Đà Nẵng", "20194", "Hải Châu",
				"12 Bạch  Đằng", OwnerLead.addressKey("12 Bạch  Đằng"),
				GeoPoint.of(new BigDecimal("16.054407"), new BigDecimal("108.202167"), false),
				OwnerLeadPropertyType.APARTMENT,
				Optional.of(AskingPrice.of(12_500_000L, "VND", PriceUnit.MONTH, OwnerLeadIntent.LONG_TERM_RENT)),
				LocalDate.parse("2026-11-01"), "Near the river", "After 18:00", consentId, snapshot, ASSIGNEE,
				T1, duplicateOf, "Because", T0, T1, null, 1);

		leads.save(lead);
		OwnerLead stored = leads.findById(lead.getId()).orElseThrow();

		assertSameLead(lead, stored);
		assertEquals(0L, stored.getVersion());
		Map<String, Object> row = jdbc.queryForMap("SELECT * FROM owner_leads WHERE id = ?", lead.getId().getValue());
		assertEquals("ASSIGNED", row.get("status"));
		assertEquals("LONG_TERM_RENT", row.get("intent"));
		assertEquals("REFERRAL", row.get("source"));
		assertEquals("+84901234567", row.get("phone_normalized"));
		assertEquals("12 bạch đằng", row.get("address_key"));
		assertEquals(0, new BigDecimal("16.054407").compareTo((BigDecimal) row.get("latitude")));
		assertEquals(0, new BigDecimal("12500000").compareTo((BigDecimal) row.get("asking_price_amount")));
		assertEquals("MONTH", row.get("asking_price_unit"));
		assertEquals("APARTMENT", row.get("property_type"));
		assertEquals(snapshot, row.get("submission_snapshot"));
		assertEquals(duplicateOf.getValue(), row.get("duplicate_of_lead_id"));
	}

	@Test
	void save_leadWithNoOptionalFields_roundTripsNulls() {
		OwnerLead lead = plainLead("0901234567", "12 bach dang", OwnerLeadStatus.NEW, T0);

		leads.save(lead);
		OwnerLead stored = leads.findById(lead.getId()).orElseThrow();

		assertSameLead(lead, stored);
		assertTrue(stored.getGeoPoint().isEmpty());
		assertTrue(stored.getAskingPrice().isEmpty());
		assertNull(stored.getEmail());
		assertNull(stored.getPropertyType());
		assertNull(stored.getAvailableFrom());
		assertNull(stored.getNotes());
		assertNull(stored.getPreferredCallbackTime());
		assertNull(stored.getAssignedUserId());
		assertNull(stored.getNextActionAt());
		assertNull(stored.getDuplicateOfLeadId());
		assertNull(stored.getCloseReason());
	}

	@Test
	void existsByRequestCodeAndFindById_storedOrUnknown_findTheLeadOrReportNone() {
		OwnerLead lead = saveLead(plainLead("0901234567", "a", OwnerLeadStatus.NEW, T0));

		assertTrue(leads.existsByRequestCode(lead.getRequestCode().getValue()));
		assertEquals(lead.getRequestCode(), leads.findById(lead.getId()).orElseThrow().getRequestCode());
		assertFalse(leads.existsByRequestCode("SV-ZZZZ-ZZZZ"));
		assertTrue(leads.findById(OwnerLeadId.generate()).isEmpty());
	}

	@Test
	void save_existingLeadWithCurrentVersion_updatesTheRowAndBumpsTheVersion() {
		OwnerLead lead = saveLead(plainLead("0901234567", "a", OwnerLeadStatus.NEW, T0));
		OwnerLead loaded = leads.findById(lead.getId()).orElseThrow();

		leads.save(withStatus(loaded, OwnerLeadStatus.CONTACTED, loaded.getVersion()));

		OwnerLead stored = leads.findById(lead.getId()).orElseThrow();
		assertEquals(OwnerLeadStatus.CONTACTED, stored.getStatus());
		assertEquals(1L, stored.getVersion());
	}

	@Test
	void save_newLead_setsVersionZeroOnTheSameAggregate() {
		OwnerLead lead = plainLead("0901234567", "a", OwnerLeadStatus.NEW, T0);
		assertNull(lead.getVersion());

		leads.save(lead);

		assertEquals(0L, lead.getVersion());
	}

	@Test
	void save_updatedLoadedLead_setsTheNextVersionOnTheUpdatedAggregate() {
		OwnerLead lead = saveLead(plainLead("0901234567", "a", OwnerLeadStatus.NEW, T0));
		OwnerLead loaded = leads.findById(lead.getId()).orElseThrow();
		OwnerLead updated = withStatus(loaded, OwnerLeadStatus.CONTACTED, loaded.getVersion());

		leads.save(updated);

		assertEquals(loaded.getVersion() + 1, updated.getVersion());
		assertEquals(updated.getVersion(), leads.findById(lead.getId()).orElseThrow().getVersion());
	}

	@Test
	void save_sameAggregateTwice_secondSaveSucceedsAndKeepsOneRow() {
		OwnerLead lead = plainLead("0901234567", "a", OwnerLeadStatus.NEW, T0);
		leads.save(lead);

		leads.save(lead);
		OwnerLead updated = withStatus(lead, OwnerLeadStatus.CONTACTED, lead.getVersion());
		leads.save(updated);
		leads.save(updated);

		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM owner_leads", Integer.class));
		assertEquals(1L, updated.getVersion());
		assertEquals(1L, leads.findById(lead.getId()).orElseThrow().getVersion());
	}

	@Test
	void save_existingLeadWithStaleVersion_throwsOptimisticLockingFailure() {
		OwnerLead lead = saveLead(plainLead("0901234567", "a", OwnerLeadStatus.NEW, T0));
		OwnerLead loaded = leads.findById(lead.getId()).orElseThrow();
		jdbc.update("UPDATE owner_leads SET version = version + 1 WHERE id = ?", lead.getId().getValue());

		assertThrows(ObjectOptimisticLockingFailureException.class,
				() -> leads.save(withStatus(loaded, OwnerLeadStatus.CONTACTED, loaded.getVersion())));
		assertEquals("NEW", jdbc.queryForObject("SELECT status FROM owner_leads WHERE id = ?", String.class,
				lead.getId().getValue()));
	}

	@Test
	void findRecentSameSubmission_matchesOnAllFourKeysAndTheTimeBound() {
		OwnerLead lead = saveLead(plainLead("0901234567", "12 a", OwnerLeadStatus.NEW, T0));
		String phone = lead.getPhone().getValue();

		assertEquals(lead.getId(), leads.findRecentSameSubmission(phone, OwnerLeadIntent.LONG_TERM_RENT, "20194",
				"12 a", T0).orElseThrow().getId());
		assertEquals(lead.getId(), leads.findRecentSameSubmission(phone, OwnerLeadIntent.LONG_TERM_RENT, "20194",
				"12 a", T0.minusSeconds(60)).orElseThrow().getId());
		assertTrue(leads.findRecentSameSubmission(phone, OwnerLeadIntent.LONG_TERM_RENT, "20194", "12 a",
				T0.plusNanos(1000)).isEmpty());
		assertTrue(leads.findRecentSameSubmission("+84900000000", OwnerLeadIntent.LONG_TERM_RENT, "20194", "12 a",
				T0).isEmpty());
		assertTrue(leads.findRecentSameSubmission(phone, OwnerLeadIntent.SALE, "20194", "12 a", T0).isEmpty());
		assertTrue(leads.findRecentSameSubmission(phone, OwnerLeadIntent.LONG_TERM_RENT, "20195", "12 a", T0)
				.isEmpty());
		assertTrue(leads.findRecentSameSubmission(phone, OwnerLeadIntent.LONG_TERM_RENT, "20194", "13 a", T0)
				.isEmpty());
	}

	@Test
	void findRecentSameSubmission_severalMatches_returnsTheNewest() {
		saveLead(plainLead("0901234567", "12 a", OwnerLeadStatus.NEW, T0));
		OwnerLead newer = saveLead(plainLead("0901234567", "12 a", OwnerLeadStatus.NEW, T1));

		OwnerLead found = leads.findRecentSameSubmission("+84901234567", OwnerLeadIntent.LONG_TERM_RENT, "20194",
				"12 a", T0).orElseThrow();

		assertEquals(newer.getId(), found.getId());
	}

	// ==================== consent ====================

	@Test
	void save_consentRecord_roundTripsEveryColumn() {
		ConsentRecord consent = ConsentRecord.create(ConsentRecord.CURRENT_NOTICE_VERSION,
				ConsentPurpose.OWNER_INTAKE_CONTACT, ConsentChannel.WEB, HASH, "b".repeat(64), T0);

		consents.save(consent);
		ConsentRecord stored = consents.findById(consent.getId()).orElseThrow();

		assertEquals(consent.getId(), stored.getId());
		assertEquals(ConsentRecord.CURRENT_NOTICE_VERSION, stored.getNoticeVersion());
		assertEquals(ConsentPurpose.OWNER_INTAKE_CONTACT, stored.getPurpose());
		assertEquals(ConsentChannel.WEB, stored.getChannel());
		assertEquals(HASH, stored.getIpHash());
		assertEquals("b".repeat(64), stored.getUserAgentHash());
		assertEquals(T0, stored.getAcceptedAt());
		Map<String, Object> row = jdbc.queryForMap("SELECT * FROM consent_records WHERE id = ?",
				consent.getId().getValue());
		assertEquals("OWNER_INTAKE_CONTACT", row.get("purpose"));
		assertEquals("WEB", row.get("channel"));
	}

	@Test
	void save_consentRecordWithoutHashes_roundTripsNulls() {
		ConsentRecord consent = ConsentRecord.create(ConsentRecord.CURRENT_NOTICE_VERSION,
				ConsentPurpose.OWNER_INTAKE_CONTACT, ConsentChannel.WEB, null, null, T0);

		consents.save(consent);
		ConsentRecord stored = consents.findById(consent.getId()).orElseThrow();

		assertNull(stored.getIpHash());
		assertNull(stored.getUserAgentHash());
		assertTrue(consents.findById(ConsentRecordId.generate()).isEmpty());
	}

	// ==================== activity ====================

	@Test
	void save_activity_roundTripsEveryColumnAndListsOldestFirst() {
		OwnerLead lead = saveLead(plainLead("0901234567", "a", OwnerLeadStatus.NEW, T0));
		OwnerLeadActivity later = OwnerLeadActivity.load(202610160002L, lead.getId(),
				OwnerLeadActivityType.REASSIGNED, OTHER_USER, OwnerLeadStatus.ASSIGNED, OwnerLeadStatus.CONTACTED,
				ASSIGNEE, OTHER_USER, "Called the owner", T1, T1, null, null);
		OwnerLeadActivity earlier = OwnerLeadActivity.load(202610160001L, lead.getId(),
				OwnerLeadActivityType.SUBMITTED, null, null, null, null, null, null, null, T0, null, null);

		activities.save(later);
		activities.save(earlier);
		List<OwnerLeadActivity> stored = activities.findByLeadIdOrderByOccurredAt(lead.getId());

		assertEquals(List.of(202610160001L, 202610160002L), stored.stream().map(OwnerLeadActivity::getId).toList());
		OwnerLeadActivity first = stored.get(0);
		assertEquals(OwnerLeadActivityType.SUBMITTED, first.getType());
		assertNull(first.getActorUserId());
		assertNull(first.getFromStatus());
		assertNull(first.getNote());
		OwnerLeadActivity second = stored.get(1);
		assertEquals(lead.getId(), second.getLeadId());
		assertEquals(OwnerLeadActivityType.REASSIGNED, second.getType());
		assertEquals(OTHER_USER, second.getActorUserId());
		assertEquals(OwnerLeadStatus.ASSIGNED, second.getFromStatus());
		assertEquals(OwnerLeadStatus.CONTACTED, second.getToStatus());
		assertEquals(ASSIGNEE, second.getFromAssigneeId());
		assertEquals(OTHER_USER, second.getToAssigneeId());
		assertEquals("Called the owner", second.getNote());
		assertEquals(T1, second.getNextActionAt());
		assertEquals(T1, second.getOccurredAt());
		assertTrue(activities.findByLeadIdOrderByOccurredAt(OwnerLeadId.generate()).isEmpty());
	}

	// ==================== duplicate candidates ====================

	@Test
	void saveAll_candidates_writesOneRowPerCandidateWithTheDetectionTime() {
		OwnerLead lead = saveLead(plainLead("0901234567", "a", OwnerLeadStatus.NEW, T0));
		OwnerLead first = saveLead(plainLead("0901234567", "b", OwnerLeadStatus.NEW, T0));
		OwnerLead second = saveLead(plainLead("0901234567", "c", OwnerLeadStatus.ON_HOLD, T0));

		candidates.saveAll(lead.getId(), List.of(first.getId(), second.getId()), T1);

		List<Map<String, Object>> rows = jdbc.queryForList(
				"SELECT lead_id, candidate_lead_id, detected_at FROM owner_lead_duplicate_candidates "
						+ "ORDER BY candidate_lead_id");
		assertEquals(2, rows.size());
		assertEquals(List.of(first.getId().getValue(), second.getId().getValue()).stream().sorted().toList(),
				rows.stream().map(row -> (String) row.get("candidate_lead_id")).toList());
		assertTrue(rows.stream().allMatch(row -> lead.getId().getValue().equals(row.get("lead_id"))));
	}

	/**
	 * Security gate F2: under the phone lock the duplicate check reads only ids (no lead with its snapshot is
	 * loaded) and the candidate rows are inserted without a SELECT per row first.
	 */
	@Test
	void findOpenIdsByPhone_returnsOnlyIdsOfOpenLeadsOfThatPhone() {
		List<OwnerLeadId> expected = new java.util.ArrayList<>();
		for (OwnerLeadStatus status : OwnerLeadStatus.values()) {
			OwnerLead lead = saveLead(plainLead("0901234567", "a" + status.name(), status, T0));
			if (status.isOpen()) {
				expected.add(lead.getId());
			}
		}
		saveLead(plainLead("0907654321", "other phone", OwnerLeadStatus.NEW, T0));
		Statistics statistics = statistics();

		List<OwnerLeadId> open = leads.findOpenIdsByPhone("+84901234567");

		assertEquals(0, statistics.getEntityLoadCount(), "no lead entity is loaded");
		// NEW, ASSIGNED, CONTACTED, ON_HOLD and SURVEY_SCHEDULED (survey-schedule BR-3, W15-15)
		assertEquals(5, expected.size());
		assertEquals(expected.stream().map(OwnerLeadId::getValue).sorted().toList(),
				open.stream().map(OwnerLeadId::getValue).sorted().toList());
		assertTrue(leads.findOpenIdsByPhone("+84900000000").isEmpty());
	}

	@Test
	void saveAll_candidates_insertsEachRowWithoutReadingItFirst() {
		OwnerLead lead = saveLead(plainLead("0901234567", "a", OwnerLeadStatus.NEW, T0));
		OwnerLead first = saveLead(plainLead("0901234567", "b", OwnerLeadStatus.NEW, T0));
		OwnerLead second = saveLead(plainLead("0901234567", "c", OwnerLeadStatus.ON_HOLD, T0));
		Statistics statistics = statistics();

		candidates.saveAll(lead.getId(), List.of(first.getId(), second.getId()), T1);

		assertEquals(2, statistics.getPrepareStatementCount(), "one INSERT per candidate, no SELECT before it");
		assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM owner_lead_duplicate_candidates WHERE lead_id = ?",
				Integer.class, lead.getId().getValue()));
	}

	@Test
	void saveAll_noCandidates_writesNothing() {
		OwnerLead lead = saveLead(plainLead("0901234567", "a", OwnerLeadStatus.NEW, T0));

		candidates.saveAll(lead.getId(), List.of(), T1);

		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM owner_lead_duplicate_candidates", Integer.class));
	}

	// ==================== idempotency ====================

	@Test
	void save_idempotencyRecord_roundTripsAndKeepsTheHashAs64Characters() {
		OwnerLead lead = saveLead(plainLead("0901234567", "a", OwnerLeadStatus.NEW, T0));
		IdempotencyRecord record = new IdempotencyRecord("key-0001", HASH, lead.getId(), T0);

		idempotencyKeys.save(record);
		IdempotencyRecord stored = idempotencyKeys.findByKey("key-0001").orElseThrow();

		assertEquals(record, stored);
		assertEquals(64, jdbc.queryForObject(
				"SELECT length(request_hash) FROM owner_intake_idempotency_keys WHERE idempotency_key = 'key-0001'",
				Integer.class));
		assertTrue(idempotencyKeys.findByKey("unknown").isEmpty());
	}

	@Test
	void deleteByKey_removesOnlyThatKey() {
		OwnerLead lead = saveLead(plainLead("0901234567", "a", OwnerLeadStatus.NEW, T0));
		idempotencyKeys.save(new IdempotencyRecord("key-0001", HASH, lead.getId(), T0));
		idempotencyKeys.save(new IdempotencyRecord("key-0002", HASH, lead.getId(), T0));

		idempotencyKeys.deleteByKey("key-0001");

		assertTrue(idempotencyKeys.findByKey("key-0001").isEmpty());
		assertTrue(idempotencyKeys.findByKey("key-0002").isPresent());
	}

	@Test
	void deleteCreatedBefore_deletesOnlyOlderRowsAndReturnsTheCount() {
		OwnerLead lead = saveLead(plainLead("0901234567", "a", OwnerLeadStatus.NEW, T0));
		idempotencyKeys.save(new IdempotencyRecord("old-0001", HASH, lead.getId(), T0.minusSeconds(100)));
		idempotencyKeys.save(new IdempotencyRecord("old-0002", HASH, lead.getId(), T0.minusSeconds(1)));
		idempotencyKeys.save(new IdempotencyRecord("edge-001", HASH, lead.getId(), T0));
		idempotencyKeys.save(new IdempotencyRecord("new-0001", HASH, lead.getId(), T0.plusSeconds(1)));

		int deleted = idempotencyKeys.deleteCreatedBefore(T0);

		assertEquals(2, deleted);
		assertTrue(idempotencyKeys.findByKey("old-0001").isEmpty());
		assertTrue(idempotencyKeys.findByKey("old-0002").isEmpty());
		assertTrue(idempotencyKeys.findByKey("edge-001").isPresent());
		assertTrue(idempotencyKeys.findByKey("new-0001").isPresent());
	}

	// ==================== sale territory (read side) ====================

	@Test
	void findByWard_afterAJdbcInsert_returnsTheTerritory() {
		jdbc.update("INSERT INTO sale_territories (ward_code, user_id, assigned_by, assigned_at) VALUES (?, ?, ?, ?)",
				"20194", ASSIGNEE, OTHER_USER, java.sql.Timestamp.from(T0));

		SaleTerritory territory = territories.findByWard("20194").orElseThrow();

		assertEquals("20194", territory.getWardCode());
		assertEquals(ASSIGNEE, territory.getUserId());
		assertEquals(OTHER_USER, territory.getAssignedBy());
		assertEquals(T0, territory.getAssignedAt());
		assertTrue(territories.findByWard("20195").isEmpty());
	}

	// ==================== advisory locks ====================

	@Test
	void lockPhone_outsideATransaction_throws() {
		assertThrows(IllegalStateException.class, () -> locks.lockPhone("+84901234567"));
		assertThrows(IllegalStateException.class, () -> locks.lockIdempotencyKey("key-0001"));
	}

	@Test
	void lockPhoneAndKey_insideATransaction_holdTwoDistinctAdvisoryLocksUntilTheEnd() {
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);

		Integer held = transaction.execute(status -> {
			locks.lockIdempotencyKey("same-text");
			locks.lockPhone("same-text");
			locks.lockPhone("same-text");
			return jdbc.queryForObject(
					"SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND pid = pg_backend_pid()",
					Integer.class);
		});

		assertEquals(2, held);
		assertEquals(0, jdbc.queryForObject(
				"SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND pid = pg_backend_pid()",
				Integer.class));
	}

	// ==================== helpers ====================

	private ConsentRecordId saveConsent() {
		ConsentRecord consent = ConsentRecord.create(ConsentRecord.CURRENT_NOTICE_VERSION,
				ConsentPurpose.OWNER_INTAKE_CONTACT, ConsentChannel.WEB, HASH, null, T0);
		consents.save(consent);
		return consent.getId();
	}

	private OwnerLead saveLead(OwnerLead lead) {
		leads.save(lead);
		return lead;
	}

	/** Hibernate statistics, cleared and switched on; {@link #stopStatistics()} switches them off again. */
	private Statistics statistics() {
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		statistics.setStatisticsEnabled(true);
		return statistics;
	}

	private static RequestCode newCode() {
		return RequestCode.generate(RANDOM);
	}

	/** A long-term-rent lead in ward 20194 with only the mandatory fields. */
	private OwnerLead plainLead(String phone, String addressKey, OwnerLeadStatus status, Instant createdAt) {
		return OwnerLead.load(OwnerLeadId.generate(), newCode(), status, OwnerLeadIntent.LONG_TERM_RENT,
				OwnerLeadSource.WEB, "Tran Thi B", PhoneNumber.parse(phone), null, "48", "Đà Nẵng", "20194",
				"Hải Châu", addressKey, addressKey, Optional.empty(), null, Optional.empty(), null, null, null,
				saveConsent(), "{}", null, null, null, null, createdAt, createdAt, null, 1);
	}

	private static OwnerLead withStatus(OwnerLead lead, OwnerLeadStatus status, Long version) {
		return OwnerLead.load(lead.getId(), lead.getRequestCode(), status, lead.getIntent(), lead.getSource(),
				lead.getContactName(), lead.getPhone(), lead.getEmail(), lead.getProvinceCode(),
				lead.getProvinceName(), lead.getWardCode(), lead.getWardName(), lead.getAddressDetail(),
				lead.getAddressKey(), lead.getGeoPoint(), lead.getPropertyType(), lead.getAskingPrice(),
				lead.getAvailableFrom(), lead.getNotes(), lead.getPreferredCallbackTime(), lead.getConsentRecordId(),
				lead.getSubmissionSnapshot(), lead.getAssignedUserId(), lead.getNextActionAt(),
				lead.getDuplicateOfLeadId(), lead.getCloseReason(), lead.getCreatedAt(), T1, version,
				lead.getIntentCycle());
	}

	private static void assertSameLead(OwnerLead expected, OwnerLead actual) {
		assertEquals(expected.getId(), actual.getId());
		assertEquals(expected.getRequestCode(), actual.getRequestCode());
		assertEquals(expected.getStatus(), actual.getStatus());
		assertEquals(expected.getIntent(), actual.getIntent());
		assertEquals(expected.getSource(), actual.getSource());
		assertEquals(expected.getContactName(), actual.getContactName());
		assertEquals(expected.getPhone(), actual.getPhone());
		assertEquals(expected.getEmail(), actual.getEmail());
		assertEquals(expected.getProvinceCode(), actual.getProvinceCode());
		assertEquals(expected.getProvinceName(), actual.getProvinceName());
		assertEquals(expected.getWardCode(), actual.getWardCode());
		assertEquals(expected.getWardName(), actual.getWardName());
		assertEquals(expected.getAddressDetail(), actual.getAddressDetail());
		assertEquals(expected.getAddressKey(), actual.getAddressKey());
		assertEquals(expected.getGeoPoint(), actual.getGeoPoint());
		assertEquals(expected.getPropertyType(), actual.getPropertyType());
		assertEquals(expected.getAskingPrice(), actual.getAskingPrice());
		assertEquals(expected.getAvailableFrom(), actual.getAvailableFrom());
		assertEquals(expected.getNotes(), actual.getNotes());
		assertEquals(expected.getPreferredCallbackTime(), actual.getPreferredCallbackTime());
		assertEquals(expected.getConsentRecordId(), actual.getConsentRecordId());
		assertEquals(expected.getSubmissionSnapshot(), actual.getSubmissionSnapshot());
		assertEquals(expected.getAssignedUserId(), actual.getAssignedUserId());
		assertEquals(expected.getNextActionAt(), actual.getNextActionAt());
		assertEquals(expected.getDuplicateOfLeadId(), actual.getDuplicateOfLeadId());
		assertEquals(expected.getCloseReason(), actual.getCloseReason());
		assertEquals(expected.getCreatedAt(), actual.getCreatedAt());
		assertEquals(expected.getUpdatedAt(), actual.getUpdatedAt());
		assertNotNull(actual.getVersion());
	}
}
