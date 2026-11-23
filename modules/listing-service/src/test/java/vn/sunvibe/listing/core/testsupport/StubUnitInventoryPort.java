package vn.sunvibe.listing.core.testsupport;

import vn.sunvibe.listing.core.application.shared.external.property.UnitAddressFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitAvailabilityFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitInventoryPort;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** property's unit reads for the service tests: availability and addresses set per unit, each call recorded by method. */
public class StubUnitInventoryPort implements UnitInventoryPort {

    private final Map<String, UnitAvailabilityFacts> availabilities = new HashMap<>();
    private final Map<String, UnitAddressFacts> addresses = new HashMap<>();
    private final List<String> calls = new ArrayList<>();

    public void availability(UnitAvailabilityFacts facts) {
        availabilities.put(facts.unitId(), facts);
    }

    public void address(UnitAddressFacts facts) {
        addresses.put(facts.unitId(), facts);
    }

    public List<String> calls() {
        return List.copyOf(calls);
    }

    @Override
    public List<UnitAvailabilityFacts> availability(Collection<String> unitIds) {
        calls.add("availability " + unitIds);
        if (unitIds.size() > MAX_UNITS) {
            throw new IllegalArgumentException("At most 250 units a call.");
        }
        return unitIds.stream().map(availabilities::get).filter(Objects::nonNull).toList();
    }

    @Override
    public Optional<UnitAddressFacts> address(String unitId) {
        calls.add("address " + unitId);
        return Optional.ofNullable(addresses.get(unitId));
    }
}
