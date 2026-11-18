package vn.sunvibe.brokerage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import vn.sunvibe.PostgresContainerConfig;

/**
 * The agreement list's indexes and its page query on a real PostgreSQL (E05-S7 spec S7-D1 to S7-D4, S7-D9, 5.3;
 * criteria MG-1, LS-3). The annotations are those of {@link AgreementPersistencePostgresTest}, so both share one
 * context. Needs Docker.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class AgreementListPersistencePostgresTest {

	@Autowired
	private JdbcTemplate jdbc;

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
