package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.property.core.application.exception.IntakeError;
import vn.sunvibe.property.core.application.exception.IntakeRefusedException;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeHistoryEntry;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLead;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLeadPort;
import vn.sunvibe.property.core.application.shared.external.survey.IntakeSource;
import vn.sunvibe.property.core.application.shared.external.user.UserDirectoryPort;
import vn.sunvibe.property.core.application.shared.lock.WardCreateLockPort;
import vn.sunvibe.property.core.application.usecase.intake.IntakeActor;
import vn.sunvibe.property.core.application.usecase.intake.IntakeCompletionUseCase;
import vn.sunvibe.property.core.domain.exception.PropertyAlreadyExistsException;
import vn.sunvibe.property.core.domain.exception.UnitCodeTakenException;
import vn.sunvibe.property.core.domain.model.property.IntakeProperty;
import vn.sunvibe.property.core.domain.model.property.Property;
import vn.sunvibe.property.core.domain.model.property.enums.AssetType;
import vn.sunvibe.property.core.domain.model.intake.AvailabilityItem;
import vn.sunvibe.property.core.domain.model.intake.IntakeStatus;
import vn.sunvibe.property.core.domain.model.intake.IntakeTarget;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.model.intake.SelectedSpace;
import vn.sunvibe.property.core.domain.model.intake.TargetMode;
import vn.sunvibe.property.core.domain.model.intake.UnitMapping;
import vn.sunvibe.property.core.domain.model.ownership.PropertyOwnership;
import vn.sunvibe.property.core.domain.model.unit.Unit;
import vn.sunvibe.property.core.domain.model.property.vo.IntakePropertyCode;
import vn.sunvibe.property.core.domain.model.property.vo.Location;
import vn.sunvibe.property.core.domain.model.property.vo.PropertyCode;
import vn.sunvibe.property.core.domain.repository.IntakeUnitMappingDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryIntakeDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository.PropertyFacts;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository.UnitFacts;
import vn.sunvibe.property.core.domain.repository.PropertyDomainRepository;
import vn.sunvibe.property.core.domain.repository.PropertyOwnershipDomainRepository;
import vn.sunvibe.property.core.domain.repository.UnitDomainRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Use Case Implementation - completion (property-create intake spec 5.3, S-12..S-18). No surrounding transaction on purpose: a
 * duplicate found here commits its case and CASE_OPEN, and only then answers 409; and the answer is built after the commit. Any
 * other failure stores nothing. It reuses nothing of the legacy create, because an intake property has no legacy columns.
 */
@Service
@RequiredArgsConstructor
public class IntakeCompletionService implements IntakeCompletionUseCase {

    private final TransactionTemplate transaction;
    private final IntakeCommandSupport support;
    private final IntakePreviewBuilder builder;
    private final IntakeLinkRules linkRules;
    private final IntakeDuplicateGate gate;
    private final WardCreateLockPort wardLock;
    private final IntakeLeadPort leads;
    private final UserDirectoryPort users;
    private final InventoryIntakeDomainRepository intakes;
    private final InventoryReadRepository inventory;
    private final IntakeUnitMappingDomainRepository mappings;
    private final PropertyDomainRepository properties;
    private final UnitDomainRepository units;
    private final PropertyOwnershipDomainRepository ownerships;

    @Override
    public CompletionResult complete(String leadId, IntakeActor actor, Long expectedVersion, String previewHash) {
        Outcome outcome;
        try {
            outcome = transaction.execute(status -> completeLocked(leadId, actor, expectedVersion, previewHash));
        } catch (PropertyAlreadyExistsException e) {
            // A rollback left the number behind; the retry takes the next one
            throw new IntakeRefusedException(IntakeError.CONCURRENT_MODIFICATION);
        } catch (UnitCodeTakenException e) {
            throw new IntakeRefusedException(IntakeError.INTAKE_SOURCE_INVALID);
        }
        if (outcome.held()) {
            throw new IntakeRefusedException(IntakeError.INTAKE_DUPLICATE_CHECK);
        }
        return result(outcome.intakeId());
    }

