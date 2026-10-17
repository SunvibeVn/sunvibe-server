package vn.sunvibe.auth.core.application.exception.auth;

import vn.sunvibe.shared.exception.ApplicationException;

import java.io.Serial;

/**
 * A session request (refresh or logout) has no {@code Origin} header or comes
 * from an origin that may not use it. Answered with 403; the handler sends its
 * own fixed message, so this one is for the log only.
 */
public class OriginNotAllowedException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OriginNotAllowedException() {
        super("Request origin is not allowed.");
    }
}
