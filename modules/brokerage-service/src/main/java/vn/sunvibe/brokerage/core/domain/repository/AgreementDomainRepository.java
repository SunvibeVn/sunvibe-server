package vn.sunvibe.brokerage.core.domain.repository;

import vn.sunvibe.brokerage.core.domain.exception.AgreementExistsException;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** The stored brokerage agreements and their versions. */
public interface AgreementDomainRepository {

    /**
     * Inserts a new agreement with its versions and flushes.
     *
     * @throws AgreementExistsException when the database refuses a second unfinished agreement of the unit and type
     *                                  (brokerage-agreement BR-1)
     */
    void create(BrokerageAgreement agreement);

    /** The agreement with its versions and their fee rows, all read inside the caller's transaction. */
    Optional<BrokerageAgreement> findById(String id);

    /**
     * Writes a loaded agreement with its versions and their fee rows, guarded by the row version it was read with, and
     * flushes; the row version moves on (E05-S3 spec S3-D4). A stale row version fails the write and writes nothing.
     */
    void update(BrokerageAgreement agreement);

    /**
     * The unfinished agreements of those units and that type (brokerage-agreement BR-1: at most one per unit); empty
     * for an empty collection.
     */
    List<UnfinishedAgreement> unfinishedOf(Collection<String> unitIds, TransactionType type);

    /**
     * The owner's agreements that hold at least one version sent to them, newest sending first (the latest sending of
     * each), then by id, at most {@code limit}; each with every version and their fee and tier rows, read inside the
     * caller's transaction (E05-S5 spec S5-D1).
     */
    List<BrokerageAgreement> sentToOwner(String ownerUserId, int limit);

    /**
     * The ids of the agreements whose version waits for the owner and was sent at or before {@code sentAtOrBefore}, the
     * oldest sending first, then by id, at most {@code limit} (E05-S5 spec S5-D8: the lapses due). A version never sent
     * is never selected.
     */
    List<String> dueLapseAgreementIds(Instant sentAtOrBefore, int limit);

    /** An unfinished agreement of a unit, with the basis lead of its latest version. */
    record UnfinishedAgreement(String agreementId, String unitId, String basisLeadId) {
    }
}
