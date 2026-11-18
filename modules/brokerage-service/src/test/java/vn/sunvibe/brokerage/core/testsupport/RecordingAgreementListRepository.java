package vn.sunvibe.brokerage.core.testsupport;

import vn.sunvibe.brokerage.core.domain.repository.AgreementIdPage;
import vn.sunvibe.brokerage.core.domain.repository.AgreementListCriteria;
import vn.sunvibe.brokerage.core.domain.repository.AgreementListDomainRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Answers every page with the page it was given (an empty one at first) and records each call's criteria; the SQL's
 * matching and order are pinned on PostgreSQL.
 */
public final class RecordingAgreementListRepository implements AgreementListDomainRepository {

    private final List<AgreementListCriteria> criteria = new ArrayList<>();
    private AgreementIdPage answer = new AgreementIdPage(List.of(), 0);

    /** The page answered from now on. */
    public void answer(AgreementIdPage page) {
        answer = page;
    }

    @Override
    public AgreementIdPage idPage(AgreementListCriteria criteria, int page, int size) {
        this.criteria.add(criteria);
        return answer;
    }

    /** The criteria of each call so far, in order. */
    public List<AgreementListCriteria> criteria() {
        return List.copyOf(criteria);
    }

    /** How many pages were asked for. */
    public int calls() {
        return criteria.size();
    }
}
