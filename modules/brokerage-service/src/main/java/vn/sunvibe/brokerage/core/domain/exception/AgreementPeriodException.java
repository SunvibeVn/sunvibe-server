package vn.sunvibe.brokerage.core.domain.exception;

/**
 * An agreement period breaks brokerage-agreement BR-6. It names the field only; its message quotes no date.
 */
public class AgreementPeriodException extends RuntimeException {

    private final String field;

    public AgreementPeriodException(String field) {
        super("The agreement period is not accepted.");
        this.field = field;
    }

    /** The request field the refusal is about: {@code startDate} or {@code endDate}. */
    public String field() {
        return field;
    }
}
