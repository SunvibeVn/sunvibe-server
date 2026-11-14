package vn.sunvibe.acquisition.core.application.command;

import vn.sunvibe.acquisition.core.domain.exception.InvalidLeadCommandException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Command - AssignLeadsCommand
 * <p>
 * Give several leads to one staff member in one action (acquisition-owner-lead-bulk-assign spec 3.1), each lead
 * guarded by the version the caller saw. The optional note goes into the history entry of every lead that is assigned.
 */
public record AssignLeadsCommand(String assigneeUserId, String note, List<LeadInput> leads) {

    /** acquisition-owner-lead-bulk-assign BR-3: one action holds 1 to 20 distinct leads. */
    public static final int MAX_LEADS = 20;

    /** One lead of the action and the version the caller saw. */
    public record LeadInput(String leadId, long expectedVersion) {
    }

    /**
     * acquisition-owner-lead-bulk-assign BR-3, D-4: 1 to {@link #MAX_LEADS} leads, none of them twice (ids compared as
     * exact strings); refused on {@code leads} with a message that names no lead.
     */
    public AssignLeadsCommand {
        leads = List.copyOf(leads);
        if (leads.isEmpty() || leads.size() > MAX_LEADS) {
            throw new InvalidLeadCommandException("leads", "Leads must hold 1 to " + MAX_LEADS + " leads.");
        }
        Set<String> seen = new HashSet<>();
        for (LeadInput lead : leads) {
            if (!seen.add(lead.leadId())) {
                throw new InvalidLeadCommandException("leads", "A lead may appear only once.");
            }
        }
    }

    /** The note is staff text: never print it. */
    @Override
    public String toString() {
        return "AssignLeadsCommand[hidden]";
    }
}
