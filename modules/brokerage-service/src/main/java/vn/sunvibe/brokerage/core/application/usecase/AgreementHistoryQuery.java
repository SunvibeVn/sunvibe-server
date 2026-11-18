package vn.sunvibe.brokerage.core.application.usecase;

import java.util.Objects;

/**
 * What one read of an agreement's history asks (E05-S7 spec S7-D7, S7-D10). The request converter refuses a value
 * outside the bounds first (S7-D13), so a refusal here is a programming error no request reaches.
 *
 * @param agreementId the agreement as given; a malformed one is answered as unknown by the use case
 * @param page        the zero-based page, 0 to {@link #MAX_PAGE}
 * @param size        the entries of a page, 1 to {@link #MAX_SIZE}
 */
public record AgreementHistoryQuery(String agreementId, int page, int size) {

    /** The highest page a read may ask for. */
    public static final int MAX_PAGE = 9999;
    /** The most entries of one page. */
    public static final int MAX_SIZE = 100;

    public AgreementHistoryQuery {
        Objects.requireNonNull(agreementId, "agreementId");
        if (page < 0 || page > MAX_PAGE || size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("The page or the size of the agreement history is out of its bounds.");
        }
    }
}
