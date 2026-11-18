package vn.sunvibe.brokerage.core.application.usecase;

import org.junit.jupiter.api.Test;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The query records of the list and the history (E05-S7 spec S7-D10, 5.2): their bounds are a programming error no
 * request reaches, since the request converter refuses first (S7-D13).
 */
class AgreementQueryRecordsTest {

    @Test
    void agreementListQuery_outOfBounds_refused() {
        assertThrows(IllegalArgumentException.class, () -> new AgreementListQuery(Set.of(), false, -1, 20));
        assertThrows(IllegalArgumentException.class, () -> new AgreementListQuery(Set.of(), false, 10000, 20));
        assertThrows(IllegalArgumentException.class, () -> new AgreementListQuery(Set.of(), false, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new AgreementListQuery(Set.of(), false, 0, 51));
        assertDoesNotThrow(() -> new AgreementListQuery(Set.of(), false, 0, 1));
        assertDoesNotThrow(() -> new AgreementListQuery(Set.of(), true, 9999, 50));
        Set<AgreementStatus> statuses = new HashSet<>(Set.of(AgreementStatus.ACTIVE));
        AgreementListQuery query = new AgreementListQuery(statuses, false, 0, 20);
        statuses.add(AgreementStatus.SUSPENDED);
        assertEquals(Set.of(AgreementStatus.ACTIVE), query.statuses());
    }
}
