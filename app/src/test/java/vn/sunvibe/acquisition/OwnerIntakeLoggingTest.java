package vn.sunvibe.acquisition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.NestedTestConfiguration;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivity;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadActivityDomainRepository;
import vn.sunvibe.acquisition.core.infrastructure.persistence.adapter.OwnerLeadActivityRepositoryAdapter;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * What the server logs while it handles intake submissions (W9-19; acquisition BR-2, spec 7): the console output of
 * a successful submission, of a 500 caused by a constraint violation whose message quotes the phone, and of a
 * rate-limited submission holds no contact name, phone, email, address, coordinates or client IP. The calls go to
 * 127.0.0.1, so the client IP the server sees is known. Only the output written during the test is checked. Each
 * context is its own (the rate-limited one has the limiter on), with its own PostgreSQL. Needs Docker.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate",
			"app.security.rate-limit.enabled=false" })
@ActiveProfiles("test")
@Import({ PostgresContainerConfig.class, OwnerIntakeLoggingTest.ConstraintViolationOnDemand.class })
@ExtendWith(OutputCaptureExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OwnerIntakeLoggingTest {

	static final String ENDPOINT = "/api/v1/public/owner-intake-requests";
	static final String NAME = "Nguyen Van Zeta";
	static final String PHONE = "0909 777 123";
	static final String EMAIL = "zeta@example.com";
	static final String ADDRESS = "99 Duong Zeta";
	static final String LATITUDE = "16.0123";
	static final String LONGITUDE = "108.1234";
	static final String CLIENT_IP = "127.0.0.1";
	static final List<String> PERSONAL_DATA = List.of(NAME, PHONE, "0909777123", "+84909777123", "909777123", EMAIL,
			ADDRESS, LATITUDE, LONGITUDE, CLIENT_IP);

	/** Set by a test: the next activity write fails like a unique-key violation that quotes the phone. */
	static final AtomicBoolean FAIL_NEXT_WRITE = new AtomicBoolean();
	static final String VIOLATION_MESSAGE = "could not execute statement [ERROR: duplicate key value violates unique "
			+ "constraint \"owner_leads_phone\" Detail: Key (phone_normalized)=(+84909777123) already exists. "
			+ "Typed: 0909 777 123]";

	@TestConfiguration(proxyBeanMethods = false)
	static class ConstraintViolationOnDemand {

		@Bean
		@Primary
		OwnerLeadActivityDomainRepository failingOnDemand(OwnerLeadActivityRepositoryAdapter real) {
			return new OwnerLeadActivityDomainRepository() {
				@Override
				public void save(OwnerLeadActivity activity) {
					if (FAIL_NEXT_WRITE.getAndSet(false)) {
						throw new DataIntegrityViolationException(VIOLATION_MESSAGE);
					}
					real.save(activity);
				}

				@Override
				public List<OwnerLeadActivity> findByLeadIdOrderByOccurredAt(OwnerLeadId leadId) {
					return real.findByLeadIdOrderByOccurredAt(leadId);
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
	}

	@AfterAll
	void closeHttpClient() {
		api.http().close();
	}

	/** Every test submits the same distinctive values, so the 5-minute repeat rule must not join them. */
	@BeforeEach
	void emptyIntakeTables() {
		deleteIntakeRows(jdbc);
		FAIL_NEXT_WRITE.set(false);
	}

	@Test
	void logs_successfulSubmission_containNoPersonalData(CapturedOutput output) throws Exception {
		int start = output.getAll().length();

		Result result = submit(api);

		assertEquals(202, result.status(), result.rawBody());
		assertNoPersonalData(output.getAll().substring(start));
	}

	/** The handler logs only the exception class and the correlation id, never the message or the stack trace. */
	@Test
	void logs_forced500ConstraintViolation_containNoPersonalData(CapturedOutput output) throws Exception {
		int start = output.getAll().length();
		FAIL_NEXT_WRITE.set(true);

		Result result = submit(api);

		assertEquals(500, result.status(), result.rawBody());
		assertEquals("INTERNAL_ERROR", result.body().path("code").asString());
		String logs = output.getAll().substring(start);
		assertTrue(logs.contains("DataIntegrityViolationException"), "the 500 is logged: " + logs);
		assertTrue(logs.contains(result.header("X-Correlation-Id")), "with its correlation id: " + logs);
		assertFalse(logs.contains("duplicate key value"), "the exception message is not logged: " + logs);
		assertNoPersonalData(logs);
	}

	/**
	 * The same failure path with a real PostgreSQL constraint: a check constraint (added for this test only) refuses
	 * the lead row, and the database error quotes the whole row in its detail.
	 */
	@Test
	void logs_realCheckConstraintViolation_containNoPersonalData(CapturedOutput output) throws Exception {
		jdbc.execute("alter table owner_leads add constraint test_refuse_zeta_phone "
				+ "check (phone_normalized <> '+84909777123') not valid");
		try {
			int start = output.getAll().length();

			Result result = submit(api);

			assertEquals(500, result.status(), result.rawBody());
			assertEquals("INTERNAL_ERROR", result.body().path("code").asString());
			String logs = output.getAll().substring(start);
			assertTrue(logs.contains(result.header("X-Correlation-Id")), "the 500 is logged: " + logs);
			assertFalse(logs.contains("Failing row"), "the row is not logged: " + logs);
			assertNoPersonalData(logs);
		} finally {
			jdbc.execute("alter table owner_leads drop constraint test_refuse_zeta_phone");
		}
	}

	/** W9-12 end to end with the real rule (10 per client IP per hour): the 11th call is refused, without the IP. */
	@Nested
	@NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
	@SpringBootTest(
			webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
			properties = {
				"spring.flyway.enabled=true",
				"spring.jpa.hibernate.ddl-auto=validate",
				"app.security.rate-limit.enabled=true" })
	@ActiveProfiles("test")
	@Import(PostgresContainerConfig.class)
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	class WithRateLimit {

		@Value("${local.server.port}")
		private int limitedPort;

		@Autowired
		private UserDomainRepository limitedUserRepository;

		@Autowired
		private BCryptPasswordEncoder limitedPasswordEncoder;

		@Autowired
		private IdGenerator<String> limitedUlidGenerator;

		private AcquisitionApiTestSupport limitedApi;

		@BeforeAll
		void setUpLimited() {
			limitedApi = new AcquisitionApiTestSupport(limitedPort, limitedUserRepository, limitedPasswordEncoder,
					limitedUlidGenerator);
		}

		@AfterAll
		void closeLimitedHttpClient() {
			limitedApi.http().close();
		}

		@Test
		void logs_rateLimitedSubmission_containNoPersonalDataAndNoIp(CapturedOutput output) throws Exception {
			int start = output.getAll().length();

			for (int call = 1; call <= 10; call++) {
				Result accepted = submit(limitedApi);
				assertEquals(202, accepted.status(), "call " + call + ": " + accepted.rawBody());
			}
			Result refused = submit(limitedApi);

			assertEquals(429, refused.status(), refused.rawBody());
			assertEquals("RATE_LIMITED", refused.body().path("code").asString());
			assertEquals(0, refused.body().path("errors").size());
			assertNotNull(refused.header("Retry-After"));
			assertTrue(Long.parseLong(refused.header("Retry-After")) > 0);
			assertEquals(refused.header("X-Correlation-Id"), refused.body().path("correlationId").asString());
			String logs = output.getAll().substring(start);
			assertTrue(logs.contains("Rate limit exceeded"), "the refusal is logged: " + logs);
			assertNoPersonalData(logs);
		}
	}

	// ==================== Helpers ====================

	/** POSTs the distinctive submission to 127.0.0.1. */
	static Result submit(AcquisitionApiTestSupport api) throws Exception {
		Map<String, Object> consent = new LinkedHashMap<>();
		consent.put("accepted", true);
		consent.put("noticeVersion", "owner-intake-2026-10");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("contactName", NAME);
		body.put("phone", PHONE);
		body.put("email", EMAIL);
		body.put("intent", "LONG_TERM_RENT");
		body.put("provinceCode", "48");
		body.put("wardCode", "20242");
		body.put("addressDetail", ADDRESS);
		body.put("latitude", new java.math.BigDecimal(LATITUDE));
		body.put("longitude", new java.math.BigDecimal(LONGITUDE));
		body.put("consent", consent);

		return api.send(HttpRequest.newBuilder(api.ipv4Uri(ENDPOINT))
				.timeout(AcquisitionApiTestSupport.REQUEST_TIMEOUT)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(api.json().writeValueAsString(body), StandardCharsets.UTF_8))
				.build());
	}

	static void assertNoPersonalData(String logs) {
		for (String value : PERSONAL_DATA) {
			assertFalse(logs.contains(value), "the logs contain " + value + ":\n" + logs);
		}
	}

	static void deleteIntakeRows(JdbcTemplate jdbc) {
		jdbc.update("delete from owner_intake_idempotency_keys");
		jdbc.update("delete from owner_lead_duplicate_candidates");
		jdbc.update("delete from owner_lead_activities");
		jdbc.update("delete from owner_lead_access_logs");
		jdbc.update("delete from owner_leads");
		jdbc.update("delete from consent_records");
	}
}
