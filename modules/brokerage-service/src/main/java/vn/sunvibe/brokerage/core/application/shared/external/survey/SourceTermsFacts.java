package vn.sunvibe.brokerage.core.application.shared.external.survey;

import java.time.LocalDate;
import java.util.List;

/**
 * The G8 terms of a survey revision in the survey's flattened forms (E05-S3 spec S3-D12, A-3): money as amount,
 * currency and unit, anything else as text; a common term has no space code. Survey terms are private
 * (brokerage-agreement BR-17), so no {@code toString} here shows a value.
 *
 * @param sourceDate the day of the visit, in Vietnam
 * @param spaces     the surveyed spaces with their codes
 */
public record SourceTermsFacts(String revisionId, LocalDate sourceDate, List<Space> spaces, List<Term> terms) {

    public SourceTermsFacts {
        spaces = List.copyOf(spaces);
        terms = List.copyOf(terms);
    }

    /** One surveyed space; its code names its terms. */
    public record Space(String spaceId, String code) {

        @Override
        public String toString() {
            return "Space[" + spaceId + "]";
        }
    }

    /**
     * One flattened term, such as {@code monthlyRent} of a space, a common {@code electricity}, or {@code otherFees:} and
     * {@code spaceFees:} followed by a fee's name.
     *
     * @param sourceOrNull the survey's source name; null when it gives none
     * @param dateOrNull   the date of an owner statement; null otherwise
     */
    public record Term(String name, String spaceCodeOrNull, Long amountOrNull, String currencyOrNull, String unitOrNull,
                       String textOrNull, String sourceOrNull, LocalDate dateOrNull) {

        @Override
        public String toString() {
            return "Term[hidden]";
        }
    }

    @Override
    public String toString() {
        return "SourceTermsFacts[" + revisionId + "]";
    }
}
