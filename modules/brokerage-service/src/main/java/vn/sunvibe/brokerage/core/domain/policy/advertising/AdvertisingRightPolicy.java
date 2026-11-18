package vn.sunvibe.brokerage.core.domain.policy.advertising;

import vn.sunvibe.brokerage.core.domain.model.advertising.AdvertisingReasonGroup;
import vn.sunvibe.brokerage.core.domain.model.advertising.AdvertisingRight;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementVersion;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionStatus;
import vn.sunvibe.brokerage.core.domain.model.eligibility.ReasonGroup;
import vn.sunvibe.brokerage.core.domain.policy.eligibility.EligibilityPolicy;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Whether Sunvibe may advertise a unit now under one agreement (brokerage-agreement BR-12, BR-13, US-4 AC-1, AC-2;
 * E05-S6 spec S6-D7). The agreement is read as it stands at {@code now} ({@link BrokerageAgreement#effectiveAt}); the
 * version that counts is its EFFECTIVE version with the highest number, else its ACTIVATED one. The right is on exactly
 * when no group stops it: the agreement is in effect and not suspended, {@code now} is before the end of the version's
 * end date, its basis is usable with no BR-3 stop, and its owner is eligible (BR-4).
 */
public final class AdvertisingRightPolicy {

    private AdvertisingRightPolicy() {
    }

    /**
     * The facts of the version that counts, read from other modules.
     *
     * @param basisEntryOrNull      the version's basis intake as an entry of the unit in read 1, with its state and the
     *                              unit's reasons; null when it is no entry of the unit (E05-S6b S6b-D1)
     * @param owner                 the property's owner data of read 1b with the declared owner's account eligibility
     * @param declaredOwnerIdOrNull the property's declared owner now; null when it has none
     */
    public record ConditionFacts(EligibilityPolicy.Basis basisEntryOrNull, EligibilityPolicy.Owner owner,
                                 String declaredOwnerIdOrNull) {
    }

    /**
     * The answer for the agreement's unit at {@code now}.
     *
     * @param effective  the agreement as it stands at {@code now}
     * @param factsOrNull the facts of the version that counts; read only when {@link #needsConditions} holds
     * @throws IllegalArgumentException when the conditions are needed and no facts are given
     */
    public static AdvertisingRight answer(BrokerageAgreement effective, Instant now, ConditionFacts factsOrNull) {
        AgreementVersion counts = versionThatCounts(effective);
        boolean ended = ended(effective, now);
        Set<AdvertisingReasonGroup> groups = EnumSet.noneOf(AdvertisingReasonGroup.class);
        AgreementVersion inEffect = counts != null && counts.getStatus() == VersionStatus.EFFECTIVE ? counts : null;
        AgreementVersion pending = effective.getVersions().stream()
                .filter(version -> version.getStatus() == VersionStatus.ACTIVATED).findFirst().orElse(null);
        if (ended) {
            groups.add(AdvertisingReasonGroup.ENDED);
        } else if (!isInEffectOrSuspended(effective.getStatus()) || counts == null) {
            // brokerage-agreement BR-13: drafting or awaiting confirmation; an agreement in effect without a version
            // that counts is no stored state of the commands and reads as not in effect (fail closed).
            groups.add(AdvertisingReasonGroup.NOT_IN_EFFECT);
        } else {
            if (effective.getStatus() == AgreementStatus.SUSPENDED) {
                groups.add(AdvertisingReasonGroup.SUSPENDED);
            } else if (inEffect == null) {
                // listing-publish section 7: off only because the activated version has not started
                groups.add(AdvertisingReasonGroup.NOT_STARTED);
            }
            if (factsOrNull == null) {
                throw new IllegalArgumentException("The conditions of an agreement in effect need their facts.");
            }
            groups.addAll(conditionGroups(effective, factsOrNull));
        }
        boolean allowed = groups.isEmpty();
        Boolean interior = counts == null ? null : counts.getPhotoConsent().interiorOrNull();
        return new AdvertisingRight(allowed, List.copyOf(groups),
                counts == null ? null : counts.getPeriod().endDate(),
                counts == null ? null : counts.endsAt(),
                allowed && Boolean.TRUE.equals(interior),
                allowed && counts.getPhotoConsent().exterior(),
                inEffect == null || ended ? null : inEffect.getTerms(),
                pending == null || ended ? null : pending.getPeriod().startDate(),
                pending == null || ended ? null : pending.startsAt(),
                pending == null || ended ? null : pending.getTerms(),
                ended);
    }

    /**
     * Whether the agreement has ended at {@code now} (E05-S6 spec S6-D10, S6-D20; PLAN6-D1): a final state (EXPIRED,
     * TERMINATED, CANCELLED), or ACTIVE or SUSPENDED from the end of the end date of its version that counts
     * (brokerage-agreement BR-6: the right ends at the end of the end date, no grace).
     */
    public static boolean ended(BrokerageAgreement effective, Instant now) {
        AgreementStatus status = effective.getStatus();
        if (status == AgreementStatus.EXPIRED || status == AgreementStatus.TERMINATED
                || status == AgreementStatus.CANCELLED) {
            return true;
        }
        AgreementVersion counts = versionThatCounts(effective);
        return isInEffectOrSuspended(status) && counts != null && !now.isBefore(counts.endsAt());
    }

    /**
     * Whether the answer needs the facts of other modules: only an ACTIVE or SUSPENDED agreement with a version that
     * counts that has not ended (E05-S6 spec S6-D8, S6-D9); every other answer is decided by the agreement alone.
     */
    public static boolean needsConditions(BrokerageAgreement effective, Instant now) {
        return isInEffectOrSuspended(effective.getStatus()) && versionThatCounts(effective) != null
                && !ended(effective, now);
    }

    /**
     * E05-S6 spec S6-D7: BASIS alone when the version's basis is no entry of the unit (BR-2, as the commands' rerun
     * answers a basis that is not the lead's latest); else E05-S1's groups on that entry and the owner data, mapped one
     * to one, plus OWNER when the agreement's owner is no longer the declared owner (BR-1, BR-4).
     */
    private static Set<AdvertisingReasonGroup> conditionGroups(BrokerageAgreement agreement, ConditionFacts facts) {
        Set<AdvertisingReasonGroup> groups = EnumSet.noneOf(AdvertisingReasonGroup.class);
        if (facts.basisEntryOrNull() == null) {
            groups.add(AdvertisingReasonGroup.BASIS);
            return groups;
        }
        for (ReasonGroup group : EligibilityPolicy.reasonGroups(agreement.getUnitId(), facts.basisEntryOrNull(),
                facts.owner())) {
            groups.add(AdvertisingReasonGroup.valueOf(group.name()));
        }
        if (!agreement.getOwnerUserId().equals(facts.declaredOwnerIdOrNull())) {
            groups.add(AdvertisingReasonGroup.OWNER);
        }
        return groups;
    }

    /**
     * The version whose facts and dates the answer reads (E05-S6 spec S6-D7): the EFFECTIVE version with the highest
     * number, else the ACTIVATED one; null when neither exists.
     */
    public static AgreementVersion versionThatCountsOrNull(BrokerageAgreement effective) {
        return versionThatCounts(effective);
    }

    /** The EFFECTIVE version with the highest number, else the ACTIVATED one; null when neither exists. */
    private static AgreementVersion versionThatCounts(BrokerageAgreement agreement) {
        return agreement.getVersions().stream()
                .filter(version -> version.getStatus() == VersionStatus.EFFECTIVE)
                .max(Comparator.comparingInt(AgreementVersion::getVersionNo))
                .or(() -> agreement.getVersions().stream()
                        .filter(version -> version.getStatus() == VersionStatus.ACTIVATED).findFirst())
                .orElse(null);
    }

    private static boolean isInEffectOrSuspended(AgreementStatus status) {
        return status == AgreementStatus.ACTIVE || status == AgreementStatus.SUSPENDED;
    }
}
