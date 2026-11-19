package vn.sunvibe.brokerage.core.domain.repository;

import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementSuspension;

import java.time.Instant;
import java.util.List;

/**
 * The suspension records of the agreements (E05-S8 spec S8-D6, 5.1): kept beside the aggregate, written and read in the
 * agreement's transaction under its row lock. An agreement has at most one open record; the database refuses a second.
 */
public interface AgreementSuspensionDomainRepository {

    /**
     * Inserts the open record and flushes.
     *
     * @throws IllegalStateException when the agreement already holds an open record (E05-S8 spec S8-D6's rule broken)
     */
    void open(AgreementSuspension suspension);

    /**
     * Closes the agreement's open record with who lifted it, when and why.
     *
     * @return false when the agreement holds no open record
     */
    boolean release(String agreementId, String releasedBy, Instant releasedAt, String reason);

    /** The agreement's records, the newest suspension first (then the greater id), at most {@code limit}. */
    List<AgreementSuspension> newestOf(String agreementId, int limit);
}
