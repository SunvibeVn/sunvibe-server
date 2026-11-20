package vn.sunvibe.listing.core.application.query;

import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;

/**
 * What a terms snapshot can be taken from now (listing-publish US-2 AC-2; E06-S2 spec E06S2-D14, plan E06S2-P4,
 * E06S2-P10): whether the agreement ended, and E05's version in effect and its activated version waiting to start, each
 * present only when E05 has it, as the snapshot would copy them. E05's terms are private (brokerage-agreement BR-17).
 *
 * @param ended          the listing's agreement ended: no source is answered
 * @param inEffectOrNull the terms of the version in effect
 * @param pendingOrNull  the terms of the activated version waiting to start, with its start date
 */
public record TermsSourcesView(boolean ended, RevisionTerms inEffectOrNull, RevisionTerms pendingOrNull) {

    @Override
    public String toString() {
        return "TermsSourcesView[hidden]";
    }
}
