package vn.sunvibe.survey.core.application.shared.external.media;

import java.io.Serial;

/**
 * The store failed or did not answer in time (survey-media spec 4.4 step 2, A-18, A-25): answered 503
 * MEDIA_STORE_UNAVAILABLE, and a store probe is asked for. The message is the code only: never a key, bucket,
 * endpoint or SDK text (survey-media spec 4.9).
 */
public class StoreUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** No free connection of the client's pool within its acquisition timeout. */
    public static final String POOL_EXHAUSTED = "STORE_POOL_EXHAUSTED";
    /** A connection could not be made, or the call timed out. */
    public static final String CONNECTION = "STORE_CONNECTION";
    /** The store answered a server error. */
    public static final String SERVER_ERROR = "STORE_SERVER_ERROR";
    /** The store refused the credentials or the request. */
    public static final String REFUSED = "STORE_REFUSED";
    /** The store answered in a way the contract does not allow (a probe's wrong answer). */
    public static final String WRONG_ANSWER = "STORE_WRONG_ANSWER";
    /** No store is configured (every adapter none). */
    public static final String NOT_CONFIGURED = "STORE_NOT_CONFIGURED";

    private final String code;

    public StoreUnavailableException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
