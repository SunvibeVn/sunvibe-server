package vn.sunvibe.auth.core.application.exception.auth;

import vn.sunvibe.shared.exception.ApplicationException;

import java.io.Serial;

/** The phone operation is closed by {@code app.auth.phone.mode}. Answered with 403 {@code AUTH_CHANNEL_DISABLED}. */
public class AuthChannelDisabledException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AuthChannelDisabledException() {
        super("This sign-in method isn't available.");
    }
}
