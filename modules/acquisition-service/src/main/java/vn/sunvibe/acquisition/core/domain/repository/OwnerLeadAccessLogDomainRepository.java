package vn.sunvibe.acquisition.core.domain.repository;

import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.log.OwnerLeadAccessLog;

import java.util.List;

/**
 * Domain repository of the lead access log. Insert-only: a log entry is never updated or deleted.
 */
public interface OwnerLeadAccessLogDomainRepository {

    void save(OwnerLeadAccessLog log);

    /** Every time the detail of one lead was opened, oldest first. */
    List<OwnerLeadAccessLog> findByLeadId(OwnerLeadId id);
}
