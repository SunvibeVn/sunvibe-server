package vn.sunvibe.acquisition.core.testsupport;

import vn.sunvibe.acquisition.core.application.shared.external.area.AdministrativeAreaPort;
import vn.sunvibe.acquisition.core.application.shared.external.area.AdministrativeWard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Hand-written fake of the catalog port: a province is known when at least one ward of it was added. */
public class InMemoryAdministrativeAreaPort implements AdministrativeAreaPort {

    private final Map<String, AdministrativeWard> wards = new LinkedHashMap<>();

    public InMemoryAdministrativeAreaPort with(AdministrativeWard ward) {
        wards.put(ward.code(), ward);
        return this;
    }

    @Override
    public Optional<AdministrativeWard> findWard(String wardCode) {
        return Optional.ofNullable(wards.get(wardCode));
    }

    @Override
    public Optional<List<AdministrativeWard>> findWards(String provinceCode) {
        List<AdministrativeWard> inProvince = new ArrayList<>(wards.values().stream()
                .filter(ward -> ward.provinceCode().equals(provinceCode))
                .toList());
        return inProvince.isEmpty() ? Optional.empty() : Optional.of(inProvince);
    }
}
