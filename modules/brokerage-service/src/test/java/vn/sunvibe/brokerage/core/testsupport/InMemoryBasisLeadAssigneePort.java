package vn.sunvibe.brokerage.core.testsupport;

import vn.sunvibe.brokerage.core.application.shared.external.lead.BasisLeadAssigneePort;
import vn.sunvibe.brokerage.core.application.shared.external.lead.LeadAssigneeFacts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The leads' assignees in a map; an unknown lead is absent; every call is recorded with its ids. */
public final class InMemoryBasisLeadAssigneePort implements BasisLeadAssigneePort {

    private final Map<String, LeadAssigneeFacts> leads = new HashMap<>();
    private final List<String> calls = new ArrayList<>();

    public InMemoryBasisLeadAssigneePort lead(String leadId, String assigneeOrNull) {
        leads.put(leadId, new LeadAssigneeFacts(leadId, assigneeOrNull));
        return this;
    }

    @Override
    public List<LeadAssigneeFacts> assigneesOf(Collection<String> leadIds) {
        calls.add("assigneesOf " + List.copyOf(leadIds));
        return leadIds.stream().distinct().filter(leads::containsKey).map(leads::get).toList();
    }

    /** The calls made so far, as "assigneesOf [ids]", in order. */
    public List<String> calls() {
        return List.copyOf(calls);
    }
}
