package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.application.exception.IntakeError;
import vn.sunvibe.property.core.application.exception.IntakeRefusedException;
import vn.sunvibe.property.core.application.shared.external.survey.IntakeSource;
import vn.sunvibe.property.core.domain.model.enums.RentalLayout;
import vn.sunvibe.property.core.domain.model.intake.CaseOutcome;
import vn.sunvibe.property.core.domain.model.intake.CaseStatus;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.model.intake.SelectedSpace;
import vn.sunvibe.property.core.domain.model.intake.SpaceUnitMapping;
import vn.sunvibe.property.core.domain.policy.AddressNormalizer;
import vn.sunvibe.property.core.domain.policy.intake.CasePolicy.CaseFacts;
import vn.sunvibe.property.core.domain.repository.IntakeSuspensionDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository.PropertyFacts;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository.UnitFacts;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The checks of a link, in the order of property-create intake spec 4.7, with one answer for unknown and foreign, so nobody learns
 * of a property that is not the account's. The target runs them, and completion runs them again.
 */
@Component
@RequiredArgsConstructor
public class IntakeLinkRules {

    private final InventoryReadRepository inventory;
    private final IntakeSuspensionDomainRepository suspensions;
    private final IntakeDisputes disputes;

    public void require(IntakeState state, InventoryIntake row, IntakeSource source, String propertyId,
                        List<SpaceUnitMapping> mappings) {
        String owner = state.proofOrNull().getUserId();
        PropertyFacts property = inventory.property(propertyId).filter(found -> inventory.ownsNow(found.id(), owner))
                .orElseThrow(IntakeLinkRules::invalid);
        requireSameWardAndType(property, source);
        requireOneToOne(row, property, mappings);
        requireLayout(property, source);
        if (suspensions.anyActiveOnUnits(mappings.stream().map(SpaceUnitMapping::unitId).toList())) {
            throw invalid();
        }
        requireReconciled(property, source);
        if (disputes.disputed(property.id())) {
            throw new IntakeRefusedException(IntakeError.INTAKE_DISPUTED);
        }
        requireBound(state, property);
        if (state.cases().stream().anyMatch(facts -> rulesOut(facts, property.id()))) {
            throw invalid();
        }
    }

    private static void requireSameWardAndType(PropertyFacts property, IntakeSource source) {
        boolean sameWard = property.wardCodeOrNull() != null ? property.wardCodeOrNull().equals(source.wardCode())
                : Objects.equals(AddressNormalizer.normalize(property.wardName()), AddressNormalizer.normalize(source.wardName()));
        boolean sameType = property.assetTypeOrNull() == null || property.assetTypeOrNull().equals(source.assetTypeOrNull());
        if (!sameWard || !sameType) {
            throw invalid();
        }
    }

    /** Every selected space onto one unit of this property, and no two onto the same one. */
    private void requireOneToOne(InventoryIntake row, PropertyFacts property, List<SpaceUnitMapping> mappings) {
        Set<String> selected = row.getSelection().stream().map(SelectedSpace::spaceId).collect(Collectors.toSet());
        Set<String> spaces = mappings.stream().map(SpaceUnitMapping::spaceId).collect(Collectors.toSet());
        Set<String> unitIds = mappings.stream().map(SpaceUnitMapping::unitId).collect(Collectors.toSet());
        List<UnitFacts> units = inventory.units(unitIds);
        boolean oneToOne = mappings.size() == selected.size() && spaces.equals(selected) && unitIds.size() == mappings.size();
        boolean ofProperty = units.size() == unitIds.size()
                && units.stream().allMatch(unit -> unit.propertyId().equals(property.id()));
        if (!oneToOne || !ofProperty) {
            throw invalid();
        }
    }

    /** spec 4.7: WHOLE not onto a PARTS property; PARTS not onto a property an intake created WHOLE. */
    private static void requireLayout(PropertyFacts property, IntakeSource source) {
        RentalLayout scope = IntakeEvaluator.scope(source);
        boolean wholeOntoParts = scope == RentalLayout.WHOLE && RentalLayout.PARTS.name().equals(property.layoutOrNull());
        boolean partsOntoWhole = scope == RentalLayout.PARTS && RentalLayout.WHOLE.name().equals(property.layoutOrNull())
                && property.intakeIdOrNull() != null;
        if (wholeOntoParts || partsOntoWhole) {
            throw invalid();
        }
    }

    /** E-6, property-create BR-13: the only unit of a legacy property carries the property's code, and that one cannot take a part. */
    private void requireReconciled(PropertyFacts property, IntakeSource source) {
        if (IntakeEvaluator.scope(source) != RentalLayout.PARTS || property.intakeIdOrNull() != null) {
            return;
        }
        List<UnitFacts> units = inventory.unitsOf(property.id());
        if (units.size() == 1 && units.getFirst().code().equals(property.code())) {
            throw new IntakeRefusedException(IntakeError.CANONICAL_UNRECONCILED);
        }
    }

    /** spec 4.10, D5-12: a forced property or an earlier result leaves one legal target. */
    private static void requireBound(IntakeState state, PropertyFacts property) {
        Optional<String> bound = state.forcedPropertyId().or(() -> state.hasEarlierResult()
                ? Optional.of(state.latestCompletedOrNull().getResultPropertyId()) : Optional.empty());
        if (bound.filter(id -> !id.equals(property.id())).isPresent()) {
            throw invalid();
        }
    }

    /** spec 4.7: a manager's DIFFERENT_ASSET ruling on this intake and scope binds. */
    private static boolean rulesOut(CaseFacts facts, String propertyId) {
        if (facts.row().getStatus() != CaseStatus.DECIDED || facts.row().getOutcome() != CaseOutcome.DIFFERENT_ASSET) {
            return false;
        }
        if (facts.row().onProperty()) {
            return propertyId.equals(facts.row().getCandidatePropertyId());
        }
        return Stream.of(facts.side(), facts.candidateOrNull()).anyMatch(side -> propertyId.equals(side.resultPropertyIdOrNull()));
    }

    private static IntakeRefusedException invalid() {
        return new IntakeRefusedException(IntakeError.LINK_MAPPING_INVALID);
    }
}
