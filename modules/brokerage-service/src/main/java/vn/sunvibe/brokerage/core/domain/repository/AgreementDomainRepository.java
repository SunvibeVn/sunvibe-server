package vn.sunvibe.brokerage.core.domain.repository;

import vn.sunvibe.brokerage.core.domain.exception.AgreementExistsException;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;

import java.time.Instant;
import java.time.LocalDate;
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

    /**
     * The agreements of those ids with every version and their fee and tier rows, in a fixed number of statements
     * whatever the count, read inside the caller's transaction (E05-S6 spec S6-D9); an id without a row is left out, no
     * order is promised, and an empty collection reads nothing.
     */
    List<BrokerageAgreement> findAllById(Collection<String> ids);

    /**
     * Locks the agreement row FOR SHARE until the caller's transaction ends (E05-S6 spec S6-D11: a guarded update of the
     * row waits for it; another share lock does not). The caller holds a read-write transaction.
     *
     * @return whether the row exists
     */
    boolean lockForShare(String agreementId);

    /**
     * The ids of the agreements whose version, confirmed by its owner, was confirmed at or before
     * {@code answeredAtOrBefore}, the oldest confirmation first, then by id, at most {@code limit} (E05-S6 spec S6-D5:
     * the 14-day lapses due). A confirmed version without an owner confirmation is never selected.
     */
    List<String> dueActivationLapseAgreementIds(Instant answeredAtOrBefore, int limit);

    /**
     * The ids of the agreements whose ACTIVATED version holds its activation record and starts on or before
     * {@code startOnOrBefore}, the oldest start first, then by id, at most {@code limit} (E05-S6 spec S6-D6: the starts
     * due).
     */
    List<String> dueStartAgreementIds(LocalDate startOnOrBefore, int limit);

    /**
     * Locks the agreement row until the caller's transaction ends, then reads the agreement with its versions and their
     * fee and tier rows fresh (E05-S8 spec S8-D8 step 3): the lock statement reads the agreements table alone and waits
     * for a writer that holds the row; only after it is granted do the reads take their snapshots, so they see every
     * write committed before the lock. Its row lock conflicts with FOR SHARE and with a guarded update of the row.
     *
     * @return empty when no row exists
     */
    Optional<BrokerageAgreement> findByIdForUpdate(String id);

    /**
     * The ids of the agreements stored ACTIVE or SUSPENDED that hold an EFFECTIVE or ACTIVATED version and none of those
     * whose end date is on or after {@code endBefore} (today in Vietnam), by id, at most {@code limit} (E05-S8 spec S8-D2,
     * plan E05S8-P6: the expiries due). An agreement in effect without such a version is never selected.
     */
    List<String> dueExpiryAgreementIds(LocalDate endBefore, int limit);

    /** An unfinished agreement of a unit, with the basis lead of its latest version. */
    record UnfinishedAgreement(String agreementId, String unitId, String basisLeadId) {
    }
}
