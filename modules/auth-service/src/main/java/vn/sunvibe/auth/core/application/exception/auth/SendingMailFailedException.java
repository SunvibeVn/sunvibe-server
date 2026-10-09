package vn.sunvibe.auth.core.application.exception.auth;

import vn.sunvibe.common.exception.ApplicationException;

import java.io.Serial;

public class SendingMailFailedException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SendingMailFailedException(String message) {
        super(message);
    }

}
