package vn.sunvibe.survey.core.application.query.common;

import vn.sunvibe.survey.core.domain.model.checklist.RentalScope;
import vn.sunvibe.survey.core.domain.model.checklist.SubmittedSpace;
import vn.sunvibe.survey.core.domain.policy.checklist.UsabilityReason;

import java.util.List;

/**
 * Whether a PASSED revision can still be used and what was surveyed (survey-checklist submission spec 4.7): the rental
 * scope and spaces of its G1, never the brokerage scope.
 */
public record PassedView(boolean usable, UsabilityReason reasonOrNull, RentalScope rentalScope,
                         List<SubmittedSpace> spaces) {

    public PassedView {
        spaces = List.copyOf(spaces);
    }
}
