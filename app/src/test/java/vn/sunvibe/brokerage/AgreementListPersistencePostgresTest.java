package vn.sunvibe.brokerage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.brokerage.AgreementListFixture.Version;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementVersion;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.policy.advertising.AdvertisingRightPolicy;
import vn.sunvibe.brokerage.core.domain.repository.AgreementDomainRepository;
import vn.sunvibe.brokerage.core.domain.repository.AgreementIdPage;
import vn.sunvibe.brokerage.core.domain.repository.AgreementListCriteria;
import vn.sunvibe.brokerage.core.domain.repository.AgreementListDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * The agreement list's indexes and its page query on a real PostgreSQL (E05-S7 spec S7-D1 to S7-D4, S7-D9, 5.3;
 * criteria MG-1, LS-3 with exact dates, plan E05S7-P3). The annotations are those of
 * {@link AgreementPersistencePostgresTest}, so both share one context. Each test scopes its rows by basis leads it
 * draws itself, since other classes share the database; where it reads every lead it asserts its own rows only. Needs
 * Docker.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class AgreementListPersistencePostgresTest {

	/** A Vietnam date far from today, so no other class's agreement ends in the windows of these tests. */
	private static final LocalDate D = LocalDate.parse("2031-03-01");

	@Autowired
	private AgreementListDomainRepository lists;

	@Autowired
	private AgreementDomainRepository agreements;

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

	/** MG-1: both indexes exist and are valid, with the columns, order and predicate of spec S7-D9. */
	@Test
	void indexes_createdAtAndInEffectEndDate_validWithTheirColumnsAndPredicate() {
		Map<String, Object> createdAt = index("idx_brokerage_agreements_created_at");
		Map<String, Object> endDate = index("idx_brokerage_agreement_versions_in_effect_end_date");

		assertEquals("brokerage_agreements", createdAt.get("tablename"));
		assertEquals(Boolean.TRUE, createdAt.get("indisvalid"));
		assertTrue(((String) createdAt.get("indexdef")).endsWith("USING btree (created_at DESC, id DESC)"),
				(String) createdAt.get("indexdef"));

		String definition = (String) endDate.get("indexdef");
		assertEquals("brokerage_agreement_versions", endDate.get("tablename"));
		assertEquals(Boolean.TRUE, endDate.get("indisvalid"));
		assertTrue(definition.contains("USING btree (end_date) WHERE"), definition);
		String predicate = definition.substring(definition.indexOf(" WHERE "));
		assertTrue(predicate.contains("'ACTIVATED'"), definition);
		assertTrue(predicate.contains("'EFFECTIVE'"), definition);
		for (String other : List.of("'DRAFT'", "'AWAITING_OWNER'", "'OWNER_CONFIRMED'", "'OWNER_REJECTED'", "'LAPSED'",
				"'ACTIVATION_REFUSED'", "'SUPERSEDED'", "'CANCELLED'")) {
			assertFalse(predicate.contains(other), definition);
		}
	}

	/**
	 * S7-D1: newest creation first, equal times by id descending, with the count; over every lead, the rows of this test
	 * keep that order among themselves and the count holds them.
	 */
	@Test
	void idPage_noFilter_newestFirstThenIdDescendingWithCount() {
		Instant later = Instant.parse("2099-01-02T00:00:00Z");
		Instant earlier = Instant.parse("2099-01-01T00:00:00Z");
		String lead = ids.generateId();
		String newest = insert(lead, "DRAFTING", later, draft());
		String sameA = insert(lead, "DRAFTING", earlier, draft());
		String sameB = insert(lead, "DRAFTING", earlier, draft());
		List<String> tied = new ArrayList<>(List.of(sameA, sameB));
		tied.sort(Comparator.reverseOrder());

		AgreementIdPage page = read(criteria(Set.of(), Set.of(lead), null, null), 0, 50);
		AgreementIdPage everyLead = read(criteria(Set.of(), null, null, null), 0, 50);

		assertEquals(List.of(newest, tied.get(0), tied.get(1)), page.agreementIds());
		assertEquals(3, page.totalElements());
		assertEquals(List.of(newest, tied.get(0), tied.get(1)), everyLead.agreementIds().subList(0, 3));
		assertTrue(everyLead.totalElements() >= 3, String.valueOf(everyLead.totalElements()));
	}

	/** S7-D3: the stored states kept; one, two, none (every state). */
	@Test
	void idPage_statuses_storedStatesOnly() {
		String lead = ids.generateId();
		String drafting = insert(lead, "DRAFTING", at(3), draft());
		String active = insert(lead, "ACTIVE", at(2), new Version(1, "EFFECTIVE", D.minusDays(30), D));
		String suspended = insert(lead, "SUSPENDED", at(1), new Version(1, "EFFECTIVE", D.minusDays(30), D));

		assertEquals(List.of(active), read(criteria(Set.of(AgreementStatus.ACTIVE), Set.of(lead), null, null), 0, 50)
				.agreementIds());
		assertEquals(List.of(active, suspended), read(criteria(Set.of(AgreementStatus.ACTIVE, AgreementStatus.SUSPENDED),
				Set.of(lead), null, null), 0, 50).agreementIds());
		AgreementIdPage all = read(criteria(Set.of(), Set.of(lead), null, null), 0, 50);
		assertEquals(List.of(drafting, active, suspended), all.agreementIds());
		assertEquals(3, all.totalElements());
	}

	/** S7-D2: only the given basis leads; an empty set matches nothing. */
	@Test
	void idPage_leadIds_thoseLeadsOnlyEmptySetNone() {
		String leadA = ids.generateId();
		String leadB = ids.generateId();
		String leadC = ids.generateId();
		String a = insert(leadA, "DRAFTING", at(3), draft());
		insert(leadB, "DRAFTING", at(2), draft());
		String c = insert(leadC, "DRAFTING", at(1), draft());

		AgreementIdPage two = read(criteria(Set.of(), Set.of(leadA, leadC), null, null), 0, 50);
		AgreementIdPage none = read(criteria(Set.of(), Set.of(), null, null), 0, 50);

		assertEquals(List.of(a, c), two.agreementIds());
		assertEquals(2, two.totalElements());
		assertEquals(List.of(), none.agreementIds());
		assertEquals(0, none.totalElements());
	}

	/**
	 * LS-3 (S7-D4) with exact dates: ACTIVE and SUSPENDED agreements whose version that counts ends from D to D + 29 are
	 * kept, soonest first, equal dates by id ascending; D - 1 and D + 30 are not, nor a drafting or awaiting agreement
	 * whose version ends on D; combined with a state by "and".
	 */
	@Test
	void idPage_ending_inEffectBetweenTheTwoDatesSoonestFirst() {
		String lead = ids.generateId();
		String first = insert(lead, "ACTIVE", at(9), new Version(1, "EFFECTIVE", D.minusDays(60), D));
		String suspended = insert(lead, "SUSPENDED", at(8), new Version(1, "EFFECTIVE", D.minusDays(60), D.plusDays(29)));
		String sameEnd = insert(lead, "ACTIVE", at(7), new Version(1, "EFFECTIVE", D.minusDays(60), D.plusDays(29)));
		insert(lead, "ACTIVE", at(6), new Version(1, "EFFECTIVE", D.minusDays(60), D.minusDays(1)));
		insert(lead, "ACTIVE", at(5), new Version(1, "EFFECTIVE", D.minusDays(60), D.plusDays(30)));
		insert(lead, "DRAFTING", at(4), new Version(1, "DRAFT", D.minusDays(60), D));
		insert(lead, "AWAITING_CONFIRMATION", at(3), new Version(1, "OWNER_CONFIRMED", D.minusDays(60), D));
		List<String> tied = new ArrayList<>(List.of(suspended, sameEnd));
		tied.sort(Comparator.naturalOrder());

		AgreementIdPage ending = read(criteria(Set.of(), Set.of(lead), D, D.plusDays(29)), 0, 50);
		AgreementIdPage suspendedOnly = read(criteria(Set.of(AgreementStatus.SUSPENDED), Set.of(lead), D, D.plusDays(29)),
				0, 50);

		assertEquals(List.of(first, tied.get(0), tied.get(1)), ending.agreementIds());
		assertEquals(3, ending.totalElements());
		assertEquals(List.of(suspended), suspendedOnly.agreementIds());
		assertEquals(1, suspendedOnly.totalElements());
	}

	/**
	 * LS-3 parity (S7-D4): for an agreement with one ACTIVATED version, one with one EFFECTIVE version, and one with an
	 * EFFECTIVE version 1 and an ACTIVATED version 2 of other dates, the end date of
	 * {@link AdvertisingRightPolicy#versionThatCountsOrNull} is the only one-day window that lists it.
	 */
	@Test
	void idPage_endingVersionThatCounts_equalsThePolicy() {
		String activatedLead = ids.generateId();
		String effectiveLead = ids.generateId();
		String bothLead = ids.generateId();
		String activated = insert(activatedLead, "ACTIVE", at(3), new Version(1, "ACTIVATED", D.plusDays(2), D.plusDays(5)));
		String effective = insert(effectiveLead, "ACTIVE", at(2), new Version(1, "EFFECTIVE", D.minusDays(60), D.plusDays(3)));
		String both = insert(bothLead, "ACTIVE", at(1), new Version(1, "EFFECTIVE", D.minusDays(60), D.plusDays(4)),
				new Version(2, "ACTIVATED", D.plusDays(5), D.plusDays(20)));

		for (String agreementId : List.of(activated, effective, both)) {
			BrokerageAgreement stored = tx(() -> agreements.findById(agreementId)).orElseThrow();
			LocalDate counts = AdvertisingRightPolicy.versionThatCountsOrNull(stored).getPeriod().endDate();
			Set<String> lead = Set.of(stored.getBasisLeadId());

			assertEquals(List.of(agreementId), read(criteria(Set.of(), lead, counts, counts), 0, 50).agreementIds(),
					agreementId);
			for (AgreementVersion version : stored.getVersions()) {
				LocalDate end = version.getPeriod().endDate();
				if (!end.equals(counts)) {
					assertEquals(List.of(), read(criteria(Set.of(), lead, end, end), 0, 50).agreementIds(), agreementId);
				}
			}
		}
		assertEquals(D.plusDays(4), AdvertisingRightPolicy.versionThatCountsOrNull(
				tx(() -> agreements.findById(both)).orElseThrow()).getPeriod().endDate());
	}

	/** A page past the end: no id, and the count of every match. */
	@Test
	void idPage_pagePastTheEnd_noIdAndTheCount() {
		String lead = ids.generateId();
		insert(lead, "DRAFTING", at(2), draft());
		insert(lead, "DRAFTING", at(1), draft());

		AgreementIdPage past = read(criteria(Set.of(), Set.of(lead), null, null), 5, 1);
		AgreementIdPage second = read(criteria(Set.of(), Set.of(lead), null, null), 1, 1);

		assertEquals(List.of(), past.agreementIds());
		assertEquals(2, past.totalElements());
		assertEquals(1, second.agreementIds().size());
		assertEquals(2, second.totalElements());
	}

	private String insert(String lead, String status, Instant createdAt, Version... versions) {
		String id = AgreementListFixture.insert(jdbc, ids, lead, null, status, createdAt, versions);
		created.add(id);
		return id;
	}

	private static Version draft() {
		return new Version(1, "DRAFT", D, D.plusDays(100));
	}

	/** A creation time some minutes after a fixed instant: a larger offset is newer. */
	private static Instant at(int minutes) {
		return Instant.parse("2030-01-01T00:00:00Z").plus(minutes, ChronoUnit.MINUTES);
	}

	private static AgreementListCriteria criteria(Set<AgreementStatus> statuses, Set<String> leadsOrNull,
			LocalDate fromOrNull, LocalDate toOrNull) {
		return new AgreementListCriteria(statuses, leadsOrNull, fromOrNull, toOrNull);
	}

	private AgreementIdPage read(AgreementListCriteria criteria, int page, int size) {
		return tx(() -> lists.idPage(criteria, page, size));
	}

	private <T> T tx(Supplier<T> work) {
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		template.setReadOnly(true);
		return template.execute(status -> work.get());
	}

	private Map<String, Object> index(String name) {
		List<Map<String, Object>> found = jdbc.queryForList("SELECT i.tablename, i.indexdef, x.indisvalid"
				+ " FROM pg_indexes i JOIN pg_class c ON c.relname = i.indexname"
				+ " JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = i.schemaname"
				+ " JOIN pg_index x ON x.indexrelid = c.oid"
				+ " WHERE i.schemaname = current_schema() AND i.indexname = ?", name);
		assertEquals(1, found.size(), name);
		return found.getFirst();
	}
}
