package vn.sunvibe.common.exception;

import java.io.Serial;

public class SendingMailFailedException extends InfrastructureException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SendingMailFailedException(String message) {
        super(message);
    }

    public SendingMailFailedException(String message, Throwable cause) {
        super(message, cause);
    }

}
