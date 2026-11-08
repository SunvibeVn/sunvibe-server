package vn.sunvibe.property.core.domain.repository;

import vn.sunvibe.property.core.domain.model.intake.OwnerProof;

import java.util.List;
import java.util.Optional;

/** Owner proofs (property-create intake I-3). Reads answer the stored row as it stands now. */
public interface OwnerProofDomainRepository {

    /** Inserts or writes the row and flushes. */
    void save(OwnerProof proof);

    Optional<OwnerProof> findById(String id);

    /** The row under a FOR NO KEY UPDATE lock, read after the lock is taken. */
    Optional<OwnerProof> lockById(String id);

    /** The one proof of the intake neither withdrawn nor voided, live or not. */
    Optional<OwnerProof> findInForce(String intakeId);

    Optional<OwnerProof> findByRequestId(String requestId);

    List<OwnerProof> findByLeadId(String leadId);

    /** Every proof of the account, for the signals of the owner check (property-create intake spec 7.1). */
    List<OwnerProof> findByUserId(String userId);

    /** Every proof whose request this user created (property-create intake spec 7.1). */
    List<OwnerProof> findByRequestCreator(String creatorUserId);

    /**
     * Counts a wrong phrase in one guarded statement, so parallel entries all count (A-14). The fifth ends the phrase; the
     * proof stays.
     *
     * @return the attempts after this one; 0 when no phrase is open
     */
    int countWrongPhrase(String id, int limit);
}
