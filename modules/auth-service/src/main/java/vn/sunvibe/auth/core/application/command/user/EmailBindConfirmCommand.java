package vn.sunvibe.auth.core.application.command.user;

import vn.sunvibe.auth.core.application.command.auth.ClientInfo;

/**
 * The signed-in user proves the new address.
 *
 * @param clientInfo the device that keeps a session; {@link ClientInfo#unknown()} when null
 */
public record EmailBindConfirmCommand(String newEmail, String code, ClientInfo clientInfo) {

    /** The code and the address stay out of logs. */
    @Override
    public String toString() {
        return "EmailBindConfirmCommand[hidden]";
    }
}
