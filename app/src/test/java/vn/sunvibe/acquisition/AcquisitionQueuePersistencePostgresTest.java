package vn.sunvibe.acquisition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivity;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentChannel;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentPurpose;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentRecord;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentRecordId;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadSource;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.acquisition.core.domain.model.lead.PhoneNumber;
import vn.sunvibe.acquisition.core.domain.model.lead.RequestCode;
import vn.sunvibe.acquisition.core.domain.model.log.OwnerLeadAccessLog;
import vn.sunvibe.acquisition.core.domain.model.territory.SaleTerritory;
import vn.sunvibe.acquisition.core.domain.model.territory.SaleTerritoryChange;
import vn.sunvibe.acquisition.core.domain.repository.ConsentRecordDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.DuplicateCandidateDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.LeadSearchCriteria;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadAccessLogDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadActivityDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.PageResult;
import vn.sunvibe.acquisition.core.domain.repository.SaleTerritoryChangeDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.SaleTerritoryDomainRepository;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.OwnerLeadAccessLogEntity;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.OwnerLeadActivityEntity;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.SaleTerritoryChangeEntity;

/**
 * The lead queue additions of the acquisition adapters on a real PostgreSQL 17 (acquisition-owner-lead-queue spec 4
 * and 5.1): the access log and the territory change history, the lead search with its paging and order, the
 * duplicate candidate lookups with the viewer's visibility, the ward upsert that never fails on the primary key, and
 * the guarantee that an immutable history row is not changed by Hibernate. The annotations are those of
 * {@link vn.sunvibe.FlywayPostgresMigrationTest}, so all share one context. Needs Docker, like that test.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class AcquisitionQueuePersistencePostgresTest {

	/** Whole microseconds: PostgreSQL keeps no more, so a stored instant can be compared with the one saved. */
	private static final Instant T0 = Instant.parse("2026-10-16T08:00:00.123456Z");
	private static final Instant T1 = Instant.parse("2026-10-17T09:30:00.654321Z");
	private static final String SALE_A = "01SALEA0000000000000000000";
	private static final String SALE_B = "01SALEB0000000000000000000";
	private static final String LEADER = "01LEADER000000000000000000";
	private static final String WARD_1 = "20194";
	private static final String WARD_2 = "20195";
	private static final String HASH = "a".repeat(64);
	private static final SecureRandom RANDOM = new SecureRandom();

	@Autowired
	private OwnerLeadDomainRepository leads;

	@Autowired
	private ConsentRecordDomainRepository consents;

	@Autowired
	private OwnerLeadActivityDomainRepository activities;

	@Autowired
	private OwnerLeadAccessLogDomainRepository accessLogs;

	@Autowired
	private DuplicateCandidateDomainRepository candidates;

	@Autowired
	private SaleTerritoryDomainRepository territories;

	@Autowired
	private SaleTerritoryChangeDomainRepository territoryChanges;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@PersistenceContext
	private EntityManager entityManager;

	@BeforeEach
	@AfterEach
	void cleanAcquisitionTables() {
		jdbc.update("DELETE FROM owner_intake_idempotency_keys");
		jdbc.update("DELETE FROM owner_lead_duplicate_candidates");
		jdbc.update("DELETE FROM owner_lead_activities");
		jdbc.update("DELETE FROM owner_lead_access_logs");
		jdbc.update("DELETE FROM owner_leads");
		jdbc.update("DELETE FROM consent_records");
		jdbc.update("DELETE FROM sale_territories");
		jdbc.update("DELETE FROM sale_territory_changes");
	}

	// ==================== access log ====================

	@Test
	void saveAndFindByLeadId_accessLogs_roundTripOldestFirstAndOnlyThatLead() {
		OwnerLead lead = saveLead(lead(OwnerLeadStatus.ASSIGNED, SALE_A, WARD_1, T0));
		OwnerLead other = saveLead(lead(OwnerLeadStatus.ASSIGNED, SALE_A, WARD_1, T0));
		accessLogs.save(OwnerLeadAccessLog.create(7002L, lead.getId(), LEADER, T1));
		accessLogs.save(OwnerLeadAccessLog.create(7001L, lead.getId(), SALE_A, T0));
		accessLogs.save(OwnerLeadAccessLog.create(7003L, other.getId(), SALE_A, T0));

		List<OwnerLeadAccessLog> found = accessLogs.findByLeadId(lead.getId());

		assertEquals(List.of(7001L, 7002L), found.stream().map(OwnerLeadAccessLog::getId).toList());
		assertEquals(lead.getId(), found.get(0).getLeadId());
		assertEquals(SALE_A, found.get(0).getUserId());
		assertEquals(T0, found.get(0).getAccessedAt());
		assertEquals(LEADER, found.get(1).getUserId());
		assertEquals(T1, found.get(1).getAccessedAt());
		assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM owner_lead_access_logs", Integer.class));
		assertTrue(accessLogs.findByLeadId(OwnerLeadId.generate()).isEmpty());
	}

	// ==================== territory change history ====================

	@Test
	void saveAndFind_territoryChanges_roundTripOldestFirstWithNullSides() {
		territoryChanges.save(SaleTerritoryChange.create(8002L, WARD_1, SALE_A, null, LEADER, T1));
		territoryChanges.save(SaleTerritoryChange.create(8001L, WARD_1, null, SALE_A, LEADER, T0));
		territoryChanges.save(SaleTerritoryChange.create(8003L, WARD_2, SALE_B, SALE_A, LEADER, T0));

		List<SaleTerritoryChange> found = territoryChanges.findByWardCodeOrderByOccurredAt(WARD_1);

		assertEquals(List.of(8001L, 8002L), found.stream().map(SaleTerritoryChange::getId).toList());
		assertNull(found.get(0).getOldUserId());
		assertEquals(SALE_A, found.get(0).getNewUserId());
		assertEquals(LEADER, found.get(0).getActorUserId());
		assertEquals(T0, found.get(0).getOccurredAt());
		assertEquals(SALE_A, found.get(1).getOldUserId());
		assertNull(found.get(1).getNewUserId());
		assertEquals(T1, found.get(1).getOccurredAt());
		assertTrue(territoryChanges.findByWardCodeOrderByOccurredAt("99999").isEmpty());
	}

	// ==================== search ====================

	@Test
	void search_noFilter_returnsEveryLeadNewestFirstWithTheTotal() {
		Dataset data = searchDataset();

		PageResult<OwnerLead> page = leads.search(criteria(null, Set.of(), null), 0, 10);

		assertEquals(5, page.totalElements());
		assertEquals(0, page.page());
		assertEquals(10, page.size());
		assertEquals(ids(data.newest(), data.tieHigh(), data.tieLow(), data.middle(), data.oldest()), ids(page));
	}

	@Test
	void search_byAssignee_returnsOnlyThatPersonsLeads() {
		Dataset data = searchDataset();

		PageResult<OwnerLead> page = leads.search(criteria(SALE_A, Set.of(), null), 0, 10);

		assertEquals(3, page.totalElements());
		assertEquals(ids(data.tieHigh(), data.tieLow(), data.middle()), ids(page));
	}

	@Test
	void search_byStatusSet_returnsLeadsInAnyOfTheStatuses() {
		Dataset data = searchDataset();

		PageResult<OwnerLead> page = leads.search(
				criteria(null, Set.of(OwnerLeadStatus.CONTACTED, OwnerLeadStatus.NEW), null), 0, 10);

		assertEquals(3, page.totalElements());
		assertEquals(ids(data.tieHigh(), data.tieLow(), data.oldest()), ids(page));
	}

	@Test
	void search_byWard_returnsOnlyLeadsOfThatWard() {
		Dataset data = searchDataset();

		PageResult<OwnerLead> page = leads.search(criteria(null, Set.of(), WARD_2), 0, 10);

		assertEquals(ids(data.newest(), data.middle()), ids(page));
		assertEquals(2, page.totalElements());
	}

	@Test
	void search_assigneeStatusAndWardTogether_combineWithAnd() {
		Dataset data = searchDataset();

		PageResult<OwnerLead> page = leads.search(
				criteria(SALE_A, Set.of(OwnerLeadStatus.CONTACTED), WARD_1), 0, 10);

		assertEquals(ids(data.tieHigh(), data.tieLow()), ids(page));
		assertEquals(2, page.totalElements());
		PageResult<OwnerLead> none = leads.search(criteria(SALE_B, Set.of(OwnerLeadStatus.NEW), WARD_2), 0, 10);
		assertTrue(none.items().isEmpty());
		assertEquals(0, none.totalElements());
	}

	@Test
	void search_paging_returnsTheFirstMiddleAndLastPagesAndAnEmptyPageBeyond() {
		Dataset data = searchDataset();
		List<String> all = ids(data.newest(), data.tieHigh(), data.tieLow(), data.middle(), data.oldest());

		PageResult<OwnerLead> first = leads.search(criteria(null, Set.of(), null), 0, 2);
		PageResult<OwnerLead> second = leads.search(criteria(null, Set.of(), null), 1, 2);
		PageResult<OwnerLead> last = leads.search(criteria(null, Set.of(), null), 2, 2);
		PageResult<OwnerLead> beyond = leads.search(criteria(null, Set.of(), null), 3, 2);

		assertEquals(all.subList(0, 2), ids(first));
		assertEquals(all.subList(2, 4), ids(second));
		assertEquals(all.subList(4, 5), ids(last));
		assertTrue(beyond.items().isEmpty());
		for (PageResult<OwnerLead> page : List.of(first, second, last, beyond)) {
			assertEquals(5, page.totalElements());
			assertEquals(2, page.size());
		}
		assertEquals(0, first.page());
		assertEquals(2, last.page());
		assertEquals(3, beyond.page());
	}

	// ==================== duplicate candidates and visibility ====================

	@Test
	void leadIdsWithVisibleCandidate_viewerSeesAll_countsLinksInBothDirections() {
		OwnerLead a = saveLead(lead(OwnerLeadStatus.ASSIGNED, SALE_A, WARD_1, T0));
		OwnerLead b = saveLead(lead(OwnerLeadStatus.ASSIGNED, SALE_B, WARD_1, T0));
		OwnerLead c = saveLead(lead(OwnerLeadStatus.NEW, null, WARD_1, T0));
		OwnerLead lonely = saveLead(lead(OwnerLeadStatus.NEW, null, WARD_1, T0));
		candidates.saveAll(a.getId(), List.of(b.getId()), T0);
		candidates.saveAll(c.getId(), List.of(a.getId()), T0);

		Set<String> found = candidates.leadIdsWithVisibleCandidate(idValues(a, b, c, lonely), Optional.empty());

		assertEquals(Set.copyOf(idValues(a, b, c)), found);
	}

	@Test
	void leadIdsWithVisibleCandidate_restrictedViewer_ignoresCandidatesAssignedToSomeoneElseOrNobody() {
		OwnerLead a = saveLead(lead(OwnerLeadStatus.NEW, null, WARD_1, T0));
		OwnerLead b = saveLead(lead(OwnerLeadStatus.ASSIGNED, SALE_A, WARD_1, T0));
		OwnerLead c = saveLead(lead(OwnerLeadStatus.ASSIGNED, SALE_B, WARD_1, T0));
		candidates.saveAll(a.getId(), List.of(b.getId(), c.getId()), T0);

		Set<String> forSaleA = candidates.leadIdsWithVisibleCandidate(idValues(a, b, c), Optional.of(SALE_A));
		Set<String> forSaleB = candidates.leadIdsWithVisibleCandidate(idValues(a, b, c), Optional.of(SALE_B));
		Set<String> forOutsider = candidates.leadIdsWithVisibleCandidate(idValues(a, b, c), Optional.of(LEADER));

		// a repeats b (visible to SALE_A) and c (visible to SALE_B); b and c repeat only a, which nobody holds
		assertEquals(Set.copyOf(idValues(a)), forSaleA);
		assertEquals(Set.copyOf(idValues(a)), forSaleB);
		assertEquals(Set.of(), forOutsider);
		assertEquals(Set.of(), candidates.leadIdsWithVisibleCandidate(List.of(), Optional.empty()));
	}

	@Test
	void findVisibleCandidates_viewerSeesAll_returnsBothDirectionsOldestFirst() {
		OwnerLead a = saveLead(lead(OwnerLeadStatus.NEW, null, WARD_1, T0.plusSeconds(50)));
		OwnerLead outgoing = saveLead(lead(OwnerLeadStatus.ASSIGNED, SALE_A, WARD_1, T0.plusSeconds(20)));
		OwnerLead incoming = saveLead(lead(OwnerLeadStatus.ASSIGNED, SALE_B, WARD_1, T0.plusSeconds(10)));
		OwnerLead unrelated = saveLead(lead(OwnerLeadStatus.ASSIGNED, SALE_B, WARD_1, T0));
		candidates.saveAll(a.getId(), List.of(outgoing.getId()), T0);
		candidates.saveAll(incoming.getId(), List.of(a.getId()), T0);

		List<OwnerLead> found = candidates.findVisibleCandidates(a.getId(), Optional.empty());

		assertEquals(List.of(incoming.getId(), outgoing.getId()), found.stream().map(OwnerLead::getId).toList());
		assertTrue(candidates.findVisibleCandidates(unrelated.getId(), Optional.empty()).isEmpty());
	}

	@Test
	void findVisibleCandidates_restrictedViewer_dropsCandidatesAssignedToSomeoneElse() {
		OwnerLead a = saveLead(lead(OwnerLeadStatus.NEW, null, WARD_1, T0));
		OwnerLead mine = saveLead(lead(OwnerLeadStatus.ASSIGNED, SALE_A, WARD_1, T0.plusSeconds(1)));
		OwnerLead theirs = saveLead(lead(OwnerLeadStatus.ASSIGNED, SALE_B, WARD_1, T0.plusSeconds(2)));
		OwnerLead nobodys = saveLead(lead(OwnerLeadStatus.NEW, null, WARD_1, T0.plusSeconds(3)));
		candidates.saveAll(a.getId(), List.of(mine.getId(), theirs.getId()), T0);
		candidates.saveAll(nobodys.getId(), List.of(a.getId()), T0);

		List<OwnerLead> forSaleA = candidates.findVisibleCandidates(a.getId(), Optional.of(SALE_A));
		List<OwnerLead> forSaleB = candidates.findVisibleCandidates(a.getId(), Optional.of(SALE_B));

		assertEquals(List.of(mine.getId()), forSaleA.stream().map(OwnerLead::getId).toList());
		assertEquals(List.of(theirs.getId()), forSaleB.stream().map(OwnerLead::getId).toList());
		assertTrue(candidates.findVisibleCandidates(a.getId(), Optional.of(LEADER)).isEmpty());
	}

	// ==================== sale territory ====================

	@Test
	void upsert_twiceOnOneWard_leavesOneRowWithTheSecondPerson() {
		territories.upsert(SaleTerritory.load(WARD_1, SALE_A, LEADER, T0));
		territories.upsert(SaleTerritory.load(WARD_1, SALE_B, SALE_A, T1));

		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM sale_territories WHERE ward_code = ?",
				Integer.class, WARD_1));
		SaleTerritory stored = territories.findByWard(WARD_1).orElseThrow();
		assertEquals(SALE_B, stored.getUserId());
		assertEquals(SALE_A, stored.getAssignedBy());
		assertEquals(T1, stored.getAssignedAt());
	}

	@Test
	void upsert_twoParallelCallsOnANewWard_bothSucceedAndLeaveOneRow() throws Exception {
		CyclicBarrier start = new CyclicBarrier(2);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<?> first = pool.submit(() -> {
				await(start);
				territories.upsert(SaleTerritory.load(WARD_2, SALE_A, LEADER, T0));
			});
			Future<?> second = pool.submit(() -> {
				await(start);
				territories.upsert(SaleTerritory.load(WARD_2, SALE_B, LEADER, T1));
			});
			first.get(30, TimeUnit.SECONDS);
			second.get(30, TimeUnit.SECONDS);
		} finally {
			pool.shutdownNow();
		}

		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM sale_territories WHERE ward_code = ?",
				Integer.class, WARD_2));
		assertTrue(Set.of(SALE_A, SALE_B).contains(territories.findByWard(WARD_2).orElseThrow().getUserId()));
	}

	@Test
	void findByWardCodes_returnsOnlyTheKnownWardsAmongTheGivenCodes() {
		territories.upsert(SaleTerritory.load(WARD_1, SALE_A, LEADER, T0));
		territories.upsert(SaleTerritory.load(WARD_2, SALE_B, LEADER, T0));
		territories.upsert(SaleTerritory.load("20196", SALE_B, LEADER, T0));

		List<SaleTerritory> found = territories.findByWardCodes(List.of(WARD_1, WARD_2, "99999"));

		assertEquals(Set.of(WARD_1, WARD_2), Set.copyOf(found.stream().map(SaleTerritory::getWardCode).toList()));
		assertTrue(territories.findByWardCodes(List.of()).isEmpty());
	}

	@Test
	void deleteByWard_existingWardIsRemovedAndAMissingWardIsANoOp() {
		territories.upsert(SaleTerritory.load(WARD_1, SALE_A, LEADER, T0));

		territories.deleteByWard("99999");
		assertTrue(territories.findByWard(WARD_1).isPresent());
		territories.deleteByWard(WARD_1);

		assertTrue(territories.findByWard(WARD_1).isEmpty());
		territories.deleteByWard(WARD_1);
	}

	// ==================== immutable history rows ====================

	@Test
	void flush_modifiedAccessLogEntity_leavesTheRowUnchanged() throws Exception {
		OwnerLead lead = saveLead(lead(OwnerLeadStatus.ASSIGNED, SALE_A, WARD_1, T0));
		accessLogs.save(OwnerLeadAccessLog.create(7001L, lead.getId(), SALE_A, T0));

		modifyInsideTransaction(OwnerLeadAccessLogEntity.class, 7001L, "userId", LEADER);

		assertEquals(SALE_A, jdbc.queryForObject("SELECT user_id FROM owner_lead_access_logs WHERE id = 7001",
				String.class));
	}

	@Test
	void flush_modifiedTerritoryChangeEntity_leavesTheRowUnchanged() throws Exception {
		territoryChanges.save(SaleTerritoryChange.create(8001L, WARD_1, null, SALE_A, LEADER, T0));

		modifyInsideTransaction(SaleTerritoryChangeEntity.class, 8001L, "newUserId", SALE_B);

		assertEquals(SALE_A, jdbc.queryForObject("SELECT new_user_id FROM sale_territory_changes WHERE id = 8001",
				String.class));
	}

	@Test
	void flush_modifiedActivityEntity_leavesTheRowUnchanged() throws Exception {
		OwnerLead lead = saveLead(lead(OwnerLeadStatus.NEW, null, WARD_1, T0));
		activities.save(OwnerLeadActivity.submitted(6001L, lead.getId()));

		modifyInsideTransaction(OwnerLeadActivityEntity.class, 6001L, "note", "changed");

		assertNull(jdbc.queryForObject("SELECT note FROM owner_lead_activities WHERE id = 6001", String.class));
	}

	// ==================== helpers ====================

	/**
	 * Five leads: the oldest is new and unassigned in ward 1; the middle one and the newest are in ward 2; the two
	 * tied ones are created at the same instant in ward 1 and ordered by id.
	 */
	private Dataset searchDataset() {
		OwnerLead oldest = saveLead(lead(OwnerLeadStatus.NEW, null, WARD_1, T0));
		OwnerLead middle = saveLead(lead(OwnerLeadStatus.ASSIGNED, SALE_A, WARD_2, T0.plusSeconds(10)));
		OwnerLead tieOne = saveLead(lead(OwnerLeadStatus.CONTACTED, SALE_A, WARD_1, T0.plusSeconds(20)));
		OwnerLead tieTwo = saveLead(lead(OwnerLeadStatus.CONTACTED, SALE_A, WARD_1, T0.plusSeconds(20)));
		OwnerLead newest = saveLead(lead(OwnerLeadStatus.ASSIGNED, SALE_B, WARD_2, T0.plusSeconds(30)));
		boolean oneHigher = tieOne.getId().getValue().compareTo(tieTwo.getId().getValue()) > 0;
		return new Dataset(oldest, middle, oneHigher ? tieOne : tieTwo, oneHigher ? tieTwo : tieOne, newest);
	}

	/** The five leads of {@link #searchDataset()}; tieHigh has the greater id of the two created at one instant. */
	private record Dataset(OwnerLead oldest, OwnerLead middle, OwnerLead tieHigh, OwnerLead tieLow, OwnerLead newest) {
	}

	private static LeadSearchCriteria criteria(String assignedUserId, Set<OwnerLeadStatus> statuses, String wardCode) {
		return new LeadSearchCriteria(Optional.ofNullable(assignedUserId), statuses, Optional.ofNullable(wardCode));
	}

	private static List<String> ids(OwnerLead... leads) {
		return Arrays.stream(leads).map(lead -> lead.getId().getValue()).toList();
	}

	private static List<String> ids(PageResult<OwnerLead> page) {
		return page.items().stream().map(lead -> lead.getId().getValue()).toList();
	}

	private static List<String> idValues(OwnerLead... leads) {
		return ids(leads);
	}

	private static void await(CyclicBarrier barrier) {
		try {
			barrier.await(30, TimeUnit.SECONDS);
		} catch (Exception e) {
			throw new IllegalStateException("The parallel calls did not start together.", e);
		}
	}

	/** Loads the entity, writes one field by reflection (test only) and flushes, as a buggy update would. */
	private void modifyInsideTransaction(Class<?> entityClass, Long id, String fieldName, String value)
			throws Exception {
		Field field = entityClass.getDeclaredField(fieldName);
		field.setAccessible(true);
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			Object entity = entityManager.find(entityClass, id);
			try {
				field.set(entity, value);
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
			entityManager.flush();
		});
	}

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

	/** A long-term-rent lead with only the mandatory fields; the null version makes it a new row. */
	private OwnerLead lead(OwnerLeadStatus status, String assignedUserId, String wardCode, Instant createdAt) {
		return OwnerLead.load(OwnerLeadId.generate(), RequestCode.generate(RANDOM), status,
				OwnerLeadIntent.LONG_TERM_RENT, OwnerLeadSource.WEB, "Tran Thi B", PhoneNumber.parse("0901234567"),
				null, "48", "Đà Nẵng", wardCode, "Hải Châu", "12 a", "12 a", Optional.empty(), null,
				Optional.empty(), null, null, null, saveConsent(), "{}", assignedUserId, null, null, null,
				createdAt, createdAt, null);
	}
}
