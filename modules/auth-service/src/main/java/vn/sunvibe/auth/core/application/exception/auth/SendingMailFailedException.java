package vn.sunvibe.auth.core.application.exception.auth;

import vn.sunvibe.shared.exception.ApplicationException;

import java.io.Serial;

/**
 * An e-mail could not be handed to the mail server.
 */
public class SendingMailFailedException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SendingMailFailedException(String message) {
        super(message);
    }

}
