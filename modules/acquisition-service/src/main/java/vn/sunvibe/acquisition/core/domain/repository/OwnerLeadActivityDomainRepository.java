package vn.sunvibe.acquisition.core.domain.repository;

import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivity;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Domain repository of the lead history. Insert-only: an activity is never updated or deleted.
 */
public interface OwnerLeadActivityDomainRepository {

    void save(OwnerLeadActivity activity);

    /** The history of one lead, oldest first. */
    List<OwnerLeadActivity> findByLeadIdOrderByOccurredAt(OwnerLeadId leadId);

    /**
     * The latest activity time of each given lead, in one query. A lead without any activity is absent from the map;
     * an empty input returns an empty map without a query.
     */
    Map<String, Instant> findLatestOccurredAt(Collection<String> leadIds);

    /**
     * The time of the latest history entry that moved the lead to the given status (for example the moment it went on
     * hold), or empty when no entry did.
     */
    Optional<Instant> findLatestOccurredAtOfStatus(OwnerLeadId leadId, OwnerLeadStatus toStatus);

    /**
     * Every distinct staff id the lead's history names: actors and previous and new assignees, in one query
     * (survey-checklist spec A-25).
     */
    Set<String> findStaffIds(OwnerLeadId leadId);
}
