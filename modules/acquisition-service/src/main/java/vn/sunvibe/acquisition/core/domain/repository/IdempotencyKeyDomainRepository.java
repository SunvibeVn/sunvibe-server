package vn.sunvibe.acquisition.core.domain.repository;

import vn.sunvibe.acquisition.core.domain.model.idempotency.IdempotencyRecord;

import java.time.Instant;
import java.util.Optional;

/**
 * Domain repository of the idempotency keys of the public owner intake.
 */
public interface IdempotencyKeyDomainRepository {

    Optional<IdempotencyRecord> findByKey(String key);

    void save(IdempotencyRecord record);

    void deleteByKey(String key);

    /** Delete the keys created before the cutoff and return how many were deleted. */
    int deleteCreatedBefore(Instant cutoff);
}
