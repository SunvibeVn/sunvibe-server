package vn.sunvibe.listing.core.testsupport;

import vn.sunvibe.shared.outbox.EventOutboxPort;
import vn.sunvibe.shared.outbox.OutboxEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Records each outbox event appended, in order, as brokerage's fake does (listing-publish E06-S6 spec section 5). Not
 * final, so a test may subclass it to log its calls beside other doubles.
 */
public class RecordingEventOutboxPort implements EventOutboxPort {

    private final List<OutboxEvent> events = new ArrayList<>();

    @Override
    public void append(OutboxEvent event) {
        events.add(event);
    }

    /** The events appended so far, in order. */
    public List<OutboxEvent> events() {
        return List.copyOf(events);
    }
}
