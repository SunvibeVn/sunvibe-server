package vn.sunvibe.acquisition.core.domain.repository;

import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntentChange;

import java.util.List;

/**
 * Domain repository of the requirement changes of a lead. Insert-only: a change is never updated or deleted.
 */
public interface OwnerLeadIntentChangeDomainRepository {

    void save(OwnerLeadIntentChange change);

    /** The requirement changes of one lead, oldest first. */
    List<OwnerLeadIntentChange> findByLeadIdOrderByOccurredAt(OwnerLeadId leadId);
}
