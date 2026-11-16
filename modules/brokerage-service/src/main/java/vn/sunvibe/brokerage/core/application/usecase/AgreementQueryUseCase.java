package vn.sunvibe.brokerage.core.application.usecase;

import vn.sunvibe.brokerage.core.application.query.AgreementDetailView;
import vn.sunvibe.brokerage.core.application.query.SurveyTermsView;

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

    /**
     * The survey proposal of the agreement's draft terms (brokerage-agreement US-1 AC-3; E05-S3 spec S3-D11, S3-D12) for
     * staff who may see its basis lead: the G8 values of the survey revision the draft's intake rests on, for the unit's
     * space; the empty proposal when that revision is no longer the current one or the unit has no surveyed space. It
     * writes nothing, no access log included.
     *
     * @throws vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException a malformed or unknown id, or a
     *                                                                                    basis lead the viewer may not
     *                                                                                    see
     * @throws vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException  the agreement has no draft
     */
    SurveyTermsView surveyTerms(String agreementId, StaffViewer viewer);
}
