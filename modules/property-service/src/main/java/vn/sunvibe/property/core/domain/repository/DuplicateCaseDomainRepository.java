package vn.sunvibe.property.core.domain.repository;

import vn.sunvibe.property.core.domain.model.intake.CaseStatus;
import vn.sunvibe.property.core.domain.model.intake.DuplicateCase;

import java.util.List;
import java.util.Optional;

/** Duplicate cases (property-create intake spec 4.10). Reads answer the stored row as it stands now. */
public interface DuplicateCaseDomainRepository {

    /**
     * Inserts by native SQL with ON CONFLICT on the partial index, so two commands on one pair leave one row and no error.
     *
     * @return false when a row of this pair that is not CLOSED already stands
     */
    boolean insertIfAbsent(DuplicateCase row);

    /** The row of this pair that is not CLOSED; several CLOSED rows can share a key. */
    Optional<DuplicateCase> findLiveByPairKey(String pairKey);

    Optional<DuplicateCase> findById(String id);

    /** The row under a FOR NO KEY UPDATE lock, read after the lock is taken. */
    Optional<DuplicateCase> lockById(String id);

    /** Writes the row and flushes. */
    void update(DuplicateCase row);

    /** The cases of an intake on either side of a pair (spec 4.10). */
    List<DuplicateCase> findForIntake(String intakeId);

    /** The cases that name this property as their candidate. */
    List<DuplicateCase> findNamingProperty(String propertyId);

    /** The cases of a status in the order of the list: created_at, then id (spec 7.2). */
    List<DuplicateCase> findByStatus(CaseStatus status);
}