    private Outcome completeLocked(String leadId, IntakeActor actor, Long expectedVersion, String previewHash) {
        Instant now = IntakeCommandSupport.now();
        IntakeLead lead = support.lockVisibleLead(leadId, actor);
        support.requireAssignee(lead, actor);
        IntakeState state = support.lockedState(lead, now);
        Optional<String> repeat = repeat(state, previewHash);
        if (repeat.isPresent()) {
            return new Outcome(repeat.get(), false);
        }
        requireWork(state);
        support.requireGate(state);
        support.requireCheckedRow(state);
        IntakeSource source = state.source().orElseThrow(() -> new IntakeRefusedException(IntakeError.INTAKE_STATE));
        IntakePreviewBuilder.requireStorable(source);
        String owner = state.proofOrNull().getUserId();
        if (!users.isEligibleOwner(owner)) {
            throw new IntakeRefusedException(IntakeError.OWNER_PROOF_REQUIRED);
        }
        requireContactToday(state.currentOrNull(), IntakeCommandSupport.today(now));
        support.requireVersion(state, expectedVersion);
        String hash = builder.hash(state, source);
        if (!hash.equals(previewHash)) {
            throw new IntakeRefusedException(IntakeError.INTAKE_PREVIEW_STALE);
        }
        IntakeTarget target = state.currentOrNull().getTarget();
        if (target.mode() == TargetMode.CREATE && (state.forcedPropertyId().isPresent() || state.hasEarlierResult())) {
            throw new IntakeRefusedException(IntakeError.INTAKE_CREATE_NOT_ALLOWED);
        }
        if (target.mode() == TargetMode.LINK) {
            linkRules.require(state, state.currentOrNull(), source, target.propertyIdOrNull(), target.mappings());
        }
        Map<String, Long> versions = builder.versions(target);

        InventoryIntake row = support.settle(state, now);
        if (signalHolds(row, target, source, now)) {
            row.openCase(now);
            intakes.update(row);
            return new Outcome(row.getId(), true);
        }
        Written written = target.mode() == TargetMode.CREATE ? create(row, source, owner, actor) : linked(row, target, source);
        mappings.saveAll(written.mappings());
        row.complete(written.propertyId(), state.proofOrNull().getId(), versions, hash, actor.userId(), now);
        intakes.update(row);
        support.event(row.getId(), actor, IntakeEventAction.COMPLETION, now);
        leads.recordActivity(leadId, new IntakeHistoryEntry(IntakeHistoryEntry.Type.INVENTORY_INTAKE_COMPLETED, actor.userId(),
                row.getId(), row.getIntakeNo(), now));
        return new Outcome(row.getId(), false);
    }

    /** spec 5.3: a COMPLETED row with an equal hash answers the stored result, another hash 409. An open row goes on. */
    private static Optional<String> repeat(IntakeState state, String previewHash) {
        InventoryIntake current = state.currentOrNull();
        InventoryIntake result = state.latestCompletedOrNull();
        if (result == null || (current != null && current.getStatus().isOpen())) {
            return Optional.empty();
        }
        if (!result.getPreviewHash().equals(previewHash)) {
            throw new IntakeRefusedException(IntakeError.INTAKE_ALREADY_COMPLETED);
        }
        return Optional.of(result.getId());
    }

    /** spec 5.3: no target or availability, a target a decision cleared included, answers INTAKE_STATE. */
    private static void requireWork(IntakeState state) {
        InventoryIntake row = state.currentOrNull();
        boolean confirmed = state.effectiveOrNull() == IntakeStatus.OWNER_CONFIRMED;
        if (state.effectiveOrNull() == IntakeStatus.CASE_OPEN
                || (confirmed && (row.getTarget() == null || row.getAvailability().isEmpty()))) {
            throw new IntakeRefusedException(IntakeError.INTAKE_STATE);
        }
    }

    private static void requireContactToday(InventoryIntake row, LocalDate today) {
        if (row.getAvailability().stream().anyMatch(item -> !today.equals(item.contactedOn()))) {
            throw new IntakeRefusedException(IntakeError.AVAILABILITY_NOT_CONFIRMED);
        }
    }

    /** spec 5.3: the ward lock comes first, and a miss is a conflict; a hit is a normal branch before any inventory write. */
    private boolean signalHolds(InventoryIntake row, IntakeTarget target, IntakeSource source, Instant now) {
        if (target.mode() == TargetMode.LINK) {
            return gate.holdsLink(row, target.propertyIdOrNull(), now);
        }
        if (!wardLock.tryLock(row.getSource().wardCode())) {
            throw new IntakeRefusedException(IntakeError.CONCURRENT_MODIFICATION);
        }
        return gate.holdsCreate(row, source.wardName(), now);
    }

