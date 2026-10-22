package vn.sunvibe.acquisition.core.domain.repository;

import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivity;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
}
