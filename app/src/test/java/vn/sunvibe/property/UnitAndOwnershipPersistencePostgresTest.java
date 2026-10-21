package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.property.core.domain.model.aggregate.Property;
import vn.sunvibe.property.core.domain.model.enums.PropertyType;
import vn.sunvibe.property.core.domain.model.ownership.OwnershipRelationType;
import vn.sunvibe.property.core.domain.model.ownership.OwnershipVerificationState;
import vn.sunvibe.property.core.domain.model.ownership.PropertyOwnership;
import vn.sunvibe.property.core.domain.model.ownership.PropertyOwnershipId;
import vn.sunvibe.property.core.domain.model.unit.Unit;
import vn.sunvibe.property.core.domain.model.unit.UnitAvailabilityState;
import vn.sunvibe.property.core.domain.model.unit.UnitId;
import vn.sunvibe.property.core.domain.model.unit.UnitType;
import vn.sunvibe.property.core.domain.model.vo.Location;
import vn.sunvibe.property.core.domain.model.vo.Money;
import vn.sunvibe.property.core.domain.model.vo.PropertyCode;
import vn.sunvibe.property.core.domain.repository.PropertyDomainRepository;
import vn.sunvibe.property.core.domain.repository.PropertyOwnershipDomainRepository;
import vn.sunvibe.property.core.domain.repository.UnitDomainRepository;
import vn.sunvibe.property.core.infrastructure.persistence.mapper.manual.PropertyOwnershipEntityMapper;
import vn.sunvibe.property.core.infrastructure.persistence.mapper.manual.UnitEntityMapper;
import vn.sunvibe.property.core.infrastructure.persistence.repository.PropertyOwnershipJpaRepository;
import vn.sunvibe.property.core.infrastructure.persistence.repository.UnitJpaRepository;

