package vn.sunvibe.acquisition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.core.application.shared.external.staff.EligibleAssignee;
import vn.sunvibe.acquisition.core.application.shared.external.staff.LeadAssigneeDirectoryPort;
import vn.sunvibe.acquisition.core.domain.model.idempotency.IdempotencyRecord;
import vn.sunvibe.acquisition.core.domain.repository.IdempotencyKeyDomainRepository;
import vn.sunvibe.acquisition.core.infrastructure.persistence.adapter.IdempotencyKeyRepositoryAdapter;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * A failure in the middle of a submission stores nothing (acquisition-owner-intake brief E-7; W9-23). The ward has a
 * territory whose person may take leads and the request sends an Idempotency-Key, so the submission writes the
 * consent, the lead, the SUBMITTED and AUTO_ASSIGNED activities and the key row; the test configuration flushes those
 * inserts to PostgreSQL and fails right after the key row. The answer is 500 INTERNAL_ERROR with the correlation id,
 * and the five intake tables stay empty.
 * <p>
 * The service reads everything (the assignee port included) before its first write, so no port call comes after the
 * consent insert; the failure is placed on the last repository write instead. Own context, so its tables start empty.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate",
			"app.security.rate-limit.enabled=false" })
@ActiveProfiles("test")
@Import({ PostgresContainerConfig.class, OwnerIntakeRollbackPostgresTest.FailAfterConsentInsert.class })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OwnerIntakeRollbackPostgresTest {

	private static final String ENDPOINT = "/api/v1/public/owner-intake-requests";
	private static final String HAI_CHAU = "20242";
	private static final String SALE_USER = "01JSALE0000000000000000001";
	private static final List<String> INTAKE_TABLES = List.of("consent_records", "owner_leads",
			"owner_lead_activities", "owner_lead_duplicate_candidates", "owner_intake_idempotency_keys");

	/** Row counts the failing write saw inside the transaction, just before it failed. */
	static final AtomicReference<Map<String, Integer>> COUNTS_BEFORE_FAILURE = new AtomicReference<>();

	@TestConfiguration(proxyBeanMethods = false)
	static class FailAfterConsentInsert {

		/** Every territory person may take leads, so the AUTO_ASSIGNED activity is written. */
		@Bean
		@Primary
		LeadAssigneeDirectoryPort everyoneEligible() {
			return new LeadAssigneeDirectoryPort() {
				@Override
				public boolean isEligibleAssignee(String userId) {
					return true;
				}

				@Override
				public List<EligibleAssignee> listEligibleAssignees() {
					return List.of();
				}

				@Override
				public Map<String, String> findDisplayNames(Set<String> userIds) {
					return Map.of();
				}
			};
		}

		/**
		 * Writes through the real adapter; the key row is the last write of a submission, so after it every pending
		 * insert is flushed, the rows are counted, and the write fails.
		 */
		@Bean
		@Primary
		IdempotencyKeyDomainRepository failAfterKeyRow(IdempotencyKeyRepositoryAdapter real,
				EntityManagerFactory entityManagerFactory, JdbcTemplate jdbc) {
			EntityManager entityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
			return new IdempotencyKeyDomainRepository() {
				@Override
				public Optional<IdempotencyRecord> findByKey(String key) {
					return real.findByKey(key);
				}

				@Override
				public void save(IdempotencyRecord record) {
					real.save(record);
					entityManager.flush();
					Map<String, Integer> counts = new LinkedHashMap<>();
					INTAKE_TABLES.forEach(table ->
							counts.put(table, jdbc.queryForObject("select count(*) from " + table, Integer.class)));
					COUNTS_BEFORE_FAILURE.set(counts);
					throw new IllegalStateException("Simulated failure after the consent insert");
				}

				@Override
				public void deleteByKey(String key) {
					real.deleteByKey(key);
				}

				@Override
				public int deleteCreatedBefore(Instant cutoff) {
					return real.deleteCreatedBefore(cutoff);
				}
			};
		}
	}

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private UserDomainRepository userRepository;

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	@Autowired
	private IdGenerator<String> ulidGenerator;

	@Autowired
	private JdbcTemplate jdbc;

	private AcquisitionApiTestSupport api;

	@BeforeAll
	void setUp() {
		api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
		jdbc.update("insert into sale_territories (ward_code, user_id, assigned_by, assigned_at) values (?, ?, ?, ?)",
				HAI_CHAU, SALE_USER, SALE_USER, Timestamp.from(Instant.now()));
	}

	@AfterAll
	void tearDown() {
		jdbc.update("delete from sale_territories where ward_code = ?", HAI_CHAU);
		api.http().close();
	}

	@Test
	void post_failureAfterConsentInsert_returns500WithCorrelationIdAndStoresNothing() throws Exception {
		assertEquals(zeroCounts(), counts(), "the intake tables start empty");

		Result result = api.send(HttpRequest.newBuilder(api.uri(ENDPOINT))
				.timeout(AcquisitionApiTestSupport.REQUEST_TIMEOUT)
				.header("Content-Type", "application/json")
				.header("Idempotency-Key", "rollback-key-0001")
				.POST(HttpRequest.BodyPublishers.ofString(api.json().writeValueAsString(validBody()),
						StandardCharsets.UTF_8))
				.build());

		assertEquals(500, result.status(), result.rawBody());
		assertEquals("INTERNAL_ERROR", result.body().path("code").asString());
		assertEquals(0, result.body().path("errors").size());
		assertNotNull(result.header("X-Correlation-Id"));
		assertEquals(result.header("X-Correlation-Id"), result.body().path("correlationId").asString());

		Map<String, Integer> seenBeforeFailure = COUNTS_BEFORE_FAILURE.get();
		assertNotNull(seenBeforeFailure, "the failing write was not reached");
		assertEquals(1, seenBeforeFailure.get("consent_records"), "the consent was inserted before the failure");
		assertEquals(1, seenBeforeFailure.get("owner_leads"));
		assertEquals(2, seenBeforeFailure.get("owner_lead_activities"), "SUBMITTED and AUTO_ASSIGNED");
		assertEquals(1, seenBeforeFailure.get("owner_intake_idempotency_keys"));

		assertEquals(zeroCounts(), counts(), "the rollback removed every row");
	}

	private static Map<String, Object> validBody() {
		Map<String, Object> consent = new LinkedHashMap<>();
		consent.put("accepted", true);
		consent.put("noticeVersion", "owner-intake-2026-10");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("contactName", "Lê Thị Hoàn");
		body.put("phone", "0931 000 001");
		body.put("intent", "LONG_TERM_RENT");
		body.put("provinceCode", "48");
		body.put("wardCode", HAI_CHAU);
		body.put("addressDetail", "5 Trần Phú");
		body.put("consent", consent);
		return body;
	}

	private Map<String, Integer> counts() {
		Map<String, Integer> counts = new LinkedHashMap<>();
		INTAKE_TABLES.forEach(table -> counts.put(table, jdbc.queryForObject("select count(*) from " + table,
				Integer.class)));
		return counts;
	}

	private static Map<String, Integer> zeroCounts() {
		Map<String, Integer> counts = new LinkedHashMap<>();
		INTAKE_TABLES.forEach(table -> counts.put(table, 0));
		return counts;
	}
}
