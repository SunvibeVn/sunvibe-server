package vn.sunvibe.brokerage.core.domain.exception;

/**
 * A content value of an agreement version (its photo consent or its confirmed terms) breaks a rule of
 * brokerage-agreement BR-8 or BR-9. It names the field path only, relative to the value that refused it; its message
 * quotes no value (brokerage-agreement BR-17).
 */
public final class AgreementContentException extends RuntimeException {

    private final String field;

    public AgreementContentException(String field) {
        super("The agreement content is not accepted.");
        this.field = field;
    }

    /** The field path the refusal is about, such as {@code deposit.months} or {@code amount}. */
    public String field() {
        return field;
    }

    /** The same refusal with its field path inside {@code prefix}, such as {@code terms.mandatoryFees[2].}. */
    public AgreementContentException within(String prefix) {
        return new AgreementContentException(prefix + field);
    }
}
