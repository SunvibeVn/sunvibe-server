package vn.sunvibe.acquisition.core.application.usecase.survey;

import java.math.BigDecimal;
import java.util.List;

/**
 * The meeting place checked by the intake location rules (survey-schedule spec A-15): the catalog names of its province
 * and ward and its pin rounded as stored (null when none), or the broken rules. Names and pin are null when there is a
 * violation.
 */
public record MeetingPlaceCheck(String provinceName, String wardName, BigDecimal latitude, BigDecimal longitude,
                                List<MeetingPlaceViolation> violations) {

    public MeetingPlaceCheck {
        violations = List.copyOf(violations);
    }

    public boolean isValid() {
        return violations.isEmpty();
    }

    /** Location data: never print it. */
    @Override
    public String toString() {
        return "MeetingPlaceCheck[" + violations.size() + " violations]";
    }
}
