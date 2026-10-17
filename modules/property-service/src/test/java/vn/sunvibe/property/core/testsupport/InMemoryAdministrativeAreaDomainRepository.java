package vn.sunvibe.property.core.testsupport;

import vn.sunvibe.property.core.domain.model.area.Province;
import vn.sunvibe.property.core.domain.model.area.Ward;
import vn.sunvibe.property.core.domain.repository.AdministrativeAreaDomainRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Keeps the catalog in lists, in the order the test added them (no sorting, like a table scan). */
public class InMemoryAdministrativeAreaDomainRepository implements AdministrativeAreaDomainRepository {

    private final List<Province> provinces = new ArrayList<>();
    private final List<Ward> wards = new ArrayList<>();

    public InMemoryAdministrativeAreaDomainRepository addProvince(Province province) {
        provinces.add(province);
        return this;
    }

    public InMemoryAdministrativeAreaDomainRepository addWard(Ward ward) {
        wards.add(ward);
        return this;
    }

    @Override
    public List<Province> findAllProvinces() {
        return List.copyOf(provinces);
    }

    @Override
    public Optional<Province> findProvince(String code) {
        return provinces.stream().filter(province -> province.getCode().equals(code)).findFirst();
    }

    @Override
    public List<Ward> findWardsByProvince(String provinceCode) {
        return wards.stream().filter(ward -> ward.getProvinceCode().equals(provinceCode)).toList();
    }

    @Override
    public Optional<Ward> findWard(String code) {
        return wards.stream().filter(ward -> ward.getCode().equals(code)).findFirst();
    }
}
