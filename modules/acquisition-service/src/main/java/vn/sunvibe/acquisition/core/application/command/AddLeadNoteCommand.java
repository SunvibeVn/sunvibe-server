package vn.sunvibe.acquisition.core.application.command;

import java.time.Instant;

/**
 * Command - AddLeadNoteCommand
 * <p>
 * Record a note on a lead, optionally with the time of the next action (null keeps the current one).
 */
public record AddLeadNoteCommand(String leadId, String note, Instant nextActionAt) {

    /** The note is staff text: never print it. */
    @Override
    public String toString() {
        return "AddLeadNoteCommand[hidden]";
    }
}
