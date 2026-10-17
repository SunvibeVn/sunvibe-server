package vn.sunvibe.auth.core.presentation.controller.v1;

/** OpenAPI texts shared by the browser-session controllers. */
final class AuthV1ApiDocs {

    /**
     * The per-IP limit of the rate limiter (auth-browser-session spec §3.8): each rate-limited operation shares the
     * limit of its legacy pair; the limiter answers before the controller runs.
     */
    static final String RATE_LIMITED = "RATE_LIMITED: too many requests from this IP address; "
            + "Retry-After gives the seconds to wait";

    /** A send-code operation: its own code limits and the per-IP limit share the one 429 entry. */
    static final String CODE_LIMITS_OR_RATE_LIMITED = "CODE_RESEND_TOO_SOON or CODE_DAILY_LIMIT (the code limits), "
            + "or RATE_LIMITED (too many requests from this IP address); Retry-After gives the seconds to wait";

    private AuthV1ApiDocs() {
    }
}
