package vn.sunvibe.survey.core.application.shared.external.lead;

import java.math.BigDecimal;
import java.util.List;

/**
 * A meeting place checked by the intake location rules: the catalog names and the pin rounded as stored (null when
 * none), or the broken rules (then names and pin are null).
 */
public record PlaceCheck(String provinceName, String wardName, BigDecimal latitude, BigDecimal longitude,
                         List<PlaceViolation> violations) {

    public PlaceCheck {
        violations = List.copyOf(violations);
    }

    @Override
    public String toString() {
        return "PlaceCheck[" + violations.size() + " violations]";
    }
}
