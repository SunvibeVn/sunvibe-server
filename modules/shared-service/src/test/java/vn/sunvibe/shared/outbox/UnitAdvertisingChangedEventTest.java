package vn.sunvibe.shared.outbox;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** E05's event of ADR-012 decision 6 (E05-S5b spec 4.1, S5b-D1): the unit id only, never more. */
class UnitAdvertisingChangedEventTest {

    private static final String UNIT_ID = "01HZX3M5Q8R2T4V6W8Y0A2C4E6";

    @Test
    void now_carriesTheUnitIdOnly() {
        OutboxEvent event = UnitAdvertisingChangedEvent.now(UNIT_ID);

        assertEquals("brokerage.unit-advertising-changed", event.type());
        assertEquals(1, event.version());
        assertEquals("brokerage", event.sourceModule());
        assertEquals("unit", event.aggregateType());
        assertEquals(UNIT_ID, event.aggregateId());
        assertEquals(Map.of("unitId", UNIT_ID), event.payload());
        assertNull(event.dueAt());
    }

    @Test
    void dueAt_keepsTheDueTime() {
        Instant due = Instant.parse("2027-01-31T17:00:00Z");

        OutboxEvent event = UnitAdvertisingChangedEvent.dueAt(UNIT_ID, due);

        assertEquals(due, event.dueAt());
        assertEquals(Map.of("unitId", UNIT_ID), event.payload());
        assertThrows(IllegalArgumentException.class, () -> UnitAdvertisingChangedEvent.dueAt(UNIT_ID, null));
    }

    @Test
    void malformedUnitId_isRefused() {
        for (String unitId : new String[]{null, "01HZX3M5Q8R2T4V6W8Y0A2C4E", "01hzx3m5q8r2t4v6w8y0a2c4e6",
                "01HZX3M5Q8R2T4V6W8Y0A2C4EI", "01HZX3M5Q8R2T4V6W8Y0A2C4EL", "01HZX3M5Q8R2T4V6W8Y0A2C4EO",
                "01HZX3M5Q8R2T4V6W8Y0A2C4EU", "01HZX3M5Q8R2T4V6W8Y0A2C4 6"}) {
            assertThrows(IllegalArgumentException.class, () -> UnitAdvertisingChangedEvent.now(unitId),
                    String.valueOf(unitId));
            assertThrows(IllegalArgumentException.class,
                    () -> UnitAdvertisingChangedEvent.dueAt(unitId, Instant.parse("2027-01-31T17:00:00Z")),
                    String.valueOf(unitId));
        }
    }
}
