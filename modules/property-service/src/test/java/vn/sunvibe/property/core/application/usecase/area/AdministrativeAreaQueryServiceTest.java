package vn.sunvibe.property.core.application.usecase.area;

import org.junit.jupiter.api.Test;
import vn.sunvibe.property.core.application.usecase.area.impl.AdministrativeAreaQueryService;
import vn.sunvibe.property.core.domain.model.area.Province;
import vn.sunvibe.property.core.domain.model.area.Ward;
import vn.sunvibe.property.core.domain.model.area.WardType;
import vn.sunvibe.property.core.testsupport.InMemoryAdministrativeAreaDomainRepository;
import vn.sunvibe.shared.exception.ResourceNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Catalog read side (acquisition-owner-intake brief BR-6, spec 5.2; W9-14). */
class AdministrativeAreaQueryServiceTest {

    private final InMemoryAdministrativeAreaDomainRepository repository = new InMemoryAdministrativeAreaDomainRepository()
            .addProvince(Province.load("48", "Đà Nẵng"))
            .addProvince(Province.load("01", "Hà Nội"))
            .addWard(ward("20285", "Ngũ Hành Sơn", WardType.WARD))
            .addWard(ward("20320", "Hòa Vang", WardType.COMMUNE))
            .addWard(ward("20197", "Liên Chiểu", WardType.WARD))
            .addWard(ward("20200", "Hòa Khánh", WardType.WARD))
            .addWard(ward("20333", "Hoàng Sa", WardType.SPECIAL_ZONE))
            .addWard(ward("20242", "Hải Châu", WardType.WARD))
            .addWard(ward("29999", "Đông Hà", WardType.WARD))
            .addWard(ward("29998", "Dương Kinh", WardType.WARD))
            .addWard(Ward.load("10001", "01", "Ba Đình", WardType.WARD));
    private final AdministrativeAreaQueryUseCase useCase = new AdministrativeAreaQueryService(repository);

    @Test
    void findProvinces_catalogHasTwo_returnsThemSortedByVietnameseName() {
        assertEquals(List.of(new ProvinceView("48", "Đà Nẵng"), new ProvinceView("01", "Hà Nội")),
                useCase.findProvinces());
    }

    @Test
    void findWards_knownProvince_returnsOnlyItsWardsSortedByVietnameseCollation() {
        List<String> names = useCase.findWards("48").stream().map(WardView::name).toList();

        // Dương before Đông before Hải before Hòa/Hoàng before Liên before Ngũ: a plain code-point sort would put
        // Đông last. The Java collator ignores the space, so "Hòa Vang" compares as "HòaVang" and comes after
        // "Hoàng Sa" ("HoàngSa"); the spec fixes this collator, so that order is the expected one.
        assertEquals(List.of("Dương Kinh", "Đông Hà", "Hải Châu", "Hòa Khánh", "Hoàng Sa", "Hòa Vang",
                "Liên Chiểu", "Ngũ Hành Sơn"), names);
    }

    @Test
    void findWards_knownProvince_carriesCodeTypeAndProvince() {
        WardView hoangSa = useCase.findWards("48").stream()
                .filter(ward -> ward.code().equals("20333")).findFirst().orElseThrow();

        assertEquals(new WardView("20333", "Hoàng Sa", "SPECIAL_ZONE", "48", "Đà Nẵng"), hoangSa);
    }

    @Test
    void findWards_unknownProvince_throwsResourceNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> useCase.findWards("99"));
    }

    @Test
    void findWard_knownCode_returnsTheViewWithItsProvince() {
        assertEquals(Optional.of(new WardView("20285", "Ngũ Hành Sơn", "WARD", "48", "Đà Nẵng")),
                useCase.findWard("20285"));
    }

    @Test
    void findWard_unknownCode_returnsEmpty() {
        assertEquals(Optional.empty(), useCase.findWard("00000"));
    }

    private static Ward ward(String code, String name, WardType type) {
        return Ward.load(code, "48", name, type);
    }
}
