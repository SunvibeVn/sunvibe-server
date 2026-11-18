package vn.sunvibe.brokerage.core.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.brokerage.core.application.shared.external.account.OwnerEligibilityPort;
import vn.sunvibe.brokerage.core.application.shared.external.property.PropertyOwnerFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.UnitBasisFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.UnitBasisPort;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightView;
import vn.sunvibe.brokerage.core.domain.model.advertising.AdvertisingRight;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementVersion;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.MandatoryFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.TermsDeposit;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionTerms;
import vn.sunvibe.brokerage.core.domain.policy.advertising.AdvertisingRightPolicy;
import vn.sunvibe.brokerage.core.domain.policy.advertising.AdvertisingRightPolicy.ConditionFacts;
import vn.sunvibe.brokerage.core.domain.policy.eligibility.EligibilityPolicy;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Composes the advertising answer of a set of agreements from their effective state and the facts of other modules
 * (E05-S6 spec S6-D7, S6-D9): one read 1 call for the units of the agreements in effect, one read 1b call for their
 * properties and one owner-eligibility call for the declared owners, whatever the count, and none at all when no
 * agreement is in effect (an answer of any other state needs no fact). The staff detail and the published set answer
 * both use it, so the two forms run one code path. A failing port's exception reaches the caller: no answer is made
 * without its facts (ADR-012, "an error of a port fails closed").
 */
@Component
@RequiredArgsConstructor
public class AdvertisingRightAssembler {

    private final UnitBasisPort bases;
    private final OwnerEligibilityPort owners;

    /**
     * The answer of each agreement, in the order given; each agreement is read as it stands at {@code now}
     * ({@link BrokerageAgreement#effectiveAt}).
     */
    public List<AdvertisingRightView> answers(List<BrokerageAgreement> effective, Instant now) {
        List<BrokerageAgreement> inEffect = effective.stream()
                .filter(agreement -> AdvertisingRightPolicy.needsConditions(agreement, now)).toList();
        Map<String, UnitBasisFacts> units = new HashMap<>();
        Map<String, PropertyOwnerFacts> properties = new HashMap<>();
        Set<String> eligible = Set.of();
        if (!inEffect.isEmpty()) {
            bases.forUnits(inEffect.stream().map(BrokerageAgreement::getUnitId).distinct().toList())
                    .forEach(unit -> units.put(unit.unitId(), unit));
            List<String> propertyIds = units.values().stream().map(UnitBasisFacts::propertyId).distinct().toList();
            if (!propertyIds.isEmpty()) {
                bases.ownersOf(propertyIds).forEach(property -> properties.put(property.propertyId(), property));
            }
            List<String> declared = properties.values().stream().map(PropertyOwnerFacts::declaredOwnerIdOrNull)
                    .filter(Objects::nonNull).distinct().toList();
            if (!declared.isEmpty()) {
                eligible = owners.eligibleOwners(declared);
            }
        }
        Set<String> eligibleOwners = eligible;
        return effective.stream().map(agreement -> {
            ConditionFacts facts = AdvertisingRightPolicy.needsConditions(agreement, now)
                    ? conditionFacts(agreement, units, properties, eligibleOwners) : null;
            return view(agreement, AdvertisingRightPolicy.answer(agreement, now, facts));
        }).toList();
    }

    /**
     * The facts of the version that counts: its basis intake as an entry of the unit in read 1 (none when the intake is
     * no longer an entry, E05-S6b S6b-D1), the property's owner data of read 1b and the declared owner's eligibility.
     */
    private static ConditionFacts conditionFacts(BrokerageAgreement agreement, Map<String, UnitBasisFacts> units,
                                                 Map<String, PropertyOwnerFacts> properties, Set<String> eligible) {
        AgreementVersion counts = AdvertisingRightPolicy.versionThatCountsOrNull(agreement);
        UnitBasisFacts unit = units.get(agreement.getUnitId());
        UnitBasisFacts.Entry entry = unit == null || counts == null ? null : unit.entries().stream()
                .filter(candidate -> candidate.intakeId().equals(counts.getBasisIntakeId())).findFirst().orElse(null);
        EligibilityPolicy.Basis basisEntry = entry == null ? null : new EligibilityPolicy.Basis(entry.state(),
                List.of(new EligibilityPolicy.BasisUnit(agreement.getUnitId(), entry.unitReasons())));
        PropertyOwnerFacts property = unit == null ? null : properties.get(unit.propertyId());
        String declared = property == null ? null : property.declaredOwnerIdOrNull();
        EligibilityPolicy.Owner owner = new EligibilityPolicy.Owner(declared, property == null ? List.of()
                : property.holdings().stream().map(holding -> new EligibilityPolicy.Holding(holding.userId(),
                        holding.relation(), holding.verification())).toList(),
                declared != null && eligible.contains(declared));
        return new ConditionFacts(basisEntry, owner, declared);
    }

    private static AdvertisingRightView view(BrokerageAgreement agreement, AdvertisingRight right) {
        return new AdvertisingRightView(agreement.getUnitId(), agreement.getId(), right.ended(), right.allowed(),
                right.groups().stream().map(Enum::name).toList(), right.endDateOrNull(), right.endsAtOrNull(),
                right.interiorPhotos(), right.exteriorPhotos(), terms(right.termsInEffectOrNull()),
                right.pendingStartDateOrNull(), right.pendingStartsAtOrNull(), terms(right.pendingTermsOrNull()));
    }

    /** The rental terms as plain values and codes; null for none. */
    static AdvertisingRightView.Terms terms(VersionTerms termsOrNull) {
        if (termsOrNull == null) {
            return null;
        }
        TermsDeposit deposit = termsOrNull.depositOrNull();
        List<MandatoryFee> fees = termsOrNull.mandatoryFeesOrNull() == null ? List.of()
                : termsOrNull.mandatoryFeesOrNull();
        return new AdvertisingRightView.Terms(termsOrNull.monthlyRentOrNull(),
                deposit == null ? null : new AdvertisingRightView.Deposit(deposit.basis().name(),
                        deposit.monthsOrNull(), deposit.amountOrNull()),
                termsOrNull.minimumTermMonthsOrNull(), termsOrNull.paymentPeriodMonthsOrNull(),
                fees.stream().map(fee -> new AdvertisingRightView.MandatoryFee(fee.name(), fee.basis().name(),
                        fee.amountOrNull(), fee.chargeUnitOrNull() == null ? null : fee.chargeUnitOrNull().name()))
                        .toList());
    }
}
