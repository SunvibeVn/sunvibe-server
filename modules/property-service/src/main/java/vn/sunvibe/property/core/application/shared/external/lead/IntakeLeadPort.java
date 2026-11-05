package vn.sunvibe.property.core.application.shared.external.lead;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The owner lead behind an intake, owned by acquisition-service; the adapter lives in the app module (ADR-009). An intake
 * never moves a lead (property-create BR-16): it reads, holds the row for a command, and adds History entries.
 */
public interface IntakeLeadPort {

    /** The viewer of these authorities, already expanded through the role hierarchy. */
    IntakeViewer viewerFor(String userId, Set<String> expandedAuthorities);

    /** The lead when it exists and the viewer may see it; empty otherwise. */
    Optional<IntakeLead> findVisible(String leadId, IntakeViewer viewer);

    /** As {@link #findVisible}, read fresh under a FOR NO KEY UPDATE row lock held to the end of the transaction. */
    Optional<IntakeLead> findVisibleForUpdate(String leadId, IntakeViewer viewer);

    /** The lead read fresh under a FOR SHARE row lock held to the end of the transaction, with no viewer. */
    Optional<IntakeLead> lockForShare(String leadId);

    /**
     * The facts of these leads with no viewer: only for the owner commands, the manager's owner check and the duplicate
     * signal, whose candidates a Sale's own view hides. Inside the caller's transaction only.
     */
    Map<String, IntakeLead> systemFacts(Collection<String> leadIds);

    /** The other open leads of this lead's phone, leaving out those the viewer cannot see. */
    List<String> otherOpenLeadIdsOfPhone(String leadId, IntakeViewer viewer);

    /** Adds a History entry as an activity row only, in the caller's transaction. */
    void recordActivity(String leadId, IntakeHistoryEntry entry);
}
