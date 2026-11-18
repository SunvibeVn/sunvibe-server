package vn.sunvibe.brokerage.core.testsupport;

import vn.sunvibe.shared.outbox.EventOutboxPort;
import vn.sunvibe.shared.outbox.OutboxEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Records each outbox event appended, in order; {@link #failWith} makes the next appends throw, as the adapter does for
 * an event its subscription list refuses.
 */
public final class RecordingEventOutboxPort implements EventOutboxPort {

    private final List<OutboxEvent> events = new ArrayList<>();
    private RuntimeException failure;

    /** Every append from now on throws this failure and records nothing. */
    public RecordingEventOutboxPort failWith(RuntimeException failure) {
        this.failure = failure;
        return this;
    }

    @Override
    public void append(OutboxEvent event) {
        if (failure != null) {
            throw failure;
        }
        events.add(event);
    }

    /** The events appended so far, in order. */
    public List<OutboxEvent> events() {
        return List.copyOf(events);
    }
}
