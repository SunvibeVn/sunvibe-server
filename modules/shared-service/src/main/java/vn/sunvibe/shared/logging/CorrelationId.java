package vn.sunvibe.shared.logging;

import java.util.regex.Pattern;

import org.slf4j.MDC;

/** The id that ties one request to its log lines and to the error body it returned (ADR-010). */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    private CorrelationId() {}

    /** The id of the request being handled on this thread, or null outside a request. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    /** True when a client-sent id is safe to log and echo: 8 to 64 letters, digits or dashes. */
    public static boolean isValid(String candidate) {
        return candidate != null && VALID.matcher(candidate).matches();
    }
}
