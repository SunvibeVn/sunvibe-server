package vn.sunvibe.auth.core.testsupport;

import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuthAuditEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Keeps recorded audit events in memory for assertions. */
public class RecordingAuditLog implements AuditLogPort {

    public final List<AuthAuditEvent> events = new ArrayList<>();

    @Override
    public void record(AuthAuditEvent event) {
        events.add(event);
    }

    /** No transactions here: recorded at once, like the real adapter outside a transaction. */
    @Override
    public void recordAfterCommit(AuthAuditEvent event) {
        events.add(event);
    }

    @Override
    public int purgeOlderThan(Instant cutoff) {
        throw new UnsupportedOperationException();
    }

    public List<AuditEventType> types() {
        return events.stream().map(AuthAuditEvent::type).toList();
    }

    public AuthAuditEvent last() {
        return events.getLast();
    }
}
