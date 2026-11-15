package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.property.core.application.usecase.intake.PropertyOwnershipUseCase;
import vn.sunvibe.property.core.application.usecase.intake.PropertyOwnershipView;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Use Case Implementation - read 1b: the properties, then the ownership rows valid today of those found, two statements
 * for the whole set, with one reading of the clock (E05-S1 S1-D4, as {@link CompletedIntakeService#latest} reads them).
 */
@Service
@RequiredArgsConstructor
public class PropertyOwnershipService implements PropertyOwnershipUseCase {

    private final InventoryReadRepository inventory;

    @Override
    @Transactional(readOnly = true)
    public List<PropertyOwnershipView> ownershipOf(Collection<String> propertyIds) {
        List<String> ids = IntakeCommandSupport.boundedIds(propertyIds, MAX_PROPERTIES, "At most 250 properties a call.");
        if (ids.isEmpty()) {
            return List.of();
        }
        Instant now = IntakeCommandSupport.now();
        List<InventoryReadRepository.PropertyFacts> found = inventory.properties(ids);
        Map<String, List<InventoryReadRepository.HoldingFacts>> holdings = inventory.holdingsOf(
                found.stream().map(InventoryReadRepository.PropertyFacts::id).toList(), IntakeCommandSupport.today(now));
        return found.stream().map(property -> new PropertyOwnershipView(property.id(), property.ownerId(),
                holdings.getOrDefault(property.id(), List.of()).stream()
                        .map(holding -> new PropertyOwnershipView.Holding(holding.userId(), holding.relationType(),
                                holding.verificationState()))
                        .toList())).toList();
    }
}
