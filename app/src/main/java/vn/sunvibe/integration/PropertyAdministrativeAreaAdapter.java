package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.application.shared.external.area.AdministrativeAreaPort;
import vn.sunvibe.acquisition.core.application.shared.external.area.AdministrativeWard;
import vn.sunvibe.property.core.application.usecase.area.AdministrativeAreaQueryUseCase;
import vn.sunvibe.property.core.application.usecase.area.WardView;
import vn.sunvibe.shared.exception.ResourceNotFoundException;

import java.util.List;
import java.util.Optional;

/**
 * Cross-module composition: answers acquisition-service's catalog questions with property-service's published
 * {@link AdministrativeAreaQueryUseCase}, so neither module depends on the other. Lives in app, which depends on
 * every module.
 */
@Component
@RequiredArgsConstructor
public class PropertyAdministrativeAreaAdapter implements AdministrativeAreaPort {

    private final AdministrativeAreaQueryUseCase administrativeAreaQueryUseCase;

    @Override
    public Optional<AdministrativeWard> findWard(String wardCode) {
        return administrativeAreaQueryUseCase.findWard(wardCode).map(PropertyAdministrativeAreaAdapter::toWard);
    }

    @Override
    public Optional<List<AdministrativeWard>> findWards(String provinceCode) {
        try {
            return Optional.of(administrativeAreaQueryUseCase.findWards(provinceCode).stream()
                    .map(PropertyAdministrativeAreaAdapter::toWard)
                    .toList());
        } catch (ResourceNotFoundException unknownProvince) {
            return Optional.empty();
        }
    }

    private static AdministrativeWard toWard(WardView ward) {
        return new AdministrativeWard(ward.code(), ward.name(), ward.type(), ward.provinceCode(),
                ward.provinceName());
    }
}
