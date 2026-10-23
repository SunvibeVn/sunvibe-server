package vn.sunvibe.acquisition.core.domain.model.lead;

import java.util.Optional;

/**
 * What a requirement change did to a lead (survey-schedule US-5, BR-10): the intent and asking price before and
 * after, the intent cycle it started and the history entry to record.
 */
public record IntentChange(OwnerLeadIntent fromIntent, Optional<AskingPrice> fromPrice, OwnerLeadIntent toIntent,
                           Optional<AskingPrice> toPrice, int newIntentCycle, LeadChange change) {
}
