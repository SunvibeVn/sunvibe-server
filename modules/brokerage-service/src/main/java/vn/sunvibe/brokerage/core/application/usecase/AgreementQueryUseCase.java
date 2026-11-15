package vn.sunvibe.brokerage.core.application.usecase;

import vn.sunvibe.brokerage.core.application.query.AgreementDetailView;

/** Use Case - the reads of a brokerage agreement. */
public interface AgreementQueryUseCase {

    /**
     * The detail of an agreement (brokerage-agreement US-4 AC-3 part) for staff who may see its basis lead
     * (brokerage-agreement BR-16). Every answered read writes one access-log row in the same transaction, and no row
     * when it is refused (brokerage-agreement BR-17).
     *
     * @throws vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException a malformed or unknown id, or a
     *                                                                                    basis lead the viewer may not
     *                                                                                    see; one answer for all three
     */
    AgreementDetailView detail(String agreementId, StaffViewer viewer);
}
