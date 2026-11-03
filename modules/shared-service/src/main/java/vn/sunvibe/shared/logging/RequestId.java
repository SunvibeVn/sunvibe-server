package vn.sunvibe.shared.logging;

import org.slf4j.MDC;

/**
 * The server-generated id of one request, beside the client-chosen correlation id (survey-media plan PD-14, spec A-6,
 * A-19; SV-052): a ULID per request in the log context, stored on media access-log rows and media security events.
 * A worker or job run sets its own.
 */
public final class RequestId {

    public static final String MDC_KEY = "requestId";

    private RequestId() {
    }

    /** The id of the request (or run) handled on this thread, or null outside one. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
