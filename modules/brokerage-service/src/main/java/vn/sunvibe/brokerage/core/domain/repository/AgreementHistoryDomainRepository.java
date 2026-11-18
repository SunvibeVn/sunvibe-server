package vn.sunvibe.brokerage.core.domain.repository;

/**
 * The read of an agreement's stored history (E05-S7 spec S7-D7, S7-D10): an interface of its own beside the insert-only
 * trail, so the trail and its test double stay as they are.
 */
public interface AgreementHistoryDomainRepository {

    /**
     * One zero-based page of the agreement's history rows, newest first (time, then the row's id, both descending), and
     * the number of its rows, read inside the caller's transaction.
     */
    AgreementHistoryPage history(String agreementId, int page, int size);
}
