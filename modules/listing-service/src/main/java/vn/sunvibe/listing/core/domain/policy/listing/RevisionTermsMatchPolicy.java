package vn.sunvibe.listing.core.domain.policy.listing;

import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;
import vn.sunvibe.listing.core.domain.model.listing.TermsMatch;

import java.util.Objects;

/**
 * Which agreement version a revision's terms snapshot equals (listing-publish BR-4, US-4 AC-5, E-5; E06-S4 spec
 * E06S4-D7): compared by value on the monthly rent, the deposit (basis with its months or amount), the minimum term, the
 * payment period and the mandatory fees in E05's order (each by name, basis, amount and charge unit). The snapshot's
 * source, pending start date and capture time are not compared, so a snapshot taken from the waiting version still
 * equals that version once it is in effect. Pure: the caller reads E05's terms and converts them first.
 */
public final class RevisionTermsMatchPolicy {

    private RevisionTermsMatchPolicy() {
    }

    /**
     * {@code NO_SNAPSHOT} without a snapshot; {@code IN_EFFECT} when it equals the terms in effect, else
     * {@code PENDING_START} when it equals the waiting version, else {@code NONE} (also when E05 answers no terms).
     */
    public static TermsMatch match(RevisionTerms snapshotOrNull, RevisionTerms inEffectOrNull,
                                   RevisionTerms pendingOrNull) {
        if (snapshotOrNull == null) {
            return TermsMatch.NO_SNAPSHOT;
        }
        if (sameValues(snapshotOrNull, inEffectOrNull)) {
            return TermsMatch.IN_EFFECT;
        }
        if (sameValues(snapshotOrNull, pendingOrNull)) {
            return TermsMatch.PENDING_START;
        }
        return TermsMatch.NONE;
    }

    private static boolean sameValues(RevisionTerms snapshot, RevisionTerms versionOrNull) {
        return versionOrNull != null
                && snapshot.monthlyRent() == versionOrNull.monthlyRent()
                && snapshot.deposit().equals(versionOrNull.deposit())
                && snapshot.minimumTermMonths() == versionOrNull.minimumTermMonths()
                && snapshot.paymentPeriodMonths() == versionOrNull.paymentPeriodMonths()
                && Objects.equals(snapshot.fees(), versionOrNull.fees());
    }
}
