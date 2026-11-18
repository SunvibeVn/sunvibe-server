package vn.sunvibe.brokerage.core.application.usecase;

import vn.sunvibe.brokerage.core.application.query.AgreementPageView;

/**
 * The staff list of brokerage agreements (brokerage-agreement US-4 AC-3; E05-S7 spec S7-D1 to S7-D6). A use case of its
 * own beside {@link AgreementQueryUseCase}, so the detail's use case and its tests stay as they are (spec S7-D10).
 */
public interface AgreementListUseCase {

    /**
     * One page of the agreements in the viewer's scope (brokerage-agreement BR-16: every agreement for a leader, a manager
     * or an admin, those whose basis lead is assigned to a Sale now for that Sale, none for anyone else), filtered by
     * stored state and by "ends within the next 30 days", each row read as it stands now with its advertising answer.
     * It writes nothing (brokerage-agreement BR-17: a row holds no private value, so no access-log row).
     */
    AgreementPageView list(AgreementListQuery query, StaffViewer viewer);
}
