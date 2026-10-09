package vn.sunvibe.common.exception;

public class BaseBusinessException extends RuntimeException {
    private final String errorCode;

    public BaseBusinessException(String message) {
        super(message);
        this.errorCode = "INTERNAL_ERROR";
    }

    public BaseBusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BaseBusinessException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
