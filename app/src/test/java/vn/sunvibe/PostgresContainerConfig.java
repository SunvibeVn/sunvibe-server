package vn.sunvibe;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.flyway.autoconfigure.FlywayConnectionDetails;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The PostgreSQL of the API tests: one container for the whole test JVM, and a new database on it for each Spring
 * context, so a context never sees another context's rows (SV-175). The container is not a bean: closing one context
 * must not stop the server the others use. Testcontainers removes it when the JVM ends.
 *
 * <p>The pool keeps its size from {@code application.yml}; {@code src/test/resources/config/application.yml} only lowers
 * its idle connections, because the cached contexts share one server. A test that reads cluster-wide views such as
 * {@code pg_locks} filters them by {@code current_database()}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfig {

	@Bean
	PostgresTestDatabase postgresTestDatabase() {
		return SharedServer.newDatabase();
	}

	@Bean
	JdbcConnectionDetails postgresConnectionDetails(PostgresTestDatabase database) {
		return new JdbcConnectionDetails() {

			@Override
			public String getUsername() {
				return database.container().getUsername();
			}

			@Override
			public String getPassword() {
				return database.container().getPassword();
			}

			@Override
			public String getJdbcUrl() {
				return database.jdbcUrl();
			}
		};
	}

	/**
	 * Flyway migrates on its own connection, outside the pool, as {@code @ServiceConnection} made it do: a context with a
	 * pool of one (MediaCreatePoolOfOnePostgresTest) could not start otherwise.
	 */
	@Bean
	FlywayConnectionDetails postgresFlywayConnectionDetails(PostgresTestDatabase database) {
		return new FlywayConnectionDetails() {

			@Override
			public String getUsername() {
				return database.container().getUsername();
			}

			@Override
			public String getPassword() {
				return database.container().getPassword();
			}

			@Override
			public String getJdbcUrl() {
				return database.jdbcUrl();
			}
		};
	}

	/**
	 * The database of one Spring context on the shared server. The seed tests run psql in {@link #container()} against
	 * {@link #name()}.
	 */
	public record PostgresTestDatabase(PostgreSQLContainer container, String name, String jdbcUrl) {
	}

	/** Starts the server on first use; the holder idiom keeps tests without PostgreSQL from starting it. */
	private static final class SharedServer {

		/**
		 * Up to 32 cached contexts (Spring's default) share the server. Each keeps 2 idle connections
		 * (src/test/resources/config/application.yml), about 64, while the running class may fill its pool of 20 and a
		 * race test opens its own connections besides; the default of 100 leaves too little room for that.
		 */
		private static final int MAX_CONNECTIONS = 500;
		private static final PostgreSQLContainer CONTAINER = start();
		private static final AtomicInteger DATABASES = new AtomicInteger();

		private static PostgreSQLContainer start() {
			PostgreSQLContainer container = new PostgreSQLContainer(DockerImageName.parse("postgres:17.6-alpine"))
					.withCommand("postgres", "-c", "fsync=off", "-c", "max_connections=" + MAX_CONNECTIONS);
			container.start();
			return container;
		}

		static PostgresTestDatabase newDatabase() {
			String name = "context_" + DATABASES.incrementAndGet();
			try (Connection admin = DriverManager.getConnection(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(),
					CONTAINER.getPassword()); Statement statement = admin.createStatement()) {
				statement.execute("CREATE DATABASE " + name);
			} catch (SQLException e) {
				throw new IllegalStateException("could not create the test database " + name, e);
			}
			// The container's own URL with the new database name; its parameters stay
			String url = CONTAINER.getJdbcUrl().replaceFirst("/" + CONTAINER.getDatabaseName() + "(\\?|$)",
					"/" + name + "$1");
			if (url.equals(CONTAINER.getJdbcUrl())) {
				// The contexts would share the default database without a word
				throw new IllegalStateException("could not name the database " + name + " in " + url);
			}
			return new PostgresTestDatabase(CONTAINER, name, url);
		}
	}
}
