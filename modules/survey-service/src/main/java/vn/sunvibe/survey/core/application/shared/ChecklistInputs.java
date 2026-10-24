package vn.sunvibe.survey.core.application.shared;

import vn.sunvibe.survey.core.application.command.BasisInput;
import vn.sunvibe.survey.core.domain.exception.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.exception.InvalidSurveyValueException;
import vn.sunvibe.survey.core.domain.model.appointment.Basis;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.domain.policy.SurveyText;

import java.util.List;

/**
 * The body rules of the reasons and bases of takeovers, intake declarations and corrections (survey-checklist spec 5.5,
 * A-11): reason and basis text 1..500 without a phone number, cleaned by {@link SurveyText}, and the basis date
 * present. Its range needs the visit and is checked later. Paths carry the request's prefix.
 */
public final class ChecklistInputs {

    public static final int TEXT_MAX = 500;

    private ChecklistInputs() {
    }

    /** A reason with its basis, cleaned. */
    public record ReasonBasis(String reason, Basis basis) {

        @Override
        public String toString() {
            return "ReasonBasis[hidden]";
        }
    }

    /**
     * The cleaned reason and basis, or null after a violation, which is added to the list.
     *
     * @param prefix the request path before {@code reason} ({@code ""}, {@code "intake."}, {@code "correction."})
     */
    public static ReasonBasis reasonAndBasis(String prefix, String reason, BasisInput basis,
                                             List<Violation> violations) {
        String cleanReason = text(prefix + "reason", reason, violations);
        if (basis == null) {
            violations.add(new Violation(prefix + "basis", prefix + "basis is required."));
            return null;
        }
        if (basis.confirmedOn() == null) {
            violations.add(new Violation(prefix + "basis.confirmedOn", prefix + "basis.confirmedOn is required."));
        }
        String cleanText = text(prefix + "basis.text", basis.text(), violations);
        if (cleanReason == null || cleanText == null || basis.confirmedOn() == null) {
            return null;
        }
        return new ReasonBasis(cleanReason, new Basis(basis.confirmedOn(), cleanText));
    }

    public static void throwIfAny(List<Violation> violations) {
        if (!violations.isEmpty()) {
            throw new ChecklistValidationException(violations);
        }
    }

    private static String text(String path, String value, List<Violation> violations) {
        try {
            return SurveyText.require(path, value, TEXT_MAX);
        } catch (InvalidSurveyValueException e) {
            violations.add(new Violation(path, e.getMessage()));
            return null;
        }
    }
}
