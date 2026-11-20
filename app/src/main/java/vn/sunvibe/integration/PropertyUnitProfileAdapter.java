package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.listing.core.application.shared.external.property.UnitProfileFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitProfilePort;
import vn.sunvibe.property.core.application.usecase.intake.UnitProfileUseCase;

import java.util.List;
import java.util.Optional;

/**
 * Cross-module composition (listing-publish E06-S2 spec E06S2-D12, ADR-009): answers listing-service's unit profile
 * question with property-service's published, viewer-less {@link UnitProfileUseCase}, after listing checked its caller.
 * One call of one id; it only copies the values, and joins the caller's transaction.
 */
@Component
@RequiredArgsConstructor
public class PropertyUnitProfileAdapter implements UnitProfilePort {

    private final UnitProfileUseCase profiles;

    @Override
    public Optional<UnitProfileFacts> profile(String unitId) {
        return profiles.profiles(List.of(unitId)).stream()
                .filter(view -> unitId.equals(view.unitId()))
                .findFirst()
                .map(view -> new UnitProfileFacts(view.unitId(), view.assetTypeOrNull(), view.provinceCodeOrNull(),
                        view.provinceNameOrNull(), view.wardCodeOrNull(), view.wardNameOrNull(), view.bedrooms(),
                        view.bathrooms(), view.areaSqm()));
    }
}
