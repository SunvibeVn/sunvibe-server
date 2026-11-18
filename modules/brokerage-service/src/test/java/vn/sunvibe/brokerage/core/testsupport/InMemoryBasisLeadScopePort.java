package vn.sunvibe.brokerage.core.testsupport;

import vn.sunvibe.brokerage.core.application.shared.external.lead.BasisLeadScopePort;
import vn.sunvibe.brokerage.core.application.shared.external.lead.LeadScopeFacts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The lead scope of each user in a map; an unknown user may read no lead. Every call records the user it asked for. */
public final class InMemoryBasisLeadScopePort implements BasisLeadScopePort {

    private final Map<String, LeadScopeFacts> scopes = new HashMap<>();
    private final List<String> askedFor = new ArrayList<>();

    /** The scope answered for the user from now on. */
    public void answer(String userId, LeadScopeFacts facts) {
        scopes.put(userId, facts);
    }

    @Override
    public LeadScopeFacts scopeOf(String userId, Set<String> expandedAuthorities) {
        askedFor.add(userId);
        return scopes.getOrDefault(userId, new LeadScopeFacts(false, Set.of()));
    }

    /** The users asked for so far, in order. */
    public List<String> askedFor() {
        return List.copyOf(askedFor);
    }
}
