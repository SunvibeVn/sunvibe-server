package vn.sunvibe.acquisition.core.domain.repository;

import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivity;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;

import java.util.List;

/**
 * Domain repository of the lead history. Insert-only: an activity is never updated or deleted.
 */
public interface OwnerLeadActivityDomainRepository {

    void save(OwnerLeadActivity activity);

    /** The history of one lead, oldest first. */
    List<OwnerLeadActivity> findByLeadIdOrderByOccurredAt(OwnerLeadId leadId);
}
