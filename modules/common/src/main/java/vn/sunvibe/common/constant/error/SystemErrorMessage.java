package vn.sunvibe.common.constant.error;

public class SystemErrorMessage {
    // Common errors
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String BAD_REQUEST = "BAD_REQUEST";

    // Auth errors
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
    public static final String TOKEN_INVALID = "TOKEN_INVALID";

    // Business errors
    public static final String DUPLICATE_RESOURCE = "DUPLICATE_RESOURCE";
    public static final String INVALID_OPERATION = "INVALID_OPERATION";

    /*────────── EXCEPTION MESSAGES ──────────*/

    public static final String SERVER_ERROR = "Server error, please try again.";


    /*────────── VALIDATION MESSAGES ──────────*/

    public static final String VALIDATION_FAILED = "Validation failed.";

    private SystemErrorMessage() {}
}
