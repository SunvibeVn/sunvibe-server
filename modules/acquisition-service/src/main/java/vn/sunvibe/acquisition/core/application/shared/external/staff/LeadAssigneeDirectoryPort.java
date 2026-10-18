package vn.sunvibe.acquisition.core.application.shared.external.staff;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only view of staff accounts, owned by another module. The adapter lives in the app module, so
 * acquisition-service does not depend on auth-service (ADR-009).
 */
public interface LeadAssigneeDirectoryPort {

    /**
     * True if the account exists, may sign in and holds the SALE or SALE_LEADER role, so an owner lead may be given
     * to it. Unknown, deleted or blank ids give false.
     */
    boolean isEligibleAssignee(String userId);

    /**
     * Every account for which {@link #isEligibleAssignee} is true, once each, in the order the provider sorts them
     * (by name, then id) and at most as many as the provider allows.
     */
    List<EligibleAssignee> listEligibleAssignees();

    /**
     * The full name of each account with one of these ids, including accounts that were locked, deleted or lost
     * their role since (acquisition-owner-lead-queue-ui C-35). Unknown ids are absent. Only for ids read from rows
     * the caller may see; names are never logged.
     */
    Map<String, String> findDisplayNames(Set<String> userIds);
}
