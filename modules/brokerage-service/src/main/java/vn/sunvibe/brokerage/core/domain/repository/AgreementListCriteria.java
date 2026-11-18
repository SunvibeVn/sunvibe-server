package vn.sunvibe.brokerage.core.domain.repository;

import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;

import java.time.LocalDate;
import java.util.Set;

/**
 * What one page of the agreement list matches (E05-S7 spec S7-D2 to S7-D4, S7-D10). The ending window is two Vietnam
 * calendar dates the caller computed from its clock: the repository holds no clock.
 *
 * @param statuses           the stored states kept; empty for every state
 * @param basisLeadIdsOrNull the basis leads kept; null for every lead, an empty set matches nothing
 * @param endingFromOrNull   the first end date kept of the version that counts; null without the ending filter
 * @param endingToOrNull     the last end date kept; set exactly when {@code endingFromOrNull} is, never before it
 */
public record AgreementListCriteria(Set<AgreementStatus> statuses, Set<String> basisLeadIdsOrNull,
                                    LocalDate endingFromOrNull, LocalDate endingToOrNull) {

    public AgreementListCriteria {
        statuses = Set.copyOf(statuses);
        basisLeadIdsOrNull = basisLeadIdsOrNull == null ? null : Set.copyOf(basisLeadIdsOrNull);
        if ((endingFromOrNull == null) != (endingToOrNull == null)) {
            throw new IllegalArgumentException("The ending window needs both dates or none.");
        }
        if (endingFromOrNull != null && endingFromOrNull.isAfter(endingToOrNull)) {
            throw new IllegalArgumentException("The ending window starts after it ends.");
        }
    }

    /** Whether the ending filter applies. */
    public boolean ending() {
        return endingFromOrNull != null;
    }
}
