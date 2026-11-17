package vn.sunvibe.shared.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The envelope's shape checks (E05-S5b spec 4.1): each failure is an IllegalArgumentException, never an NPE. */
class OutboxEventTest {

    private static final Map<String, String> PAYLOAD = Map.of("unitId", "u-1");

    @Test
    void invalidShape_isRefused() {
        Map<String, String> eleven = new HashMap<>();
        for (int i = 0; i < 11; i++) {
            eleven.put("k" + i, "v");
        }
        Map<String, String> nullKey = new HashMap<>();
        nullKey.put(null, "v");
        Map<String, String> nullValue = new HashMap<>();
        nullValue.put("unitId", null);

        Executable[] cases = {
                () -> event("Brokerage.changed", 1, "brokerage", "unit", "u-1", PAYLOAD),
                () -> event("brokerage", 1, "brokerage", "unit", "u-1", PAYLOAD),
                () -> event("a." + "b".repeat(99), 1, "brokerage", "unit", "u-1", PAYLOAD),
                () -> event(null, 1, "brokerage", "unit", "u-1", PAYLOAD),
                () -> event("brokerage.changed", 0, "brokerage", "unit", "u-1", PAYLOAD),
                () -> event("brokerage.changed", 100, "brokerage", "unit", "u-1", PAYLOAD),
                () -> event("brokerage.changed", 1, "Brokerage", "unit", "u-1", PAYLOAD),
                () -> event("brokerage.changed", 1, null, "unit", "u-1", PAYLOAD),
                () -> event("brokerage.changed", 1, "brokerage", "unit-x", "u-1", PAYLOAD),
                () -> event("brokerage.changed", 1, "brokerage", null, "u-1", PAYLOAD),
                () -> event("brokerage.changed", 1, "brokerage", "unit", "u 1", PAYLOAD),
                () -> event("brokerage.changed", 1, "brokerage", "unit", "a".repeat(65), PAYLOAD),
                () -> event("brokerage.changed", 1, "brokerage", "unit", null, PAYLOAD),
                () -> event("brokerage.changed", 1, "brokerage", "unit", "u-1", null),
                () -> event("brokerage.changed", 1, "brokerage", "unit", "u-1", eleven),
                () -> event("brokerage.changed", 1, "brokerage", "unit", "u-1", nullKey),
                () -> event("brokerage.changed", 1, "brokerage", "unit", "u-1", nullValue),
        };

        for (int i = 0; i < cases.length; i++) {
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, cases[i], "case " + i);
            assertFalse(refused.getMessage().contains("u 1"), "the message names the field, never its value");
        }
    }

    @Test
    void payload_isCopied() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("unitId", "u-1");

        OutboxEvent event = event("brokerage.changed", 1, "brokerage", "unit", "u-1", source);
        source.put("unitId", "changed");
        source.put("other", "x");

        assertEquals(Map.of("unitId", "u-1"), event.payload());
        assertThrows(UnsupportedOperationException.class, () -> event.payload().put("x", "y"));
    }

    private static OutboxEvent event(String type, int version, String sourceModule, String aggregateType,
                                     String aggregateId, Map<String, String> payload) {
        return new OutboxEvent(type, version, sourceModule, aggregateType, aggregateId, payload, null);
    }
}
