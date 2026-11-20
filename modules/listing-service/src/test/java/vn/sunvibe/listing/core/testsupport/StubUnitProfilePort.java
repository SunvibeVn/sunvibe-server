package vn.sunvibe.listing.core.testsupport;

import vn.sunvibe.listing.core.application.shared.external.property.UnitProfileFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitProfilePort;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Property's unit profile read for the service tests: a profile set per unit id, nothing by default, each call recorded. */
public class StubUnitProfilePort implements UnitProfilePort {

    private final Map<String, UnitProfileFacts> profiles = new HashMap<>();
    private final List<String> calls = new ArrayList<>();

    public void profile(UnitProfileFacts profile) {
        profiles.put(profile.unitId(), profile);
    }

    public List<String> calls() {
        return List.copyOf(calls);
    }

    @Override
    public Optional<UnitProfileFacts> profile(String unitId) {
        calls.add("profile " + unitId);
        return Optional.ofNullable(profiles.get(unitId));
    }
}
