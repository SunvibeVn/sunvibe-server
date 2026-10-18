package vn.sunvibe.acquisition.core.application.usecase;

import vn.sunvibe.acquisition.core.application.shared.external.staff.EligibleAssignee;

import java.util.List;

/**
 * Use Case - LeadAssigneeQueryUseCase
 * <p>
 * The staff members an owner lead may be given to (acquisition-owner-lead-queue US-3): the people the assign call
 * accepts, so a client can offer a choice that will not be refused.
 */
public interface LeadAssigneeQueryUseCase {

    /**
     * Every account that exists, may sign in and holds SALE or SALE_LEADER, once each, in the order the staff
     * directory gives (by name, then id), at most 500, the directory's cap. Holds no phone, e-mail or username.
     */
    List<EligibleAssignee> listEligible();
}
