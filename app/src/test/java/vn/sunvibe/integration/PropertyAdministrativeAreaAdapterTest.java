package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import vn.sunvibe.acquisition.core.application.shared.external.area.AdministrativeWard;
import vn.sunvibe.property.core.application.usecase.area.AdministrativeAreaQueryUseCase;
import vn.sunvibe.property.core.application.usecase.area.ProvinceView;
import vn.sunvibe.property.core.application.usecase.area.WardView;
import vn.sunvibe.property.core.domain.exception.ProvinceNotFoundException;

/**
 * The adapter maps property-service's catalog views to acquisition's own ward type and turns "province not found"
 * into an empty Optional (ADR-009, W9-20); the catalog itself is tested in property-service.
 */
class PropertyAdministrativeAreaAdapterTest {

	private static final WardView HAI_CHAU = new WardView("20194", "Hải Châu", "WARD", "48", "Đà Nẵng");
	private static final WardView HOANG_SA = new WardView("20332", "Hoàng Sa", "SPECIAL_ZONE", "48", "Đà Nẵng");
	private static final WardView HOA_VANG = new WardView("20340", "Hòa Vang", "COMMUNE", "48", "Đà Nẵng");

	private final PropertyAdministrativeAreaAdapter adapter = new PropertyAdministrativeAreaAdapter(
			new AdministrativeAreaQueryUseCase() {
				@Override
				public List<ProvinceView> findProvinces() {
					throw new UnsupportedOperationException();
				}

				@Override
				public List<WardView> findWards(String provinceCode) {
					if (!"48".equals(provinceCode)) {
						throw new ProvinceNotFoundException("Province was not found.");
					}
					return List.of(HAI_CHAU, HOANG_SA);
				}

				@Override
				public Optional<WardView> findWard(String wardCode) {
					return List.of(HAI_CHAU, HOANG_SA, HOA_VANG).stream()
							.filter(ward -> ward.code().equals(wardCode)).findFirst();
				}
			});

	@Test
	void findWard_knownWard_mapsEveryField() {
		AdministrativeWard ward = adapter.findWard("20194").orElseThrow();

		assertEquals(new AdministrativeWard("20194", "Hải Châu", "WARD", "48", "Đà Nẵng"), ward);
		assertFalse(ward.isSpecialZone());
	}

	@Test
	void findWard_unknownWard_isEmpty() {
		assertTrue(adapter.findWard("00000").isEmpty());
	}

	@Test
	void findWard_specialZoneAndCommune_keepTheTypeName() {
		assertEquals("SPECIAL_ZONE", adapter.findWard("20332").orElseThrow().type());
		assertTrue(adapter.findWard("20332").orElseThrow().isSpecialZone());
		assertEquals("COMMUNE", adapter.findWard("20340").orElseThrow().type());
	}

	@Test
	void findWards_knownProvince_mapsTheListInOrder() {
		List<AdministrativeWard> wards = adapter.findWards("48").orElseThrow();

		assertEquals(List.of("20194", "20332"), wards.stream().map(AdministrativeWard::code).toList());
		assertEquals("Đà Nẵng", wards.get(0).provinceName());
	}

	@Test
	void findWards_unknownProvince_isEmptyOptionalNotAnException() {
		assertEquals(Optional.empty(), adapter.findWards("99"));
	}
}
