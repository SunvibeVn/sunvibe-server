package vn.sunvibe.acquisition.core.application.command;

import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;

import java.time.Instant;

/**
 * Command - ChangeLeadStatusCommand
 * <p>
 * Move a lead to another status, guarded by the version the caller last saw. {@code reason} is needed to close a
 * lead, {@code duplicateOfLeadId} to close it as a duplicate; note and next action time are optional (null when left
 * out).
 */
public record ChangeLeadStatusCommand(String leadId, OwnerLeadStatus toStatus, long expectedVersion, String reason,
                                      String duplicateOfLeadId, String note, Instant nextActionAt) {

    /** Reason and note are staff text: never print them. */
    @Override
    public String toString() {
        return "ChangeLeadStatusCommand[hidden]";
    }
}
