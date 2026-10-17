package vn.sunvibe.property.core.application.usecase.area.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.property.core.application.usecase.area.ProvinceView;
import vn.sunvibe.property.core.application.usecase.area.WardView;
import vn.sunvibe.property.core.application.usecase.area.AdministrativeAreaQueryUseCase;
import vn.sunvibe.property.core.domain.exception.ProvinceNotFoundException;
import vn.sunvibe.property.core.domain.model.area.Province;
import vn.sunvibe.property.core.domain.model.area.Ward;
import vn.sunvibe.property.core.domain.repository.AdministrativeAreaDomainRepository;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdministrativeAreaQueryService implements AdministrativeAreaQueryUseCase {

    private final AdministrativeAreaDomainRepository repository;

    @Override
    public List<ProvinceView> findProvinces() {
        return repository.findAllProvinces().stream()
                .sorted(byName(Province::getName))
                .map(province -> new ProvinceView(province.getCode(), province.getName()))
                .toList();
    }

    @Override
    public List<WardView> findWards(String provinceCode) {
        Province province = repository.findProvince(provinceCode)
                .orElseThrow(() -> new ProvinceNotFoundException("Province was not found."));
        return repository.findWardsByProvince(provinceCode).stream()
                .sorted(byName(Ward::getName))
                .map(ward -> view(ward, province))
                .toList();
    }

    @Override
    public Optional<WardView> findWard(String wardCode) {
        return repository.findWard(wardCode).flatMap(ward -> repository.findProvince(ward.getProvinceCode())
                .map(province -> view(ward, province)));
    }

    private static WardView view(Ward ward, Province province) {
        return new WardView(ward.getCode(), ward.getName(), ward.getType().name(), province.getCode(), province.getName());
    }

    /** A new collator per call: Collator instances are not thread-safe. */
    private static <T> Comparator<T> byName(Function<T, String> name) {
        Collator vietnamese = Collator.getInstance(Locale.forLanguageTag("vi"));
        return (a, b) -> vietnamese.compare(name.apply(a), name.apply(b));
    }
}
