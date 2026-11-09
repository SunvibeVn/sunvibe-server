package vn.sunvibe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext.HierarchyMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestContextManager;

/**
 * SV-175: the contexts of the API tests share one PostgreSQL server, never a database. Two contexts with different
 * properties are two entries of Spring's context cache, loaded here the way the test framework loads them.
 */
class PostgresContainerConfigTest {

	@Test
	void twoContexts_shareTheServer_notTheRows() throws Exception {
		TestContext first = new TestContextManager(FirstContext.class).getTestContext();
		TestContext second = new TestContextManager(SecondContext.class).getTestContext();
		JdbcTemplate one = jdbc(first.getApplicationContext());
		JdbcTemplate two = jdbc(second.getApplicationContext());

		one.execute("create table if not exists isolation_probe (marker text)");
		two.execute("create table if not exists isolation_probe (marker text)");
		one.update("insert into isolation_probe values ('first')");

		assertEquals(1, one.queryForObject("select count(*) from isolation_probe", Integer.class));
		assertEquals(0, two.queryForObject("select count(*) from isolation_probe", Integer.class),
				"the second context sees the first context's row");
		assertEquals(server(one), server(two), "the two contexts run on two servers");
		assertNotEquals(one.queryForObject("select current_database()", String.class),
				two.queryForObject("select current_database()", String.class));
	}

	@Test
	void closingAContext_leavesTheServerToTheOthers() throws Exception {
		TestContext closed = new TestContextManager(ThirdContext.class).getTestContext();
		long server = server(jdbc(closed.getApplicationContext()));
		closed.markApplicationContextDirty(HierarchyMode.EXHAUSTIVE);

		TestContext open = new TestContextManager(FourthContext.class).getTestContext();

		assertEquals(server, server(jdbc(open.getApplicationContext())), "the next context runs on another server");
	}

	private static JdbcTemplate jdbc(ApplicationContext context) {
		return context.getBean(JdbcTemplate.class);
	}

	/** The identifier initdb gave the cluster: two containers never share it. */
	private static long server(JdbcTemplate jdbc) {
		return jdbc.queryForObject("select system_identifier from pg_control_system()", Long.class);
	}

	/** The auto-configurations of the full application that connect it to the database. */
	@Configuration(proxyBeanMethods = false)
	@ImportAutoConfiguration({ ServiceConnectionAutoConfiguration.class, DataSourceAutoConfiguration.class,
		JdbcTemplateAutoConfiguration.class })
	@Import(PostgresContainerConfig.class)
	static class Probe {
	}

	@SpringBootTest(classes = Probe.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
			properties = "isolation.probe=first")
	@ActiveProfiles("test")
	static class FirstContext {
	}

	@SpringBootTest(classes = Probe.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
			properties = "isolation.probe=second")
	@ActiveProfiles("test")
	static class SecondContext {
	}

	@SpringBootTest(classes = Probe.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
			properties = "isolation.probe=third")
	@ActiveProfiles("test")
	static class ThirdContext {
	}

	@SpringBootTest(classes = Probe.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
			properties = "isolation.probe=fourth")
	@ActiveProfiles("test")
	static class FourthContext {
	}
}
