package vn.sunvibe.brokerage.core.testsupport;

import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeOriginFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeOriginPort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Intake origins by intake id. Each read is recorded into the shared call list as {@code "origin <intake>"}. */
public final class InMemoryIntakeOriginPort implements IntakeOriginPort {

    private final List<String> calls;
    private final Map<String, IntakeOriginFacts> origins = new HashMap<>();

    public InMemoryIntakeOriginPort(List<String> calls) {
        this.calls = calls;
    }

    public InMemoryIntakeOriginPort origin(String intakeId, IntakeOriginFacts facts) {
        origins.put(intakeId, facts);
        return this;
    }

    @Override
    public Optional<IntakeOriginFacts> originOf(String intakeId) {
        calls.add("origin " + intakeId);
        return Optional.ofNullable(origins.get(intakeId));
    }
}
