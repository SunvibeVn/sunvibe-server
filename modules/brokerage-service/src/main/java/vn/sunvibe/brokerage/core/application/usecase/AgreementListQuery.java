package vn.sunvibe.brokerage.core.application.usecase;

import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;

import java.util.Set;

/**
 * What one read of the agreement list asks (E05-S7 spec S7-D1, S7-D10). The request converter refuses a value outside
 * the bounds first (S7-D13), so a refusal here is a programming error no request reaches.
 *
 * @param statuses         the stored states kept; empty for every state
 * @param endsWithin30Days keep only the agreements in effect whose right ends within the next 30 days
 * @param page             the zero-based page, 0 to {@link #MAX_PAGE}
 * @param size             the rows of a page, 1 to {@link #MAX_SIZE}
 */
public record AgreementListQuery(Set<AgreementStatus> statuses, boolean endsWithin30Days, int page, int size) {

    /** The highest page a read may ask for. */
    public static final int MAX_PAGE = 9999;
    /** The most rows of one page: each row carries an assembled advertising answer (spec S7-D1). */
    public static final int MAX_SIZE = 50;

    public AgreementListQuery {
        statuses = Set.copyOf(statuses);
        if (page < 0 || page > MAX_PAGE || size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("The page or the size of the agreement list is out of its bounds.");
        }
    }
}