/**
 * Persistence of the unit and ownership aggregates on a real PostgreSQL 17 (property-units spec section 5, W8-10
 * columns, W8-14): a property, a unit and an ownership are saved through their domain repositories and read back with
 * plain SQL, so a column that is not mapped, or mapped to the wrong name, fails here. The schema comes from Flyway only
 * and Hibernate validates it; the annotations are those of {@link vn.sunvibe.FlywayPostgresMigrationTest}, so both
 * share one context. Needs Docker, like that test.
 * <p>
 * Every test uses its own property code: the database lives as long as the shared context.
 * <p>
 * The first three tests are the write side (domain repositories, plain SQL). The last three read back: the property
 * through its domain repository, the unit and the ownership through the JPA repository and the mapper, because their
 * adapters only save. The unit and the ownership use values that differ from each other (2 bedrooms,
 * 1 bathroom, CO_OWNER, ...), so that a swapped or hard-coded field fails. The stored row of the unit is also read
 * with plain SQL (bedrooms, bathrooms, created_at, updated_at), so a swapped {@code @Column} name in the entity fails
 * even when the round trip through the entity would still agree with itself.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
// The container's connection details replace the H2 datasource of application-test.yml
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class UnitAndOwnershipPersistencePostgresTest {

	private static final String OWNER_ID = "01OWNER0000000000000000000";
	private static final String CREATED_BY = "0900000001";
	/** Whole microseconds: PostgreSQL keeps no more, so a stored instant can be compared with the one saved. */
	private static final Instant CREATED_AT = Instant.parse("2026-10-16T08:00:00.123456Z");
	private static final Instant UPDATED_AT = Instant.parse("2026-10-17T09:30:00.654321Z");

	@Autowired
	private PropertyDomainRepository propertyRepository;

	@Autowired
	private UnitDomainRepository unitRepository;

	@Autowired
	private PropertyOwnershipDomainRepository ownershipRepository;

	@Autowired
	private UnitJpaRepository unitJpaRepository;

	@Autowired
	private UnitEntityMapper unitMapper;

	@Autowired
	private PropertyOwnershipJpaRepository ownershipJpaRepository;

	@Autowired
	private PropertyOwnershipEntityMapper ownershipMapper;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private JdbcTemplate jdbc;

	/** property-units BR-14: a new property starts at version 0. */
	@Test
	void save_newProperty_storesVersionZero() {
		Property property = saveProperty("WT-PER-001");

		assertEquals(0L, jdbc.queryForObject(
				"SELECT version FROM properties WHERE id = ?", Long.class, property.getId().getValue()));
	}

	/**
	 * property-units BR-4, BR-5, BR-8, BR-14: the canonical unit copies its property and starts AVAILABLE at version 0.
	 */
	@Test
	void save_canonicalUnit_writesEveryColumn() {
		Property property = saveProperty("WT-PER-002");
		Unit unit = Unit.createCanonical(property);

		unitRepository.save(unit);

		Map<String, Object> row = jdbc.queryForMap("SELECT * FROM units WHERE id = ?", unit.getId().getValue());
		assertEquals(property.getId().getValue(), row.get("property_id"));
		assertEquals("WT-PER-002", row.get("unit_code"));
		assertEquals("STUDIO", row.get("unit_type"));
		assertEquals(1, row.get("bedrooms"));
		assertEquals(1, row.get("bathrooms"));
		assertEquals(35.5, row.get("area_sqm"));
		assertEquals("AVAILABLE", row.get("availability_state"));
		assertEquals(0L, row.get("version"));
		assertNotNull(row.get("created_at"));
		assertNotNull(row.get("updated_at"));
	}

	/**
	 * property-units BR-10: the ownership property create writes is OWNER, UNVERIFIED, valid from the given day, with
	 * no end.
	 */
	@Test
	void save_unverifiedOwnership_writesEveryColumn() {
		Property property = saveProperty("WT-PER-003");
		PropertyOwnership ownership = PropertyOwnership.createUnverifiedOwner(
				property.getId(), OWNER_ID, LocalDate.of(2026, 10, 16));

		ownershipRepository.save(ownership);

		Map<String, Object> row = jdbc.queryForMap(
				"SELECT * FROM property_ownerships WHERE id = ?", ownership.getId().getValue());
		assertEquals(property.getId().getValue(), row.get("property_id"));
		assertEquals(OWNER_ID, row.get("user_id"));
		assertEquals("OWNER", row.get("relation_type"));
		assertEquals("UNVERIFIED", row.get("verification_state"));
		assertEquals(LocalDate.of(2026, 10, 16), jdbc.queryForObject(
				"SELECT valid_from FROM property_ownerships WHERE id = ?", LocalDate.class, ownership.getId().getValue()));
		assertNull(row.get("valid_to"));
		assertNotNull(row.get("created_at"));
	}

	/**
	 * property-units BR-14: a property read back carries the stored version, the lock that a later update must pass
	 * back.
	 */
	@Test
	void findById_savedProperty_loadsVersionZero() {
		Property saved = saveProperty("WT-PER-004");

		// A transaction of its own: the property mapper reads the lazy collections of the entity
		Property loaded = new TransactionTemplate(transactionManager)
				.execute(status -> propertyRepository.findById(saved.getId()).orElseThrow());

		assertEquals(0L, loaded.getVersion());
	}

	/** property-units BR-14: a saved unit carries its version, so saving it again is an update, not a second insert. */
	@Test
	void save_canonicalUnitTwice_updatesTheRowInsteadOfInsertingIt() {
		Property property = saveProperty("WT-PER-007");
		Unit unit = Unit.createCanonical(property);

		unitRepository.save(unit);
		assertEquals(0L, unit.getVersion());
		unitRepository.save(unit);

		assertEquals(1, jdbc.queryForObject(
				"SELECT count(*) FROM units WHERE property_id = ?", Integer.class, property.getId().getValue()));
		// Java keeps nanoseconds and PostgreSQL microseconds, so the merge sees new timestamps and updates the row
		assertEquals(jdbc.queryForObject("SELECT version FROM units WHERE id = ?", Long.class, unit.getId().getValue()),
				unit.getVersion(), "the unit carries the row's version");
	}

	@Test
	void toDomain_storedUnit_rebuildsTheSavedUnit() {
		Property property = saveProperty("WT-PER-005");
		Unit saved = Unit.load(UnitId.generate(), property.getId(), "WT-PER-005-B", UnitType.TWO_BEDROOM, 2, 1, 55.5,
				UnitAvailabilityState.MAINTENANCE, CREATED_AT, UPDATED_AT, null);
		unitRepository.save(saved);

		Map<String, Object> row = jdbc.queryForMap("SELECT * FROM units WHERE id = ?", saved.getId().getValue());
		assertEquals(2, row.get("bedrooms"));
		assertEquals(1, row.get("bathrooms"));
		assertEquals(CREATED_AT, toInstant(row.get("created_at")));
		assertEquals(UPDATED_AT, toInstant(row.get("updated_at")));

		Unit loaded = unitMapper.toDomain(unitJpaRepository.findById(saved.getId().getValue()).orElseThrow());

		assertEquals(saved.getId(), loaded.getId());
		assertEquals(property.getId(), loaded.getPropertyId());
		assertEquals("WT-PER-005-B", loaded.getUnitCode());
		assertEquals(UnitType.TWO_BEDROOM, loaded.getUnitType());
		assertEquals(2, loaded.getBedrooms());
		assertEquals(1, loaded.getBathrooms());
		assertEquals(55.5, loaded.getAreaSqm());
		assertEquals(UnitAvailabilityState.MAINTENANCE, loaded.getAvailabilityState());
		assertEquals(CREATED_AT, loaded.getCreatedAt());
		assertEquals(UPDATED_AT, loaded.getUpdatedAt());
		assertEquals(0L, loaded.getVersion());
	}

	@Test
	void toDomain_storedOwnership_rebuildsTheSavedOwnership() {
		Property property = saveProperty("WT-PER-006");
		PropertyOwnership saved = PropertyOwnership.load(PropertyOwnershipId.generate(), property.getId(), OWNER_ID,
				OwnershipRelationType.CO_OWNER, OwnershipVerificationState.VERIFIED, LocalDate.of(2026, 1, 5),
				LocalDate.of(2026, 12, 31), CREATED_AT);
		ownershipRepository.save(saved);

		PropertyOwnership loaded = ownershipMapper.toDomain(
				ownershipJpaRepository.findById(saved.getId().getValue()).orElseThrow());

		assertEquals(saved.getId(), loaded.getId());
		assertEquals(property.getId(), loaded.getPropertyId());
		assertEquals(OWNER_ID, loaded.getUserId());
		assertEquals(OwnershipRelationType.CO_OWNER, loaded.getRelationType());
		assertEquals(OwnershipVerificationState.VERIFIED, loaded.getVerificationState());
		assertEquals(LocalDate.of(2026, 1, 5), loaded.getValidFrom());
		assertEquals(LocalDate.of(2026, 12, 31), loaded.getValidTo());
		assertEquals(CREATED_AT, loaded.getCreatedAt());
	}

	/** A TIMESTAMPTZ column comes back from the JDBC driver as a Timestamp or an OffsetDateTime. */
	private static Instant toInstant(Object value) {
		if (value instanceof Timestamp timestamp) {
			return timestamp.toInstant();
		}
		return ((OffsetDateTime) value).toInstant();
	}

	/** A property as property create stores it: STUDIO, 1 bedroom, 1 bathroom, 35.5 m2. */
	private Property saveProperty(String code) {
		Property property = Property.create(PropertyCode.of(code), "W8 persistence", "Studio near the beach",
				Location.of("1 Bach Dang", "Thach Thang", "Hai Chau", null), PropertyType.STUDIO, 1, 1, 35.5,
				Money.vnd(5_000_000), Money.vnd(5_000_000), 6, OWNER_ID, CREATED_BY);
		propertyRepository.save(property);
		return property;
	}
}
