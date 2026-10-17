package vn.sunvibe.acquisition.core.application.exception;

import vn.sunvibe.shared.exception.ApplicationException;

import java.io.Serial;

/**
 * The lead changed since the staff member loaded it: the version they sent is not the current one, or another change
 * was saved at the same time (acquisition-owner-lead-queue BR-9). Nothing is changed; they reload the lead and try
 * again. The error names the request field that carried the version, or none when the request has no version.
 */
public class ConcurrentLeadModificationException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String field;

    /** The version the caller sent in {@code expectedVersion} is not the current one. */
    public ConcurrentLeadModificationException() {
        this("expectedVersion");
    }

    private ConcurrentLeadModificationException(String field) {
        super("The lead was changed by someone else. Reload it and try again.");
        this.field = field;
    }

    /** The conflict of a request that sends no version, such as a note: the error names no field. */
    public static ConcurrentLeadModificationException withoutField() {
        return new ConcurrentLeadModificationException(null);
    }

    /** The request field that carried the version, or null when the request has none. */
    public String getField() {
        return field;
    }
}
