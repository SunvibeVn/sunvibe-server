package vn.sunvibe.auth.core.application.command.user;

import vn.sunvibe.auth.core.application.command.auth.ClientInfo;

/**
 * Registration by a proven mailbox.
 *
 * @param registrationToken proof of the mailbox, from the verify-code step
 * @param clientInfo        the device that gets the first session; {@link ClientInfo#unknown()} when null
 */
public record EmailRegisterCommand(String email, String registrationToken, String fullName, String password,
                                   ClientInfo clientInfo) {

    /** The token and the password stay out of logs. */
    @Override
    public String toString() {
        return "EmailRegisterCommand[hidden]";
    }
}
