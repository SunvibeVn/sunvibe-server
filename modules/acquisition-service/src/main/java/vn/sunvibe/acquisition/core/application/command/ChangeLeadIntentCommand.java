package vn.sunvibe.acquisition.core.application.command;

import vn.sunvibe.acquisition.core.domain.model.lead.AskingPrice;
import vn.sunvibe.acquisition.core.domain.model.lead.ConfirmationBasis;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;

import java.util.Optional;

/**
 * Command - ChangeLeadIntentCommand
 * <p>
 * Record a new requirement agreed with the owner (survey-schedule US-5), guarded by the version the caller last saw:
 * the new rental intent, its price or none ({@code Optional.empty()}), why, and the owner's confirmation.
 */
public record ChangeLeadIntentCommand(String leadId, long expectedVersion, OwnerLeadIntent newIntent,
                                      Optional<AskingPrice> askingPrice, String reason, ConfirmationBasis basis) {

    public ChangeLeadIntentCommand {
        if (askingPrice == null) {
            throw new IllegalArgumentException("askingPrice is required (empty for no price).");
        }
    }

    /** Reason and basis are staff text: never print them. */
    @Override
    public String toString() {
        return "ChangeLeadIntentCommand[hidden]";
    }
}
