package vn.sunvibe.brokerage.core.testsupport;

import vn.sunvibe.brokerage.core.application.shared.external.lead.BasisLeadFacts;
import vn.sunvibe.brokerage.core.application.shared.external.lead.BasisLeadPort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Leads with their assignee and the users who may see them. Each read is recorded into the shared call list as
 * {@code "visible <lead> <user>"} or {@code "forUpdate <lead> <user>"}.
 */
public final class InMemoryBasisLeadPort implements BasisLeadPort {

    private final List<String> calls;
    private final Map<String, BasisLeadFacts> leads = new HashMap<>();
    private final Map<String, Set<String>> visibleTo = new HashMap<>();

    public InMemoryBasisLeadPort(List<String> calls) {
        this.calls = calls;
    }

    /** A lead with its assignee (null for none), visible to the given users. */
    public InMemoryBasisLeadPort lead(String leadId, String assigneeOrNull, String... visibleUserIds) {
        leads.put(leadId, new BasisLeadFacts(leadId, assigneeOrNull));
        visibleTo.put(leadId, Set.of(visibleUserIds));
        return this;
    }

    @Override
    public Optional<BasisLeadFacts> findVisible(String leadId, String userId, Set<String> expandedAuthorities) {
        calls.add("visible " + leadId + " " + userId);
        return read(leadId, userId);
    }

    @Override
    public Optional<BasisLeadFacts> findVisibleForUpdate(String leadId, String userId, Set<String> expandedAuthorities) {
        calls.add("forUpdate " + leadId + " " + userId);
        return read(leadId, userId);
    }

    private Optional<BasisLeadFacts> read(String leadId, String userId) {
        if (!visibleTo.getOrDefault(leadId, Set.of()).contains(userId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(leads.get(leadId));
    }
}
