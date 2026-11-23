package vn.sunvibe.listing.core.application.service;

import org.springframework.stereotype.Component;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingAnswerFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingReviewFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingRightPort;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementTermsFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitAvailabilityFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitInventoryPort;
import vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup;
import vn.sunvibe.listing.core.domain.model.listing.DisplayConditions;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessSummary;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;
import vn.sunvibe.listing.core.domain.model.listing.UnitAvailability;
import vn.sunvibe.listing.core.domain.repository.ListingDomainRepository;
import vn.sunvibe.listing.core.domain.repository.ListingRow;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Composes the display facts of a listing (listing-publish E06-S6 spec E06S6-D4; ADR-012 decision 2, read 1) from E05,
 * property and the listing's freshness summary, in its caller's transaction (no {@code @Transactional} of its own). For
 * a command it reads E05's answer under the share lock of the agreement row first, after the caller locked the listing
 * row (lock order: the listing row, then the agreement row), then E05's terms, then property's availability, then the
 * freshness summary; for a read, E05's review answer without a lock. E05's reason groups are read by code and an
 * unknown one fails; E05's terms in effect go through the one conversion of E06S6-D20; no E05 answer reads as not
 * allowed with {@code NO_AGREEMENT}; a unit property does not know has no availability, and an availability property
 * sends that listing does not know fails. Nothing of what it reads is logged.
 */
@Component
public class DisplayConditionsAssembler {

    private final AdvertisingRightPort rights;
    private final UnitInventoryPort units;
    private final ListingDomainRepository listings;

    public DisplayConditionsAssembler(AdvertisingRightPort rights, UnitInventoryPort units,
                                      ListingDomainRepository listings) {
        this.rights = rights;
        this.units = units;
        this.listings = listings;
    }

    /**
     * The facts of a command, E05 read under its share lock (E06S6-D5 step (9)): the answer, the terms, the unit and
     * the stored freshness summary, in that order.
     *
     * @throws IllegalStateException for an E05 group, availability code or terms listing does not know
     */
    public DisplayConditions underShareLock(ListingRow row, ListingState state, Instant now) {
        Advertising advertising = lockedAdvertising(row, now);
        Unit unit = unitOf(row.unitId());
        FreshnessSummary freshness = listings.findFreshnessSummary(row.listingId());
        return conditions(state, advertising, unit, freshness);
    }

    /**
     * The facts of a valid freshness record that tries the put live (E06S6-D7): as {@link #underShareLock(ListingRow,
     * ListingState, Instant)}, with the summary the record moves to in place of the stored one, which it does not read.
     */
    public DisplayConditions underShareLock(ListingRow row, ListingState state, FreshnessSummary freshness,
                                            Instant now) {
        Advertising advertising = lockedAdvertising(row, now);
        Unit unit = unitOf(row.unitId());
        return conditions(state, advertising, unit, freshness);
    }

    /**
     * The facts of a read (E06S6-D11): E05's review answer without a lock, its groups and its terms in effect, then the
     * unit and the freshness summary.
     */
    public DisplayConditions withoutLock(ListingRow row, ListingState state, Instant now) {
        Optional<AdvertisingReviewFacts> review = rights.review(row.unitId(), row.agreementId());
        Advertising advertising = review
                .map(facts -> new Advertising(facts.ended(), facts.allowed(), groupsOf(facts.reasonGroups()),
                        ListingCommandService.inEffectSnapshotOrNull(facts.inEffectOrNull(), now)))
                .orElse(Advertising.NONE);
        Unit unit = unitOf(row.unitId());
        FreshnessSummary freshness = listings.findFreshnessSummary(row.listingId());
        return conditions(state, advertising, unit, freshness);
    }

    private Advertising lockedAdvertising(ListingRow row, Instant now) {
        Optional<AdvertisingAnswerFacts> answer = rights.answerUnderShareLock(row.unitId(), row.agreementId());
        Optional<AgreementTermsFacts> terms = rights.terms(row.unitId(), row.agreementId());
        RevisionTerms inEffect = terms.map(facts -> ListingCommandService.inEffectSnapshotOrNull(facts.inEffectOrNull(),
                now)).orElse(null);
        return answer
                .map(facts -> new Advertising(facts.ended(), facts.allowed(), groupsOf(facts.reasonGroups()), inEffect))
                .orElse(new Advertising(false, false, List.of(AgreementReasonGroup.NO_AGREEMENT), inEffect));
    }

    private Unit unitOf(String unitId) {
        return units.availability(List.of(unitId)).stream()
                .filter(facts -> unitId.equals(facts.unitId()))
                .findFirst()
                .map(DisplayConditionsAssembler::unitOf)
                .orElse(Unit.UNKNOWN);
    }

    private static Unit unitOf(UnitAvailabilityFacts facts) {
        return new Unit(UnitAvailability.ofCode(facts.availabilityOrNull()), facts.availabilityDiffers());
    }

    private static List<AgreementReasonGroup> groupsOf(List<String> codes) {
        return codes.stream().map(AgreementReasonGroup::ofCode).toList();
    }

    private static DisplayConditions conditions(ListingState state, Advertising advertising, Unit unit,
                                                FreshnessSummary freshness) {
        return new DisplayConditions(state, advertising.ended(), advertising.allowed(), advertising.groups(),
                advertising.termsInEffectOrNull(), unit.availabilityOrNull(), unit.differs(), freshness);
    }

    /** E05's part of the facts; no answer reads as not allowed with {@code NO_AGREEMENT}. */
    private record Advertising(boolean ended, boolean allowed, List<AgreementReasonGroup> groups,
                               RevisionTerms termsInEffectOrNull) {

        static final Advertising NONE = new Advertising(false, false, List.of(AgreementReasonGroup.NO_AGREEMENT),
                null);

        @Override
        public String toString() {
            return "Advertising[hidden]";
        }
    }

    /** property's part of the facts; a unit property does not know has no availability. */
    private record Unit(UnitAvailability availabilityOrNull, boolean differs) {

        static final Unit UNKNOWN = new Unit(null, false);
    }
}
