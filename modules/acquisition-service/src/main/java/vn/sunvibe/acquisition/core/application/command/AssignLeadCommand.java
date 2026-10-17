package vn.sunvibe.acquisition.core.application.command;

/**
 * Command - AssignLeadCommand
 * <p>
 * Give a lead to a staff member, guarded by the version the caller last saw. The optional note goes into the history
 * entry only.
 */
public record AssignLeadCommand(String leadId, String assigneeUserId, long expectedVersion, String note) {

    /** The note is staff text: never print it. */
    @Override
    public String toString() {
        return "AssignLeadCommand[hidden]";
    }
}
