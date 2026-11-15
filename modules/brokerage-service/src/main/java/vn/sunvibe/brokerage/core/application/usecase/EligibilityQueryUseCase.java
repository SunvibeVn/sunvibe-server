package vn.sunvibe.brokerage.core.application.usecase;

import vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException;
import vn.sunvibe.brokerage.core.application.query.EligibilityView;

/**
 * Use Case - whether each unit of a lead's intake basis could get a brokerage agreement now, and which groups of reasons
 * stop it (brokerage-agreement US-1 AC-1, AC-2). A read that writes nothing.
 */
public interface EligibilityQueryUseCase {

    /**
     * The eligibility of every unit of the lead's latest COMPLETED intake.
     *
     * @throws BrokerageNotFoundException the lead is unknown, malformed or not visible to the viewer
     */
    EligibilityView forLead(String leadId, StaffViewer viewer);
}
