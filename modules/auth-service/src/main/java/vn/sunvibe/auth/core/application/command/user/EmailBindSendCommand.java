package vn.sunvibe.auth.core.application.command.user;

/**
 * The signed-in user asks for a code at a new address.
 *
 * @param currentPassword proves it is the user and not an open session on a shared machine
 */
public record EmailBindSendCommand(String newEmail, String currentPassword) {

    /** The password and the address stay out of logs. */
    @Override
    public String toString() {
        return "EmailBindSendCommand[hidden]";
    }
}
