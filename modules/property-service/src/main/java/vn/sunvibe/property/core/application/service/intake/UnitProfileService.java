package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.property.core.application.usecase.intake.UnitProfileUseCase;
import vn.sunvibe.property.core.application.usecase.intake.UnitProfileView;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository;

import java.util.Collection;
import java.util.List;

/**
 * Use Case Implementation - the profile of units (listing-publish E06-S2 spec E06S2-D12). One statement over the units,
 * their properties and the ward catalog. No viewer: the consumer has already checked its caller. Nothing is written.
 */
@Service
@RequiredArgsConstructor
public class UnitProfileService implements UnitProfileUseCase {

    private final InventoryReadRepository inventory;

    @Override
    @Transactional(readOnly = true)
    public List<UnitProfileView> profiles(Collection<String> unitIds) {
        if (unitIds.size() > MAX_UNITS) {
            throw new IllegalArgumentException("At most " + MAX_UNITS + " units are read in one call.");
        }
        if (unitIds.isEmpty()) {
            return List.of();
        }
        return inventory.unitProfiles(unitIds).stream()
                .map(row -> new UnitProfileView(row.unitId(), row.assetTypeOrNull(), row.provinceCodeOrNull(),
                        row.provinceNameOrNull(), row.wardCodeOrNull(), row.wardNameOrNull(), row.bedrooms(),
                        row.bathrooms(), row.areaSqm()))
                .toList();
    }
}
