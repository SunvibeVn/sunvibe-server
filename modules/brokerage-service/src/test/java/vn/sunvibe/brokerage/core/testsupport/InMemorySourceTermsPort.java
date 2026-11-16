package vn.sunvibe.brokerage.core.testsupport;

import vn.sunvibe.brokerage.core.application.shared.external.survey.SourceTermsFacts;
import vn.sunvibe.brokerage.core.application.shared.external.survey.SourceTermsPort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Survey source terms by lead and cycle. Each read is recorded into the shared call list as
 * {@code "terms <lead> <cycle>"}.
 */
public final class InMemorySourceTermsPort implements SourceTermsPort {

    private final List<String> calls;
    private final Map<String, SourceTermsFacts> terms = new HashMap<>();

    public InMemorySourceTermsPort(List<String> calls) {
        this.calls = calls;
    }

    public InMemorySourceTermsPort terms(String leadId, int intentCycle, SourceTermsFacts facts) {
        terms.put(leadId + " " + intentCycle, facts);
        return this;
    }

    @Override
    public Optional<SourceTermsFacts> currentTerms(String leadId, int intentCycle) {
        calls.add("terms " + leadId + " " + intentCycle);
        return Optional.ofNullable(terms.get(leadId + " " + intentCycle));
    }
}
