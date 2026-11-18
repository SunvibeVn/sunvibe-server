package vn.sunvibe.brokerage.core.testsupport;

import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisPort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The intake basis of each lead; each read is recorded into the shared call list as {@code "basis <lead>"}. */
public final class InMemoryIntakeBasisPort implements IntakeBasisPort {

    private final List<String> calls;
    private final Map<String, IntakeBasisFacts> bases = new HashMap<>();

    public InMemoryIntakeBasisPort(List<String> calls) {
        this.calls = calls;
    }

    /** The lead's latest completed intake; a lead never given one has none. */
    public InMemoryIntakeBasisPort basis(String leadId, IntakeBasisFacts facts) {
        bases.put(leadId, facts);
        return this;
    }

    @Override
    public Optional<IntakeBasisFacts> latestBasis(String leadId) {
        calls.add("basis " + leadId);
        return Optional.ofNullable(bases.get(leadId));
    }
}
