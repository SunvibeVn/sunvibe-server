package vn.sunvibe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;

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

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Test
	void migrate_emptyDatabase_appliesTheBaselineMigrations() {
		List<String> applied = jdbc.queryForList(
				"SELECT script FROM flyway_schema_history ORDER BY installed_rank", String.class);

		assertEquals(
				List.of("V202610150220__create_permission_tables.sql", "V202610150221__create_property_tables.sql",
						"V202610160900__seed_roles.sql", "V202610160901__backfill_target_roles.sql",
						"V202610161000__create_units_table.sql", "V202610161001__create_property_ownerships_table.sql",
						"V202610161002__add_version_to_properties.sql",
						"V202610161003__backfill_units_and_property_ownerships.sql",
						"V202610161100__create_administrative_areas.sql",
						"V202610161101__seed_da_nang_administrative_areas.sql",
						"V202610161110__create_consent_records_table.sql",
						"V202610161111__create_owner_leads_tables.sql",
						"V202610161112__create_owner_lead_activity_tables.sql",
						"V202610161113__create_owner_intake_idempotency_keys_table.sql",
						"V202610161114__create_sale_territories_tables.sql",
						"R__property_master_data.sql"),
				applied.subList(applied.size() - 16, applied.size()));
	}

	/** Wave W8 (property-units spec section 4, W8-14): units, ownerships and properties.version come from Flyway. */
	@Test
	void migrate_emptyDatabase_createsTheW8TablesAndVersionColumn() {
		List<String> tables = jdbc.queryForList(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
		List<String> propertyColumns = jdbc.queryForList("SELECT column_name FROM information_schema.columns"
				+ " WHERE table_schema = 'public' AND table_name = 'properties'", String.class);

		assertTrue(tables.contains("units"), "missing table units");
		assertTrue(tables.contains("property_ownerships"), "missing table property_ownerships");
		assertTrue(propertyColumns.contains("version"), "missing column properties.version");
	}

	/** Acquisition-owner-intake spec 4.2 and lead-queue spec 4: the acquisition tables come from Flyway only. */
	@Test
	void migrate_emptyDatabase_createsTheAcquisitionTables() {
		List<String> tables = jdbc.queryForList(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);

		for (String table : List.of("consent_records", "owner_leads", "owner_lead_duplicate_candidates",
				"owner_lead_activities", "owner_lead_access_logs", "owner_intake_idempotency_keys",
				"sale_territories", "sale_territory_changes")) {
			assertTrue(tables.contains(table), () -> "missing table " + table);
		}
	}

	/** Roles come from Flyway since W6 (auth-roles BR-1), no longer from RoleDataInitializer. */
	@Test
	void migrate_emptyDatabase_seedsTheFourteenRoles() {
		assertEquals(List.of("ADMIN", "CUSTOMER", "EMPLOYEE", "LANDLORD", "LEADER", "LISTING_REVIEWER", "MANAGER",
						"OWNER", "SALE", "SALES_MANAGER", "SALE_LEADER", "SUPER_ADMIN", "TENANT", "USER"),
				jdbc.queryForList("SELECT name FROM roles ORDER BY name COLLATE \"C\"", String.class));
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

	/** Wave W8 (property-units BR-13, C-13): the table stays, but no entity maps it any more. */
	@Test
	void context_noEntityMapsPropertyAvailability() {
		List<String> mappedTables = entityManagerFactory.getMetamodel().getEntities().stream()
				.map(entity -> entity.getJavaType().getAnnotation(Table.class))
				.filter(table -> table != null)
				.map(Table::name)
				.toList();

		assertTrue(!mappedTables.isEmpty(), "no @Table found: the metamodel check would pass vacuously");
		assertFalse(mappedTables.contains("property_availability"),
				"an entity still maps property_availability: " + mappedTables);
	}
}