    private Written create(InventoryIntake row, IntakeSource source, String owner, IntakeActor actor) {
        AssetType type = row.getAssetType();
        PropertyCode code = IntakePropertyCode.of(type, inventory.nextIntakeCodeNumber());
        Location location = Location.of(source.addressText(), source.wardName(), null, source.provinceName(), null,
                row.getSource().latitude(), row.getSource().longitude());
        Property property = Property.createFromIntake(new IntakeProperty(code, type.getLabel() + ", " + source.wardName(), location,
                type, row.getRentalScope(), row.getId(), source.wardCode(), row.getSource().addressNorm(), owner, actor.userId()));
        properties.save(property);

        Map<String, IntakeSource.Space> spaces = spaces(source);
        Map<String, AvailabilityItem> availability = row.getAvailability().stream()
                .collect(Collectors.toMap(AvailabilityItem::spaceId, Function.identity()));
        String canonical = row.getTarget().canonicalSpaceIdOrNull();
        List<UnitMapping> written = new ArrayList<>();
        for (SelectedSpace selected : row.getSelection()) {
            IntakeSource.Space space = spaces.get(selected.spaceId());
            boolean isCanonical = canonical == null || canonical.equals(space.spaceId());
            Unit unit = unit(property, space, isCanonical ? code.getValue() : space.code().strip(), availability.get(space.spaceId()));
            units.save(unit);
            written.add(new UnitMapping(row.getId(), space.spaceId(), space.code(), unit.getId().getValue(), isCanonical));
        }
        ownerships.save(PropertyOwnership.createUnverifiedOwner(property.getId(), owner, property.getCreatedAt().toLocalDate()));
        wardLock.boundOwnerRowWait();
        if (!users.ensureOwnerRole(owner, actor.userId())) {
            throw new IntakeRefusedException(IntakeError.OWNER_PROOF_REQUIRED);
        }
        return new Written(property.getId().getValue(), written);
    }

    /** G3 of this space alone (D5-20); a usable PASSED holds it, and a row without it cannot make a unit. */
    private static Unit unit(Property property, IntakeSource.Space space, String unitCode, AvailabilityItem availability) {
        if (space.bedrooms() == null || space.privateBathrooms() == null || space.areaSqm() == null) {
            throw new IntakeRefusedException(IntakeError.INTAKE_SOURCE_INVALID);
        }
        String label = space.location() == null ? space.code() : space.code() + " · " + space.location();
        return Unit.createFromIntake(property.getId(), unitCode, label, space.bedrooms(), space.privateBathrooms(),
                space.areaSqm().doubleValue(), availability.state().unitState());
    }

    /** spec 4.9: a link writes mappings and the result only (US-6 AC-2). */
    private static Written linked(InventoryIntake row, IntakeTarget target, IntakeSource source) {
        Map<String, IntakeSource.Space> spaces = spaces(source);
        return new Written(target.propertyIdOrNull(), target.mappings().stream()
                .map(mapping -> new UnitMapping(row.getId(), mapping.spaceId(), spaces.get(mapping.spaceId()).code(),
                        mapping.unitId(), false)).toList());
    }

    private static Map<String, IntakeSource.Space> spaces(IntakeSource source) {
        return source.spaces().stream().collect(Collectors.toMap(IntakeSource.Space::spaceId, Function.identity()));
    }

    /** Built after the commit, from what the rows hold, so a repeat reads the same. */
    private CompletionResult result(String intakeId) {
        InventoryIntake row = intakes.findById(intakeId).orElseThrow();
        PropertyFacts property = inventory.property(row.getResultPropertyId()).orElseThrow();
        List<UnitMapping> rows = mappings.findByIntakeId(intakeId);
        Map<String, UnitFacts> byId = inventory.units(rows.stream().map(UnitMapping::unitId).toList()).stream()
                .collect(Collectors.toMap(UnitFacts::id, Function.identity()));
        List<ResultUnit> resultUnits = rows.stream()
                .map(mapping -> new ResultUnit(mapping.unitId(), byId.get(mapping.unitId()).code())).toList();
        return new CompletionResult(property.id(), property.code(), resultUnits, row.getTarget().mode().name(), row.getCompletedAt());
    }

    private record Outcome(String intakeId, boolean held) {
    }

    private record Written(String propertyId, List<UnitMapping> mappings) {
    }
}
