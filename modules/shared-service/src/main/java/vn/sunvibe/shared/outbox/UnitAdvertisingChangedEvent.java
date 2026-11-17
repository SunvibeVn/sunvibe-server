package vn.sunvibe.shared.outbox;

import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * E05's event of ADR-012 decision 6: the per-unit advertising answer of brokerage-agreement BR-12 for this unit may
 * have changed. It carries the unit id only; a consumer reads the current answer again (ADR-014 decision 6), so a
 * late, lost or repeated event never makes that answer wrong.
 *
 * <p>{@link #dueAt(String, Instant)} is only for an agreement version's start and the end of its end date.
 */
public final class UnitAdvertisingChangedEvent {

    public static final String TYPE = "brokerage.unit-advertising-changed";
    public static final int VERSION = 1;
    public static final String UNIT_ID = "unitId";
    public static final String SOURCE_MODULE = "brokerage";
    public static final String AGGREGATE_TYPE = "unit";

    private static final Pattern ULID = Pattern.compile("^[0-9A-HJKMNP-TV-Z]{26}$");

    private UnitAdvertisingChangedEvent() {}

    /** The event, due now. */
    public static OutboxEvent now(String unitId) {
        return build(unitId, null);
    }

    /** The event, due at {@code dueAt} (required). */
    public static OutboxEvent dueAt(String unitId, Instant dueAt) {
        if (dueAt == null) {
            throw new IllegalArgumentException("dueAt is required");
        }
        return build(unitId, dueAt);
    }

    private static OutboxEvent build(String unitId, Instant dueAt) {
        if (unitId == null || !ULID.matcher(unitId).matches()) {
            throw new IllegalArgumentException("unitId must be a ULID");
        }
        return new OutboxEvent(TYPE, VERSION, SOURCE_MODULE, AGGREGATE_TYPE, unitId, Map.of(UNIT_ID, unitId), dueAt);
    }
}
