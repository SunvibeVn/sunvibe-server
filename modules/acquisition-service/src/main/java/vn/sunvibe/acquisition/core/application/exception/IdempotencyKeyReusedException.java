package vn.sunvibe.acquisition.core.application.exception;

import vn.sunvibe.shared.exception.ApplicationException;

import java.io.Serial;

/**
 * The idempotency key was already used within 24 hours for a request with a different body
 * (acquisition-owner-intake BR-10). Nothing is stored. The message names neither the key nor any submitted value.
 */
public class IdempotencyKeyReusedException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public IdempotencyKeyReusedException() {
        super("The idempotency key was already used for a different request.");
    }
}
