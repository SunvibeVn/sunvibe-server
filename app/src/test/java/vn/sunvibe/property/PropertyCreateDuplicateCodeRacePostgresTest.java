package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;

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
import vn.sunvibe.property.core.domain.exception.PropertyAlreadyExistsException;
import vn.sunvibe.property.core.domain.model.aggregate.Property;
import vn.sunvibe.property.core.domain.model.enums.PropertyType;
import vn.sunvibe.property.core.domain.model.vo.PropertyCode;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;
import vn.sunvibe.property.core.domain.repository.PropertyDomainRepository;
import vn.sunvibe.property.core.infrastructure.persistence.adapter.PropertyRepositoryAdapter;

/**
 * Two creates with the same new property code at the same time (D-AI-2 of the 2026-10-20 property-service refactor
 * plan): both pass the duplicate check, and the unique constraint refuses the second insert. The second create must
 * end like an ordinary duplicate (PropertyAlreadyExistsException, 409 at the API) and store no property, unit or
 * ownership. The race is reproduced by a repository whose duplicate check never sees the first property; every other
 * call goes to the real adapter. The user directory accepts the owner, so no account is needed. Needs Docker.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
// The container's connection details replace the H2 datasource of application-test.yml
@ActiveProfiles("test")
@Import({ PostgresContainerConfig.class, PropertyCreateDuplicateCodeRacePostgresTest.RacingCreates.class })
class PropertyCreateDuplicateCodeRacePostgresTest {

	private static final String PROPERTY_CODE = "WT-RCE-001";
	private static final String OWNER_ID = "01OWNER0000000000000000000";
	private static final String ACTOR_ID = "01ACTOR0000000000000000000";
	private static final String ACTOR_USERNAME = "0912345678";

	@Autowired
	private PropertyCreateUseCase propertyCreateUseCase;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void execute_codeTakenAfterTheCheck_throwsDuplicateAndStoresNothingMore() {
		propertyCreateUseCase.execute(command());
		int ownershipsBefore = countRows("select count(*) from property_ownerships");

		assertThrows(PropertyAlreadyExistsException.class, () -> propertyCreateUseCase.execute(command()));

		assertEquals(1, countRows("select count(*) from properties where property_code = ?", PROPERTY_CODE));
		assertEquals(1, countRows("select count(*) from units where unit_code = ?", PROPERTY_CODE));
		assertEquals(ownershipsBefore, countRows("select count(*) from property_ownerships"));
	}

	private int countRows(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

	private static PropertyCreateCommand command() {
		return new PropertyCreateCommand(
				PROPERTY_CODE, "Title", "Description",
				"1 Bach Dang", "Thach Thang", "Hai Chau", null, null, null, null,
				PropertyType.STUDIO, 1, 1, 35.0,
				new BigDecimal("5000000"), new BigDecimal("5000000"), 6,
				OWNER_ID, ACTOR_ID, ACTOR_USERNAME,
				List.of(), List.of(), List.of());
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class RacingCreates {

		/** The real repository, except that the duplicate check never sees a stored code. */
		@Bean
		@Primary
		PropertyDomainRepository raceBlindPropertyRepository(PropertyRepositoryAdapter adapter) {
			return new PropertyDomainRepository() {
				@Override
				public void save(Property property) {
					adapter.save(property);
				}

				@Override
				public java.util.Optional<Property> findById(PropertyId id) {
					return adapter.findById(id);
				}

				@Override
				public boolean existsByCode(PropertyCode code) {
					return false;
				}
			};
		}

		@Bean
		@Primary
		UserDirectoryPort acceptingOwnerDirectory() {
			return new UserDirectoryPort() {
				@Override
				public boolean isEligibleOwner(String userId) {
					return true;
				}

				@Override
				public boolean ensureOwnerRole(String ownerUserId, String actorUserId) {
					return true;
				}
			};
		}
	}
}
