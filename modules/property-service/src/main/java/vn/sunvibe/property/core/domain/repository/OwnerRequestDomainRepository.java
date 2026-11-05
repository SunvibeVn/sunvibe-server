package vn.sunvibe.property.core.domain.repository;

import vn.sunvibe.property.core.domain.model.intake.OwnerRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Owner confirmation requests (property-create intake spec 4.6). Reads answer the stored row as it stands now. */
public interface OwnerRequestDomainRepository {

    /** Inserts or writes the row and flushes. */
    void save(OwnerRequest request);

    Optional<OwnerRequest> findById(String id);

    /** The row under a FOR NO KEY UPDATE lock, read after the lock is taken. */
    Optional<OwnerRequest> lockById(String id);

    List<OwnerRequest> findByIntakeId(String intakeId);

    /** The requests bound to an account, newest first. */
    List<OwnerRequest> findByTargetUser(String userId);

    /** A-5: how many requests the intake got since this time. */
    long countCreatedSince(String intakeId, Instant since);

    /**
     * Counts a wrong code on an open request in one guarded statement, so parallel tries all count, and voids the request at
     * the limit. A decline or a void raises no intake version, so staff cannot read it (A-13).
     *
     * @return the attempts after this one; 0 for a request already closed
     */
    int countWrongCode(String id, int limit, Instant now);
}
