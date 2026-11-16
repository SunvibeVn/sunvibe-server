package vn.sunvibe.brokerage.core.application.usecase;

import vn.sunvibe.brokerage.core.application.command.AgreementCreateCommand;

/** Use Case - the commands of a brokerage agreement. */
public interface AgreementCommandUseCase {

    /**
     * The lead's assigned Sale drafts an agreement for one unit of the lead's intake basis (brokerage-agreement US-1
     * AC-1 to AC-4): DRAFTING with its version 1 DRAFT and one history row. In one transaction, the first failing step
     * answers: the transaction type and the period (400); the lead, locked and visible to the actor (404); the actor is
     * its current assignee with SALE or SALE_LEADER (403); the unit's eligibility on the lead's basis (409 with the
     * groups); no unfinished agreement of the unit and type (409).
     *
     * @throws vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException a refused step
     * @throws vn.sunvibe.brokerage.core.domain.exception.AgreementPeriodException        the period breaks BR-6
     * @throws vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException the lead is not visible
     * @throws vn.sunvibe.brokerage.core.domain.exception.AgreementExistsException        a concurrent create took the
     *                                                                                    unit first
     */
    AgreementCreateResult create(AgreementCreateCommand command, StaffViewer actor);
}
