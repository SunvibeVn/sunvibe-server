package vn.sunvibe.acquisition.core.application.usecase;

import vn.sunvibe.acquisition.core.application.query.LeadListQuery;
import vn.sunvibe.acquisition.core.application.query.OwnerLeadDetail;
import vn.sunvibe.acquisition.core.application.query.OwnerLeadSummary;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadViewer;
import vn.sunvibe.acquisition.core.domain.repository.PageResult;

/**
 * Use Case - OwnerLeadQueryUseCase
 * <p>
 * Staff read the owner-lead queue. Only leads the viewer may see are listed or opened: a SALE sees only the leads
 * assigned to them (acquisition-owner-lead-queue BR-1). Opening a lead is recorded in the access log
 * (acquisition-owner-lead-queue BR-7), listing is not.
 */
public interface OwnerLeadQueryUseCase {

    /** One page of the queue, newest first. */
    PageResult<OwnerLeadSummary> list(LeadViewer viewer, LeadListQuery query);

    /**
     * The detail of one lead, and one access-log row for the viewer.
     *
     * @throws vn.sunvibe.acquisition.core.application.exception.OwnerLeadNotFoundException the lead is unknown or
     *                                                                                      not visible to the viewer
     */
    OwnerLeadDetail get(LeadViewer viewer, String leadId);
}
