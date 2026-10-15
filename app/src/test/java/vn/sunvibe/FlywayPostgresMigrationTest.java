package vn.sunvibe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Builds the schema with Flyway only, on a real PostgreSQL 17 (Testcontainers), and lets Hibernate validate it
 * (ADR-002, wave W3). The context only starts if every entity table and column exists with a compatible type,
 * so a missing migration fails here; nullability, lengths, indexes and constraints are not checked by Hibernate.
 * Needs Docker; it is not skipped without it, on purpose: a silent skip in CI would hide a broken migration.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
// The container's connection details replace the H2 datasource of application-test.yml
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class FlywayPostgresMigrationTest {

	/** Tables that only Hibernate created before W3. */
	private static final List<String> BASELINE_TABLES = List.of(
			"permission", "role_permission",
			"properties", "property_images", "property_amenity", "property_rules", "property_document",
			"property_prices", "property_availability", "amenity_types", "rule_categories", "document_types");


	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void migrate_emptyDatabase_appliesTheBaselineMigrations() {
		List<String> applied = jdbc.queryForList(
				"SELECT script FROM flyway_schema_history ORDER BY installed_rank", String.class);

		assertEquals(
				List.of("V202610150220__create_permission_tables.sql", "V202610150221__create_property_tables.sql",
						"R__property_master_data.sql"),
				applied.subList(applied.size() - 3, applied.size()));
	}

	@Test
	void migrate_emptyDatabase_createsTheTablesHibernateUsedToCreate() {
		List<String> tables = jdbc.queryForList(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);

		for (String table : BASELINE_TABLES) {
			assertTrue(tables.contains(table), () -> "missing table " + table);
		}
	}

	@Test
	void migrate_emptyDatabase_seedsTheMasterDataOfPropertyMasterDataInitializer() {
		assertEquals(List.of(1, 2, 3, 4, 5, 6),
				jdbc.queryForList("SELECT id FROM rule_categories ORDER BY id", Integer.class));
		assertEquals(List.of(1, 2, 3, 4, 10, 11, 12, 13, 14, 15, 16, 17),
				jdbc.queryForList("SELECT id FROM amenity_types ORDER BY id", Integer.class));
		assertEquals(List.of(1, 2, 3, 4, 5),
				jdbc.queryForList("SELECT id FROM document_types ORDER BY id", Integer.class));

		assertEquals("PARTIES",
				jdbc.queryForObject("SELECT category_name FROM rule_categories WHERE id = 5", String.class));
		assertEquals("Kitchen",
				jdbc.queryForObject("SELECT category_name FROM amenity_types WHERE id = 14", String.class));
		assertEquals(1825,
				jdbc.queryForObject("SELECT renewal_frequency_days FROM document_types WHERE id = 5", Integer.class));
	}
}
