package vn.sunvibe.brokerage.core.testsupport;

import vn.sunvibe.brokerage.core.application.shared.external.property.UnitLocationFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.UnitLocationPort;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Unit locations in a map by unit id; an unknown unit is left out, as property-service leaves it out. Calls recorded. */
public final class InMemoryUnitLocationPort implements UnitLocationPort {

    private final Map<String, UnitLocationFacts> locations = new LinkedHashMap<>();
    private final List<List<String>> calls = new ArrayList<>();

    public InMemoryUnitLocationPort location(UnitLocationFacts facts) {
        locations.put(facts.unitId(), facts);
        return this;
    }

    @Override
    public List<UnitLocationFacts> locate(Collection<String> unitIds) {
        calls.add(List.copyOf(unitIds));
        return unitIds.stream().map(locations::get).filter(Objects::nonNull).toList();
    }

    /** The unit ids of each call, in order. */
    public List<List<String>> calls() {
        return List.copyOf(calls);
    }
}
