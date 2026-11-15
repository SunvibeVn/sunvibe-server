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
}
