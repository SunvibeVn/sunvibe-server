package vn.sunvibe.brokerage.core.domain.repository;

/**
 * The agreement list's page of ids (E05-S7 spec S7-D1 to S7-D4, S7-D10): an interface of its own, so the agreement
 * repository and its test doubles stay as they are.
 */
public interface AgreementListDomainRepository {

    /**
     * One zero-based page of the ids of the agreements that match, and their count, read inside the caller's
     * transaction. Without the ending window: by creation time, newest first, then id descending. With it: the
     * ACTIVE and SUSPENDED agreements whose version that counts (the EFFECTIVE version with the highest number, else the
     * ACTIVATED one; E05-S6 spec S6-D7) ends within the window, by that end date, soonest first, then id ascending.
     */
    AgreementIdPage idPage(AgreementListCriteria criteria, int page, int size);
}
