package vn.sunvibe.brokerage.core.testsupport;

import vn.sunvibe.brokerage.core.application.shared.external.property.PropertyOwnerFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.UnitBasisFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.UnitBasisPort;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The units' bases and the properties' owners in maps; every call is recorded with its ids, and {@link #failWith} makes
 * the next calls throw, as a failing read of property-service does.
 */
public final class InMemoryUnitBasisPort implements UnitBasisPort {

    private final Map<String, UnitBasisFacts> units = new LinkedHashMap<>();
    private final Map<String, PropertyOwnerFacts> owners = new LinkedHashMap<>();
    private final List<String> calls = new ArrayList<>();
    private RuntimeException failure;

    public InMemoryUnitBasisPort unit(UnitBasisFacts facts) {
        units.put(facts.unitId(), facts);
        return this;
    }

    public InMemoryUnitBasisPort owner(PropertyOwnerFacts facts) {
        owners.put(facts.propertyId(), facts);
        return this;
    }

    /** Every call from now on throws this failure. */
    public InMemoryUnitBasisPort failWith(RuntimeException failure) {
        this.failure = failure;
        return this;
    }

    @Override
    public List<UnitBasisFacts> forUnits(Collection<String> unitIds) {
        calls.add("forUnits " + List.copyOf(unitIds));
        if (failure != null) {
            throw failure;
        }
        return unitIds.stream().distinct().map(units::get).filter(Objects::nonNull).toList();
    }

    @Override
    public List<PropertyOwnerFacts> ownersOf(Collection<String> propertyIds) {
        calls.add("ownersOf " + List.copyOf(propertyIds));
        if (failure != null) {
            throw failure;
        }
        return propertyIds.stream().distinct().map(owners::get).filter(Objects::nonNull).toList();
    }

    /** The calls made so far, as "forUnits [ids]" or "ownersOf [ids]", in order. */
    public List<String> calls() {
        return List.copyOf(calls);
    }
}
