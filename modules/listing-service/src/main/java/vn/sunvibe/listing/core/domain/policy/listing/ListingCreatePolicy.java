package vn.sunvibe.listing.core.domain.policy.listing;

import vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup;

import java.util.List;

/**
 * Whether a listing may be created under an agreement now, and which of E05's reason groups stop it (listing-publish
 * BR-1, LP-1: created when E05's answer for the unit allows advertising, or is off only because an activated version
 * has not started; otherwise refused with E05's reason groups). Pure: the services read the facts and pass them here.
 */
public final class ListingCreatePolicy {

    private ListingCreatePolicy() {
    }

    /**
     * E05's answer for the pair (unit, agreement), in listing's domain values.
     *
     * @param ended   the agreement reached a final state or the end of its end date
     * @param allowed advertising is allowed now (E05: exactly when the groups are empty)
     * @param groups  the groups that stop advertising, in E05's order
     */
    public record AnswerFacts(boolean ended, boolean allowed, List<AgreementReasonGroup> groups) {

        public AnswerFacts {
            groups = List.copyOf(groups);
        }
    }

    /**
     * The groups that stop a create, empty when it may be created: {@code [ENDED]} when the agreement's standing ended
     * (the answer is then not needed and may be null); else {@code [NO_AGREEMENT]} when E05 gave no answer; else
     * {@code [ENDED]} when the answer ended; else none when the answer allows advertising or its groups are exactly
     * {@code [NOT_STARTED]} (listing-publish LP-1: the first listing may be prepared before the start); else E05's groups
     * in E05's order.
     */
    public static List<AgreementReasonGroup> refusals(boolean standingEnded, AnswerFacts answerOrNull) {
        if (standingEnded) {
            return List.of(AgreementReasonGroup.ENDED);
        }
        if (answerOrNull == null) {
            return List.of(AgreementReasonGroup.NO_AGREEMENT);
        }
        if (answerOrNull.ended()) {
            return List.of(AgreementReasonGroup.ENDED);
        }
        if (answerOrNull.allowed() || answerOrNull.groups().equals(List.of(AgreementReasonGroup.NOT_STARTED))) {
            return List.of();
        }
        return answerOrNull.groups();
    }
}
