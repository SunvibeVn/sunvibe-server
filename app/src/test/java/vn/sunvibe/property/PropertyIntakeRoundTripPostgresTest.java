package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import vn.sunvibe.property.core.domain.exception.UnitCodeTakenException;
import vn.sunvibe.property.core.domain.model.aggregate.IntakeProperty;
import vn.sunvibe.property.core.domain.model.aggregate.Property;
import vn.sunvibe.property.core.domain.model.enums.AssetType;
import vn.sunvibe.property.core.domain.model.enums.RentalLayout;
import vn.sunvibe.property.core.domain.model.unit.Unit;
import vn.sunvibe.property.core.domain.model.unit.UnitAvailabilityState;
import vn.sunvibe.property.core.domain.model.vo.IntakePropertyCode;
import vn.sunvibe.property.core.domain.model.vo.Location;
import vn.sunvibe.property.core.domain.repository.PropertyDomainRepository;
import vn.sunvibe.property.core.domain.repository.UnitDomainRepository;
import vn.sunvibe.property.core.infrastructure.persistence.mapper.manual.UnitEntityMapper;
import vn.sunvibe.property.core.infrastructure.persistence.repository.UnitJpaRepository;

/**
 * An intake property on a real PostgreSQL (property-create intake spec S-15, section 3): the NULL legacy columns read back
 * through {@code load} and the mappers, and a loaded row saved again keeps every new column. The annotations are those of
 * {@link vn.sunvibe.FlywayPostgresMigrationTest}, so both share one context. Needs Docker, like that test.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class PropertyIntakeRoundTripPostgresTest {

	private static final String OWNER = "01OWNER0000000000000000000";
	private static final String ACTOR = "01ACTOR0000000000000000000";

	@Autowired
	private PropertyDomainRepository properties;

	@Autowired
	private UnitDomainRepository units;

	@Autowired
	private UnitJpaRepository unitJpa;

	@Autowired
	private UnitEntityMapper unitMapper;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void roundTrip_intakeRow_keepsEveryColumn() {
		Property saved = saved(71, "01RTINTAKE0000000000000071");

		Property loaded = inTransaction(() -> properties.findById(saved.getId()).orElseThrow());

		assertEquals(saved.getPropertyCode(), loaded.getPropertyCode());
		assertEquals(AssetType.WHOLE_BUILDING, loaded.getAssetType());
		assertEquals(RentalLayout.PARTS, loaded.getRentalLayout());
		assertEquals("01RTINTAKE0000000000000071", loaded.getIntakeId());
		assertEquals("20242", loaded.getWardCode());
		assertEquals("1 bach dang", loaded.getAddressNorm());
		assertEquals("Hải Châu", loaded.getLocation().getWard());
		assertNull(loaded.getLocation().getDistrict());
		assertNull(loaded.getStatus());
		assertNull(loaded.getType());
		assertNull(loaded.getBedrooms());
		assertNull(loaded.getBathrooms());
		assertNull(loaded.getSquareMeters());
		assertNull(loaded.getMonthlyPrice());
		assertNull(loaded.getDepositPrice());
		assertNull(loaded.getMinRentMonths());
		Map<String, Object> row = jdbc.queryForMap("SELECT * FROM properties WHERE id = ?", saved.getId().getValue());
		assertNull(row.get("status"));
		assertNull(row.get("monthly_price"));
		assertNull(row.get("district"));
		assertEquals("WHOLE_BUILDING", row.get("asset_type"));
		assertEquals("PARTS", row.get("rental_layout"));
	}

	/** S-15, persistence rule: a merge of a loaded row must not clear a column the mapper left out. */
	@Test
	void roundTrip_savedAgainAfterLoad_keepsNewColumns() {
		Property saved = saved(72, "01RTINTAKE0000000000000072");

		inTransaction(() -> {
			Property loaded = properties.findById(saved.getId()).orElseThrow();
			loaded.updateBasicInfo("Renamed", null, null, null, null, ACTOR);
			properties.save(loaded);
			return loaded;
		});

		Map<String, Object> row = jdbc.queryForMap("SELECT * FROM properties WHERE id = ?", saved.getId().getValue());
		assertEquals("Renamed", row.get("title"));
		assertEquals("WHOLE_BUILDING", row.get("asset_type"));
		assertEquals("PARTS", row.get("rental_layout"));
		assertEquals("01RTINTAKE0000000000000072", row.get("intake_id"));
		assertEquals("20242", row.get("ward_code"));
		assertEquals("1 bach dang", row.get("address_norm"));
		assertNull(row.get("monthly_price"));
	}

	@Test
	void roundTrip_intakeUnit_keepsLabelAndNullType() {
		Property property = saved(73, "01RTINTAKE0000000000000073");
		Unit unit = Unit.createFromIntake(property.getId(), "P101", "P101 · Tầng 1", 1, 1, 25.5,
				UnitAvailabilityState.BLOCKED);

		inTransaction(() -> {
			units.save(unit);
			return unit;
		});

		Unit loaded = unitMapper.toDomain(unitJpa.findById(unit.getId().getValue()).orElseThrow());
		assertEquals("P101 · Tầng 1", loaded.getLocationLabel());
		assertNull(loaded.getUnitType());
		assertEquals(UnitAvailabilityState.BLOCKED, loaded.getAvailabilityState());
		assertEquals(0L, unit.getVersion());
	}

	/** spec 5.1: the adapter flushes and translates the index, so a collision never reaches the client as a 500. */
	@Test
	void save_unitCodeDifferingByCaseOrSpace_translated() {
		Property property = saved(74, "01RTINTAKE0000000000000074");
		inTransaction(() -> {
			units.save(Unit.createFromIntake(property.getId(), "P101", "P101 · Tầng 1", 1, 1, 25.5,
					UnitAvailabilityState.AVAILABLE));
			return null;
		});

		assertThrows(UnitCodeTakenException.class, () -> inTransaction(() -> {
			units.save(Unit.createFromIntake(property.getId(), " p101 ", "p101 · Tầng 1", 1, 1, 25.5,
					UnitAvailabilityState.AVAILABLE));
			return null;
		}));
	}

	private Property saved(int number, String intakeId) {
		Property property = Property.createFromIntake(new IntakeProperty(
				IntakePropertyCode.of(AssetType.WHOLE_BUILDING, 900_000 + number), "Whole building, Hải Châu",
				Location.of("1 Bạch Đằng", "Hải Châu", null, "Đà Nẵng", null, 16.07, 108.22), AssetType.WHOLE_BUILDING,
				RentalLayout.PARTS, intakeId, "20242", "1 bach dang", OWNER, ACTOR));
		return inTransaction(() -> {
			properties.save(property);
			return property;
		});
	}

	private <T> T inTransaction(java.util.function.Supplier<T> body) {
		return new TransactionTemplate(transactionManager).execute(status -> body.get());
	}
}
