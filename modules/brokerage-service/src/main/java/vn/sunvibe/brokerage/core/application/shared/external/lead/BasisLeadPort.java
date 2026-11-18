package vn.sunvibe.brokerage.core.application.shared.external.lead;

import java.util.Optional;
import java.util.Set;

/**
 * The owner lead an intake basis belongs to, owned by acquisition-service; the adapter lives in the app module (ADR-009)
 * and joins the caller's transaction.
 */
public interface BasisLeadPort {

    /**
     * The lead when it exists and this user may see it by acquisition's visibility rule (a SALE only their own leads);
     * empty otherwise, also for a malformed id.
     *
     * @param expandedAuthorities the user's authorities, already expanded through the role hierarchy
     */
    Optional<BasisLeadFacts> findVisible(String leadId, String userId, Set<String> expandedAuthorities);

    /**
     * The lead as {@link #findVisible} answers it, read under a row lock held to the end of the caller's transaction, so
     * a reassignment, a hold or a closing of the lead waits for the caller or is seen by it. Only inside a transaction;
     * used by an assignee command (brokerage-agreement BR-16, E05-S2 spec S2-D2).
     *
     * @param expandedAuthorities the user's authorities, already expanded through the role hierarchy
     */
    Optional<BasisLeadFacts> findVisibleForUpdate(String leadId, String userId, Set<String> expandedAuthorities);
}
