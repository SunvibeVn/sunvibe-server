package vn.sunvibe.brokerage.core.application.usecase;

import vn.sunvibe.brokerage.core.application.query.AgreementHistoryView;

/**
 * The history of one brokerage agreement in the staff workspace (brokerage-agreement US-4 AC-4; E05-S7 spec S7-D7,
 * S7-D8). A use case of its own beside {@link AgreementQueryUseCase}, so the detail's use case stays as it is (spec
 * S7-D10).
 */
public interface AgreementHistoryUseCase {

    /**
     * One page of the agreement's stored events, newest first: what happened, the version, who acted (a staff member by
     * name, the owner never named, or Sunvibe's own job) and when; no reason, note, term or user id
     * (brokerage-agreement BR-17). The readers are the detail's: staff who may see the basis lead (BR-16); an unknown, a
     * malformed and a hidden agreement get one not-found answer. It writes nothing.
     *
     * @throws vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException for the three
     */
    AgreementHistoryView history(AgreementHistoryQuery query, StaffViewer viewer);
}
