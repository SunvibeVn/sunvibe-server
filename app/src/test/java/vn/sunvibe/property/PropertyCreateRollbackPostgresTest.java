package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.property.core.application.command.PropertyCreateCommand;
import vn.sunvibe.property.core.application.shared.external.user.UserDirectoryPort;
import vn.sunvibe.property.core.application.usecase.PropertyCreateUseCase;
import vn.sunvibe.property.core.domain.exception.PropertyOwnerNotFoundException;
import vn.sunvibe.property.core.domain.model.enums.PropertyType;

/**
 * Rollback of property create on a real PostgreSQL (property-units BR-12, E-1; W8-11): the property,
 * its canonical unit and its ownership are written in one transaction, so when the owner turns ineligible between the
 * eligibility check and the OWNER grant (auth-roles BR-7) the create throws PropertyOwnerNotFoundException (422 at
 * the API) and none of the three rows stays.
 * The user directory is replaced by one that says "eligible" at the check and refuses at the grant; every other
 * bean is the real one. The annotations are those of {@link vn.sunvibe.FlywayPostgresMigrationTest} plus the stub, so
 * this class has a context (and a database) of its own. Needs Docker, like that test.
 * <p>
 * The test passes before the unit and the ownership are written (the property alone was already rolled back); it
 * guards property-units BR-12 for the rows that are added.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
// The container's connection details replace the H2 datasource of application-test.yml
@ActiveProfiles("test")
@Import({ PostgresContainerConfig.class, PropertyCreateRollbackPostgresTest.RacingOwnerDirectory.class })
class PropertyCreateRollbackPostgresTest {

	private static final String PROPERTY_CODE = "WT-RBK-001";
	private static final String OWNER_ID = "01OWNER0000000000000000000";
	private static final String ACTOR_ID = "01ACTOR0000000000000000000";
	private static final String ACTOR_USERNAME = "0912345678";

	@Autowired
	private PropertyCreateUseCase propertyCreateUseCase;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void execute_ownerIneligibleAtGrant_storesNoPropertyUnitOrOwnership() {
		int ownershipsBefore = countRows("select count(*) from property_ownerships");

		assertThrows(PropertyOwnerNotFoundException.class, () -> propertyCreateUseCase.execute(command()));

		assertEquals(0, countRows("select count(*) from properties where property_code = ?", PROPERTY_CODE),
				"no property row");
		assertEquals(0, countRows("select count(*) from units where unit_code = ?", PROPERTY_CODE), "no unit row");
		assertEquals(ownershipsBefore, countRows("select count(*) from property_ownerships"), "no ownership row");
	}

	private int countRows(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

	/** Every field valid, like PropertyCreateServiceOwnerTest: STUDIO, 1 bedroom, 1 bathroom, 35 m2. */
	private static PropertyCreateCommand command() {
		return new PropertyCreateCommand(
				PROPERTY_CODE, "Title", "Description",
				"1 Bach Dang", "Thach Thang", "Hai Chau", null, null, null, null,
				PropertyType.STUDIO, 1, 1, 35.0,
				new BigDecimal("5000000"), new BigDecimal("5000000"), 6,
				OWNER_ID, ACTOR_ID, ACTOR_USERNAME,
				List.of(), List.of(), List.of());
	}

	/**
	 * Replaces the user directory: the owner is eligible at the check and refused at the grant, as if a role changed
	 * in between. Hibernate writes nothing before the commit, so the grant flushes first: the property, its unit and
	 * its ownership then really reach PostgreSQL, and the rollback has rows to undo.
	 */
	@TestConfiguration(proxyBeanMethods = false)
	static class RacingOwnerDirectory {

		@Bean
		@Primary
		UserDirectoryPort racingOwnerDirectory(EntityManager entityManager) {
			return new UserDirectoryPort() {
				@Override
				public boolean isEligibleOwner(String userId) {
					return true;
				}

				@Override
				public boolean ensureOwnerRole(String ownerUserId, String actorUserId) {
					entityManager.flush();
					return false;
				}
			};
		}
	}
}
