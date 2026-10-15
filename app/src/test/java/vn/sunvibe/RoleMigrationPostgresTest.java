package vn.sunvibe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Wave W6 role migrations on a real PostgreSQL (auth-roles brief BR-1, BR-2, US-2; owner decisions W6-1, W6-2).
 * The database is migrated to the last version before W6, filled the way {@code RoleDataInitializer} and real
 * accounts left it (the 8 old roles, users holding them), then migrated to the latest version.
 * Needs Docker, like {@link FlywayPostgresMigrationTest}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoleMigrationPostgresTest {

	private static final String LAST_VERSION_BEFORE_W6 = "202610150221";
	private static final String SEED_SCRIPT = "db/migration/V202610160900__seed_roles.sql";
	private static final String BACKFILL_SCRIPT = "db/migration/V202610160901__backfill_target_roles.sql";

	private static final List<String> OLD_ROLES = List.of(
			"SUPER_ADMIN", "ADMIN", "MANAGER", "LANDLORD", "TENANT", "LEADER", "EMPLOYEE", "USER");
	private static final Set<String> ALL_ROLES = Set.of(
			"SUPER_ADMIN", "ADMIN", "MANAGER", "LANDLORD", "TENANT", "LEADER", "EMPLOYEE", "USER",
			"OWNER", "CUSTOMER", "SALE", "SALE_LEADER", "SALES_MANAGER", "LISTING_REVIEWER");

	/** user id -> roles before W6. */
	private static final Map<String, List<String>> USERS_BEFORE = Map.of(
			"01LANDLORD0000000000000001", List.of("LANDLORD"),
			"01LANDLORD0000000000000002", List.of("LANDLORD"),
			"01TENANT000000000000000001", List.of("TENANT"),
			"01EMPLOYEE0000000000000001", List.of("EMPLOYEE"),
			"01LANDLORDTENANT0000000001", List.of("LANDLORD", "TENANT"),
			"01MANAGER00000000000000001", List.of("MANAGER"),
			"01LEADER000000000000000001", List.of("LEADER"),
			"01USER00000000000000000001", List.of("USER"),
			"01ADMIN0000000000000000001", List.of("ADMIN"),
			"01SUPERADMIN00000000000001", List.of("SUPER_ADMIN"));

	private final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.6-alpine"));
	private JdbcTemplate jdbc;
	private Flyway flywayToLatest;

	@BeforeAll
	void migrateOldDatabaseThenLatest() {
		postgres.start();
		DriverManagerDataSource dataSource = new DriverManagerDataSource(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		jdbc = new JdbcTemplate(dataSource);

		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
				.target(LAST_VERSION_BEFORE_W6).load().migrate();

		// What RoleDataInitializer seeded before W6: one row per old role, random ids
		for (String role : OLD_ROLES) {
			jdbc.update("INSERT INTO roles (id, name, description) VALUES (?, ?, ?)", UUID.randomUUID(), role,
					role + " role");
		}
		AtomicInteger phone = new AtomicInteger(1);
		USERS_BEFORE.forEach((userId, roles) -> {
			jdbc.update("INSERT INTO users (id, full_name, username, password, user_status, created_date, last_update)"
					+ " VALUES (?, 'W6 user', ?, 'hashed', 'ACTIVE', now(), now())", userId,
					"09" + String.format("%08d", phone.getAndIncrement()));
			for (String role : roles) {
				jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE name = ?", userId,
						role);
			}
		});

		flywayToLatest = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
		flywayToLatest.migrate();
	}

	@AfterAll
	void stop() {
		postgres.stop();
	}

	/** BR-1 (W6-1) */
	@Test
	void migrate_existingDatabase_hasTheFourteenRolesOnce() {
		List<String> names = jdbc.queryForList("SELECT name FROM roles", String.class);

		assertEquals(ALL_ROLES, Set.copyOf(names));
		assertEquals(14, names.size());
	}

	/** BR-2, US-2 AC-1 (W6-2): LANDLORD -> OWNER, TENANT -> CUSTOMER, EMPLOYEE -> SALE; old roles kept. */
	@Test
	void migrate_existingDatabase_dualGrantsTheMappedRoles() {
		assertEquals(Set.of("LANDLORD", "OWNER"), roles("01LANDLORD0000000000000001"));
		assertEquals(Set.of("LANDLORD", "OWNER"), roles("01LANDLORD0000000000000002"));
		assertEquals(Set.of("TENANT", "CUSTOMER"), roles("01TENANT000000000000000001"));
		assertEquals(Set.of("EMPLOYEE", "SALE"), roles("01EMPLOYEE0000000000000001"));
		assertEquals(Set.of("LANDLORD", "OWNER", "TENANT", "CUSTOMER"), roles("01LANDLORDTENANT0000000001"));
	}

	/** US-2 AC-2: as many holders of each new role as of the old one. */
	@Test
	void migrate_existingDatabase_newRoleCountsMatchOldRoleCounts() {
		assertEquals(holders("LANDLORD"), holders("OWNER"));
		assertEquals(holders("TENANT"), holders("CUSTOMER"));
		assertEquals(holders("EMPLOYEE"), holders("SALE"));
		assertEquals(3, holders("OWNER"));
	}

	/** US-2 AC-3: MANAGER, LEADER, USER, ADMIN and SUPER_ADMIN accounts are untouched; nobody gets the other new roles. */
	@Test
	void migrate_existingDatabase_leavesOtherAccountsAlone() {
		assertEquals(Set.of("MANAGER"), roles("01MANAGER00000000000000001"));
		assertEquals(Set.of("LEADER"), roles("01LEADER000000000000000001"));
		assertEquals(Set.of("USER"), roles("01USER00000000000000000001"));
		assertEquals(Set.of("ADMIN"), roles("01ADMIN0000000000000000001"));
		assertEquals(Set.of("SUPER_ADMIN"), roles("01SUPERADMIN00000000000001"));
		assertEquals(0, holders("SALE_LEADER") + holders("SALES_MANAGER") + holders("LISTING_REVIEWER"));
	}

	/** Both scripts are idempotent: running them again changes nothing. */
	@Test
	void migrationScripts_runAgain_changeNothing() throws IOException {
		int roleRows = count("roles");
		int userRoleRows = count("user_roles");

		jdbc.execute(script(SEED_SCRIPT));
		jdbc.execute(script(BACKFILL_SCRIPT));

		assertEquals(roleRows, count("roles"));
		assertEquals(userRoleRows, count("user_roles"));
		assertTrue(flywayToLatest.info().pending().length == 0);
	}

	private Set<String> roles(String userId) {
		return Set.copyOf(jdbc.queryForList(
				"SELECT r.name FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = ?", String.class,
				userId));
	}

	private int holders(String role) {
		return jdbc.queryForObject(
				"SELECT count(*) FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE r.name = ?", Integer.class,
				role);
	}

	private int count(String table) {
		return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
	}

	private static String script(String path) throws IOException {
		return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
	}
}
