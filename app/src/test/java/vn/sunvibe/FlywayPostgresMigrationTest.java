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
 * (ADR-002). The context only starts if every entity table and column exists with a compatible type,
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

	/** Tables that only Hibernate created before they moved to Flyway (ADR-002). */
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
						"V202610221000__replace_owner_lead_property_types.sql",
						"V202610231900__add_owner_lead_survey_columns_and_intent_changes.sql",
						"V202610231910__create_survey_tables.sql",
						"V202610241730__add_owner_lead_activity_reference_detail.sql",
						"V202610241731__create_survey_checklist_tables.sql",
						"V202611021400__create_survey_media_tables.sql",
						"V202611021401__widen_survey_security_events_for_media.sql",
						"V202611021402__widen_survey_access_logs_for_media.sql",
						"V202611021403__index_survey_access_logs_media_list.sql",
						"V202611021404__validate_survey_media_constraints.sql",
						"V202611041530__widen_owner_lead_activity_for_checklist_review.sql",
						"V202611041531__validate_owner_lead_activity_reference_detail.sql",
						"V202611041532__checklist_submission_and_review.sql",
						"V202611041533__validate_checklist_review_constraints.sql",
						"V202611041534__index_survey_checklists_review.sql",
						"V202611051000__expand_user_identifiers.sql",
						"V202611051001__validate_users_email_key_check.sql",
						"V202611051002__index_users_email_key.sql",
						"V202611051003__index_users_phone_key.sql",
						"V202611051004__index_users_keyless_email.sql",
						"V202611051005__expand_proofs_for_email.sql",
						"V202611051006__index_verification_codes_destination.sql",
						"V202611051007__index_verification_codes_expires_at.sql",
						"V202611051008__index_password_reset_token_expires_at.sql",
						"V202611051009__create_registration_email_challenges.sql",
						"V202611051010__create_auth_email_notifications.sql",
						"db.migration.V202611051011__Backfill_user_identifier_keys",
						"V202611051940__add_checklist_requalification.sql",
						"V202611051941__validate_checklist_requalification.sql",
						"V202611051942__index_one_current_passed.sql",
						"V202611061000__expand_properties_and_units_for_intake.sql",
						"V202611061001__validate_intake_property_constraints.sql",
						"V202611061002__index_units_unit_code_key.sql",
						"V202611061003__index_properties_ward_code_address_norm.sql",
						"V202611061004__index_properties_intake_id.sql",
						"V202611061005__create_property_intake_code_seq.sql",
						"V202611061006__create_inventory_intakes.sql",
						"V202611061007__create_inventory_owner_requests.sql",
						"V202611061008__create_inventory_owner_proofs.sql",
						"V202611061009__create_inventory_unit_mappings.sql",
						"V202611061010__create_inventory_duplicate_cases.sql",
						"V202611061011__create_inventory_suspensions.sql",
						"V202611061012__create_inventory_intake_events.sql",
						"V202611061013__create_inventory_access_logs.sql",
						"V202611061014__add_users_sign_in_losses.sql",
						"V202611061015__index_inventory_suspensions_unit_id.sql",
						"V202611151000__create_brokerage_agreements.sql",
						"V202611151001__create_brokerage_agreement_versions.sql",
						"V202611151002__create_brokerage_agreement_events.sql",
						"V202611151003__create_brokerage_agreement_access_logs.sql",
						"V202611161000__add_brokerage_agreement_version_content.sql",
						"V202611161001__create_brokerage_agreement_version_fees.sql",
						"V202611171000__add_brokerage_agreement_version_fee_policy.sql",
						"V202611171001__create_brokerage_agreement_version_fee_tiers.sql",
						"V202611171002__add_brokerage_agreement_version_sent_at.sql",
						"V202611171500__create_outbox_events.sql",
						"V202611171501__add_brokerage_agreement_version_owner_answer.sql",
						"V202611171502__add_brokerage_agreement_owner_and_lapse_indexes.sql",
						"R__property_master_data.sql"),
				applied.subList(applied.size() - 74, applied.size()));
	}

	/** property-units spec section 4, W8-14: units, ownerships and properties.version come from Flyway. */
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

	/**
	 * E05-S5b spec 4.2 (ADR-014 decision 4, S5b-D9, S5b-D15): the outbox table in app's migration folder, its
	 * constraints all valid and its six indexes valid and ready (the table's own half of the whole-database check of
	 * MediaVerificationQueries V10), and no row.
	 */
	@Test
	void migrate_emptyDatabase_createsTheOutboxTable() {
		List<String> columns = jdbc.queryForList("SELECT column_name || ' ' || data_type || ' ' || is_nullable"
				+ " FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'outbox_events'"
				+ " ORDER BY ordinal_position", String.class);
		assertEquals(List.of(
						"id bigint NO", "event_id bigint NO", "event_type character varying NO",
						"event_version integer NO", "consumer character varying NO",
						"source_module character varying NO", "aggregate_type character varying NO",
						"aggregate_id character varying NO", "payload jsonb NO", "correlation_id character varying YES",
						"occurred_at timestamp with time zone NO", "status character varying NO", "attempts integer NO",
						"next_attempt_at timestamp with time zone NO", "lease_token character varying YES",
						"published_at timestamp with time zone YES", "last_error_code character varying YES"),
				columns);

		List<String> constraints = jdbc.queryForList("SELECT conname || ' ' || contype::text FROM pg_constraint"
				+ " WHERE conrelid = 'outbox_events'::regclass ORDER BY conname", String.class);
		assertEquals(List.of("outbox_events_attempts_check c", "outbox_events_event_consumer_key u",
						"outbox_events_event_version_check c", "outbox_events_pkey p", "outbox_events_status_check c"),
				constraints);

		List<String> partialIndexes = jdbc.queryForList("SELECT indexname || ' ' || indexdef FROM pg_indexes"
				+ " WHERE schemaname = 'public' AND tablename = 'outbox_events'"
				+ " AND indexname NOT IN ('outbox_events_pkey', 'outbox_events_event_consumer_key') ORDER BY indexname",
				String.class);
		assertEquals(4, partialIndexes.size(), () -> "partial indexes: " + partialIndexes);
		assertTrue(partialIndexes.get(0).startsWith("outbox_events_dead_idx ")
				&& partialIndexes.get(0).contains("(id) WHERE ((status)::text = 'DEAD'::text)"), partialIndexes::toString);
		assertTrue(partialIndexes.get(1).startsWith("outbox_events_done_published_idx ")
				&& partialIndexes.get(1).contains("(published_at) WHERE ((status)::text = 'DONE'::text)"),
				partialIndexes::toString);
		assertTrue(partialIndexes.get(2).startsWith("outbox_events_pending_consumer_due_idx ")
				&& partialIndexes.get(2).contains("(consumer, next_attempt_at, id) WHERE ((status)::text = 'PENDING'::text)"),
				partialIndexes::toString);
		assertTrue(partialIndexes.get(3).startsWith("outbox_events_pending_due_idx ")
				&& partialIndexes.get(3).contains("(next_attempt_at, id) WHERE ((status)::text = 'PENDING'::text)"),
				partialIndexes::toString);

		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pg_constraint"
				+ " WHERE conrelid = 'outbox_events'::regclass AND NOT convalidated", Integer.class));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pg_index"
				+ " WHERE indrelid = 'outbox_events'::regclass AND (NOT indisvalid OR NOT indisready)", Integer.class));
		assertEquals(6, jdbc.queryForObject("SELECT count(*) FROM pg_index"
				+ " WHERE indrelid = 'outbox_events'::regclass", Integer.class));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class));
	}

	/** Roles come from Flyway (auth-roles BR-1), no longer from RoleDataInitializer. */
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

	/** property-units BR-13, C-13: the table stays, but no entity maps it any more. */
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

	/**
	 * Each module's {@code @EntityScan} adds to the others: one table of each module stays mapped, compared exactly as
	 * its entity's {@code @Table} writes it, so a scan that replaced the others, or a renamed annotation, fails here.
	 */
	@Test
	void context_everyModuleEntityStaysMapped() {
		List<String> mappedTables = entityManagerFactory.getMetamodel().getEntities().stream()
				.map(entity -> entity.getJavaType().getAnnotation(Table.class))
				.filter(table -> table != null)
				.map(Table::name)
				.toList();

		assertTrue(!mappedTables.isEmpty(), "no @Table found: the metamodel check would pass vacuously");
		for (String table : List.of("USERS", "units", "owner_leads", "survey_media_jobs", "brokerage_agreements")) {
			assertTrue(mappedTables.contains(table), () -> "no entity maps " + table + ": " + mappedTables);
		}
	}
}
