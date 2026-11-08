package vn.sunvibe.acquisition.core.application.usecase.inventory;

import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadViewer;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Use Case - LeadIntakeUseCase
 * <p>
 * What acquisition publishes for the inventory intake (property-create intake spec 5.1, ADR-009). The intake moves no lead:
 * it reads facts, holds the row against a move for the length of a command, and adds History entries as activity rows only.
 * The implementation never calls property-service back.
 */
public interface LeadIntakeUseCase {

    /**
     * The viewer of these authorities, by the lead queue's one role mapping; the authorities are already expanded through
     * the role hierarchy.
     *
     * @throws IllegalStateException none of the authorities may read owner leads (unreachable behind the endpoints'
     *                               role check)
     */
    SurveyLeadViewer viewerFor(String userId, Set<String> expandedAuthorities);

    /** The facts of the lead when it exists and the viewer may see it; empty otherwise (also for a malformed id). */
    Optional<LeadIntakeFacts> findVisible(String leadId, SurveyLeadViewer viewer);

    /**
     * As {@link #findVisible}, read fresh under a PostgreSQL {@code FOR NO KEY UPDATE} row lock held to the end of the
     * caller's transaction: the first load of the lead in an assignee command. Activity inserts do not wait for it.
     */
    Optional<LeadIntakeFacts> findVisibleForUpdate(String leadId, SurveyLeadViewer viewer);

    /**
     * The lead read fresh under a {@code FOR SHARE} row lock held to the end of the caller's transaction, with no viewer:
     * an owner has none. A move of the lead waits for the owner's command, and owner commands do not wait for each other.
     */
    Optional<LeadIntakeFacts> lockForShare(String leadId);

    /**
     * The facts of these leads with no viewer, in one read each kind. Only for what no staff member asks directly: the owner
     * commands, the manager's owner check and the duplicate signal, whose candidates a SALE's own view hides (property-create intake spec 4.10).
     * Only inside the caller's transaction; an unknown or malformed id is absent.
     */
    Map<String, LeadIntakeFacts> systemFacts(Collection<String> leadIds);

    /** The other open leads of this lead's phone, leaving out those the viewer cannot see (property-create intake spec 5.1, US-3 AC-2). */
    List<String> otherOpenLeadIdsOfPhone(String leadId, SurveyLeadViewer viewer);

    /**
     * Inserts the History entry of an intake as an activity row only: the lead's row, version and status never change
     * (BR-16). Only inside the caller's transaction.
     */
    void recordIntakeActivity(String leadId, LeadIntakeActivity activity);
}
