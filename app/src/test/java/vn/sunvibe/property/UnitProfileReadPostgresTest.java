package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import vn.sunvibe.property.core.application.usecase.intake.UnitProfileUseCase;
import vn.sunvibe.property.core.application.usecase.intake.UnitProfileView;

/**
 * The viewer-less unit profile read of listing-publish E06-S2 (spec E06S2-D12, D12 revised): per unit the property's
 * asset type, the ward and its province from the catalog only (a ward the catalog lacks and a legacy free-text ward give
 * no place), and the unit's rooms and area; an unknown id left out; at most 250 ids. The base and annotations of
 * {@link UnitLocationReadPostgresTest}, so both share one context; its rows are written through the base's
 * {@code property} and {@code unit} helpers, which the base's cleanup reaches. Needs Docker.
 */
@Import(StatementCounter.Config.class)
class UnitProfileReadPostgresTest extends IntakeApiTestBase {

	@Autowired
	private StatementCounter counter;

	@Autowired
	private UnitProfileUseCase profiles;

	@Override
	String phonePrefix() {
		return "0915";
	}

	@Test
	void profiles_catalogWard_wardAndProvinceFromCatalog() {
		String propertyId = property(sale.userId(), WARD, "Stored ward text", unique("Profile Street"), "APARTMENT", null);
		String unitId = unit(propertyId, "P201", "AVAILABLE");
		Map<String, Object> ward = jdbc.queryForMap("select w.name as ward_name, p.code as province_code,"
				+ " p.name as province_name from wards w join provinces p on p.code = w.province_code where w.code = ?", WARD);

		UnitProfileView profile = profiles.profiles(List.of(unitId)).getFirst();

		assertEquals(new UnitProfileView(unitId, "APARTMENT", (String) ward.get("province_code"),
				(String) ward.get("province_name"), WARD, (String) ward.get("ward_name"), 1, 1, 30.0), profile);
	}

	@Test
	void profiles_wardNotInCatalog_noPlace() {
		String unknownWardProperty = property(sale.userId(), WARD, "Phường Hải Châu", unique("Unknown Ward Street"),
				"HOUSE", null);
		jdbc.update("update properties set ward_code = '99999' where id = ?", unknownWardProperty);
		String unknownWardUnit = unit(unknownWardProperty, "U1", "AVAILABLE");
		String legacyProperty = property(sale.userId(), null, "Hải Châu", unique("Legacy Profile Street"), "HOUSE", null);
		String legacyUnit = unit(legacyProperty, "L1", "AVAILABLE");

		Map<String, UnitProfileView> found = byUnit(profiles.profiles(List.of(unknownWardUnit, legacyUnit)));

		assertEquals(new UnitProfileView(unknownWardUnit, "HOUSE", null, null, null, null, 1, 1, 30.0),
				found.get(unknownWardUnit));
		assertEquals(new UnitProfileView(legacyUnit, "HOUSE", null, null, null, null, 1, 1, 30.0), found.get(legacyUnit));
	}

	@Test
	void profiles_legacyAssetType_null() {
		String propertyId = property(sale.userId(), WARD, "Phường Hải Châu", unique("Legacy Type Street"), null, null);
		String unitId = unit(propertyId, "T1", "AVAILABLE");

		assertEquals(null, profiles.profiles(List.of(unitId)).getFirst().assetTypeOrNull());
	}

	@Test
	void profiles_unitFields_roomsAndArea() {
		String propertyId = property(sale.userId(), WARD, "Phường Hải Châu", unique("Rooms Street"), "VILLA", null);
		String unitId = unit(propertyId, "R1", "AVAILABLE");
		jdbc.update("update units set bedrooms = 3, bathrooms = 2, area_sqm = 45.5 where id = ?", unitId);

		UnitProfileView profile = profiles.profiles(List.of(unitId)).getFirst();

		assertEquals(3, profile.bedrooms());
		assertEquals(2, profile.bathrooms());
		assertEquals(45.5, profile.areaSqm());
		assertEquals("VILLA", profile.assetTypeOrNull());
	}

	@Test
	void profiles_unknownId_leftOut() {
		String propertyId = property(sale.userId(), WARD, "Phường Hải Châu", unique("Known Street"), "HOUSE", null);
		String unitId = unit(propertyId, "K1", "AVAILABLE");

		List<UnitProfileView> found = profiles.profiles(List.of(unitId, "01JUNKNOWNUNIT000000000000"));

		assertEquals(List.of(unitId), found.stream().map(UnitProfileView::unitId).toList());
		assertEquals(List.of(), profiles.profiles(List.of()));
	}

	@Test
	void profiles_aboveTwoHundredFifty_illegalArgument() {
		List<String> ids = new ArrayList<>();
		for (int number = 0; number < 251; number++) {
			ids.add(String.format("01JUNKNOWNUNIT%012d", number));
		}

		long statements = counter.count(() -> assertThrows(IllegalArgumentException.class, () -> profiles.profiles(ids)));

		assertEquals(0, statements);
		assertEquals(250, UnitProfileUseCase.MAX_UNITS);
	}

	private static Map<String, UnitProfileView> byUnit(List<UnitProfileView> views) {
		return views.stream().collect(Collectors.toMap(UnitProfileView::unitId, Function.identity()));
	}
}
