package vn.sunvibe.brokerage.core.domain.repository;

import org.junit.jupiter.api.Test;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The list's criteria (E05-S7 spec S7-D10): the states and lead ids copied, null lead ids for every lead, and the
 * ending window both dates or none, in order.
 */
class AgreementListCriteriaTest {

    private static final LocalDate DAY = LocalDate.parse("2026-12-01");

    @Test
    void criteria_endingHalfSet_refused() {
        assertThrows(IllegalArgumentException.class,
                () -> new AgreementListCriteria(Set.of(), null, DAY, null));
        assertThrows(IllegalArgumentException.class,
                () -> new AgreementListCriteria(Set.of(), null, null, DAY));
    }

    @Test
    void criteria_endingFromAfterTo_refused() {
        assertThrows(IllegalArgumentException.class,
                () -> new AgreementListCriteria(Set.of(), null, DAY.plusDays(1), DAY));
        assertDoesNotThrow(() -> new AgreementListCriteria(Set.of(), null, DAY, DAY));
    }

    @Test
    void criteria_sets_copiedNullLeadsKept() {
        Set<AgreementStatus> statuses = new HashSet<>(Set.of(AgreementStatus.ACTIVE));
        Set<String> leads = new HashSet<>(Set.of("L1"));

        AgreementListCriteria criteria = new AgreementListCriteria(statuses, leads, null, null);
        statuses.add(AgreementStatus.SUSPENDED);
        leads.add("L2");

        assertEquals(Set.of(AgreementStatus.ACTIVE), criteria.statuses());
        assertEquals(Set.of("L1"), criteria.basisLeadIdsOrNull());
        assertNull(new AgreementListCriteria(Set.of(), null, null, null).basisLeadIdsOrNull());
        assertEquals(Set.of(), new AgreementListCriteria(Set.of(), Set.of(), null, null).basisLeadIdsOrNull());
    }
}
