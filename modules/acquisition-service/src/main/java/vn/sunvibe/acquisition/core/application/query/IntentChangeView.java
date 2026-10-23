package vn.sunvibe.acquisition.core.application.query;

import vn.sunvibe.acquisition.core.domain.model.lead.ConfirmationBasis;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntentChange;

import java.time.Instant;
import java.util.Map;

/**
 * One requirement change of a lead as staff see it (survey-schedule spec 7.2): old and new intent and price (null when
 * none), why, on what basis, who and when, and the intent cycle it started. The actor's name is null when unknown.
 */
public record IntentChangeView(OwnerLeadIntent fromIntent, OwnerLeadIntent toIntent,
                               OwnerLeadDetail.AskingPriceView fromAskingPrice,
                               OwnerLeadDetail.AskingPriceView toAskingPrice, String reason, ConfirmationBasis basis,
                               String actorUserId, String actorName, int newIntentCycle, Instant occurredAt) {

    /** The view of a change; {@code names} maps staff ids to full names and may lack any of them. */
    public static IntentChangeView of(OwnerLeadIntentChange change, Map<String, String> names) {
        return new IntentChangeView(change.getFromIntent(), change.getToIntent(),
                change.getFromPrice().map(price -> new OwnerLeadDetail.AskingPriceView(price.getAmount(),
                        price.getCurrency(), price.getUnit())).orElse(null),
                change.getToPrice().map(price -> new OwnerLeadDetail.AskingPriceView(price.getAmount(),
                        price.getCurrency(), price.getUnit())).orElse(null),
                change.getReason(), change.getBasis(), change.getActorUserId(), names.get(change.getActorUserId()),
                change.getNewIntentCycle(), change.getOccurredAt());
    }

    /** Reason and basis are staff text: never print them. */
    @Override
    public String toString() {
        return "IntentChangeView[" + fromIntent + " -> " + toIntent + "]";
    }
}
