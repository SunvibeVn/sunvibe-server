package vn.sunvibe.acquisition.core.domain.repository;

import vn.sunvibe.acquisition.core.domain.model.consent.ConsentRecord;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentRecordId;

import java.util.Optional;

/**
 * Domain repository of the ConsentRecord aggregate.
 */
public interface ConsentRecordDomainRepository {

    void save(ConsentRecord record);

    Optional<ConsentRecord> findById(ConsentRecordId id);
}
