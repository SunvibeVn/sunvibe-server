package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.property.core.infrastructure.config.IntakeSwitchesConfig;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * The switches of property-create intake A-11, D5-17 on their defaults: the intake is off, so every path of it answers as
 * if it did not exist, after the chain: no token 401, any token 404. Both switches on stop the start-up.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate",
		"app.security.rate-limit.enabled=false" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntakeSwitchesApiTest {

	private static final String LEAD = "01LEAD000000000000000000AA";

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private UserDomainRepository userRepository;

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	@Autowired
	private IdGenerator<String> ids;

	private AcquisitionApiTestSupport api;
	private TestUser sale;
	private TestUser customer;

	@BeforeAll
	void setUp() throws Exception {
		api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder, ids);
		sale = api.logIn(api.createUser("Switch Sale", UserStatus.ACTIVE, "SALE"));
		customer = api.logIn(api.createUser("Switch Customer", UserStatus.ACTIVE, "CUSTOMER"));
	}

	@Test
	void switchOff_404WithToken_401Without() throws Exception {
		for (String path : new String[]{"/api/v1/staff/owner-leads/" + LEAD + "/inventory-intake",
				"/api/v1/staff/inventory-cases", "/api/v1/me/property-confirmations"}) {
			Result withToken = api.get(sale, path);
			assertEquals(404, withToken.status(), path);
			assertEquals("NOT_FOUND", withToken.body().path("code").asString(), path);
			assertEquals(404, api.get(customer, path).status(), path);
			Result without = api.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
					.timeout(Duration.ofSeconds(30)).GET().build());
			assertEquals(401, without.status(), path);
		}
	}

	@Test
	void switchOff_writePath_404() throws Exception {
		Result write = api.post(sale, "/api/v1/staff/owner-leads/" + LEAD + "/inventory-intake/completion", Map.of());

		assertEquals(404, write.status());
	}

	/** T-3: the two turn over together; running both would let a legacy property dodge the intake. */
	@Test
	void bothOn_failsStart() {
		MockEnvironment both = new MockEnvironment().withProperty("app.property.intake.enabled", "true")
				.withProperty("app.property.direct-create.enabled", "true");

		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> new IntakeSwitchesConfig(both));

		assertTrue(failure.getMessage().contains("app.property.direct-create.enabled"), failure.getMessage());
	}

	@Test
	void eitherAlone_starts() {
		new IntakeSwitchesConfig(new MockEnvironment().withProperty("app.property.intake.enabled", "true")
				.withProperty("app.property.direct-create.enabled", "false"));
		new IntakeSwitchesConfig(new MockEnvironment());
	}
}
