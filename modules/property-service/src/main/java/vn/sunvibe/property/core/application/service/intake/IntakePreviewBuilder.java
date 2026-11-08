package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.application.exception.IntakeError;
import vn.sunvibe.property.core.application.exception.IntakeRefusedException;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLeadPort;
import vn.sunvibe.property.core.application.shared.external.survey.IntakeSource;
import vn.sunvibe.property.core.application.shared.external.user.UserDirectoryPort;
import vn.sunvibe.property.core.application.usecase.intake.IntakeActor;
import vn.sunvibe.property.core.application.usecase.intake.IntakeBlockView;
import vn.sunvibe.property.core.application.usecase.intake.IntakePreviewView;
import vn.sunvibe.property.core.domain.model.property.enums.AssetType;
import vn.sunvibe.property.core.domain.model.intake.AvailabilityItem;
import vn.sunvibe.property.core.domain.model.intake.IntakeTarget;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.model.intake.SelectedSpace;
import vn.sunvibe.property.core.domain.model.intake.SpaceUnitMapping;
import vn.sunvibe.property.core.domain.model.intake.TargetMode;
import vn.sunvibe.property.core.domain.policy.intake.PreviewHash;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository.PropertyFacts;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository.UnitFacts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds the preview and the hash a completion must name (property-create intake spec 7.1, S-18, S-32). One place computes both,
 * so the hash a preview shows is the hash completion recomputes.
 */
@Component
@RequiredArgsConstructor
public class IntakePreviewBuilder {

    private static final List<String> NOT_CARRIED = List.of("PHOTOS", "NOTES", "CONTACT", "TERMS");
    private static final char NUL = '\u0000';

    private final InventoryReadRepository inventory;
    private final UserDirectoryPort users;
    private final IntakeLeadPort leads;

    /** property-create intake spec 4.8: G1 text holding U+0000 cannot reach a property; a 409, never a database error. */
    public static void requireStorable(IntakeSource source) {
        boolean clean = Stream.of(source.addressText(), source.wardName(), source.provinceName())
                .allMatch(text -> text == null || text.indexOf(NUL) < 0)
                && source.spaces().stream().allMatch(space -> (space.code() + space.location()).indexOf(NUL) < 0);
        if (!clean) {
            throw new IntakeRefusedException(IntakeError.INTAKE_SOURCE_INVALID);
        }
    }

    /** The property and unit versions the preview reads: a link's, none on a create. */
    public Map<String, Long> versions(IntakeTarget target) {
        Map<String, Long> versions = new HashMap<>();
        if (target.mode() == TargetMode.LINK) {
            inventory.property(target.propertyIdOrNull()).ifPresent(property -> versions.put(property.id(), property.version()));
            inventory.units(target.mappings().stream().map(SpaceUnitMapping::unitId).toList())
                    .forEach(unit -> versions.put(unit.id(), unit.version()));
        }
        return versions;
    }

    public String hash(IntakeState state, IntakeSource source) {
        InventoryIntake row = state.currentOrNull();
        IntakeTarget target = row.getTarget();
        Map<String, String> mappings = target.mappings().stream()
                .collect(Collectors.toMap(SpaceUnitMapping::spaceId, SpaceUnitMapping::unitId));
        return PreviewHash.of(new PreviewHash.Inputs(source.contentSha256(),
                row.getSelection().stream().map(SelectedSpace::spaceId).toList(), target.mode().name(),
                target.propertyIdOrNull(), target.canonicalSpaceIdOrNull(), mappings, versions(target),
                row.getAvailability().stream().map(IntakePreviewBuilder::line).toList(), state.lead().assigneeOrNull(),
                state.proofOrNull().getId()));
    }

    private static String line(AvailabilityItem item) {
        return String.join("|", item.spaceId(), item.state().name(), String.valueOf(item.contactedOn()), item.note(),
                String.valueOf(item.expectedDateOrNull()), item.receivedByUserId());
    }

    public IntakePreviewView view(IntakeState state, IntakeSource source, IntakeActor actor) {
        InventoryIntake row = state.currentOrNull();
        IntakeTarget target = row.getTarget();
        boolean link = target.mode() == TargetMode.LINK;
        PropertyFacts property = link ? inventory.property(target.propertyIdOrNull())
                .orElseThrow(() -> new IntakeRefusedException(IntakeError.LINK_MAPPING_INVALID)) : null;
        Map<String, UnitFacts> unitsBySpace = link ? unitsBySpace(target) : Map.of();
        return new IntakePreviewView(hash(state, source), owner(state), property(property, source), units(row, source, unitsBySpace),
                new IntakePreviewView.SourcePart(source.revisionId(), source.decidedAt(), source.addressText()), terms(source),
                NOT_CARRIED, warnings(state, source, property, unitsBySpace, actor));
    }

    private IntakePreviewView.Owner owner(IntakeState state) {
        return new IntakePreviewView.Owner("CONFIRMED", state.proofOrNull().getOwnerCheck().name(),
                users.accountContext(state.proofOrNull().getUserId()).addressMasked());
    }

    private static IntakePreviewView.PropertyPart property(PropertyFacts linkedOrNull, IntakeSource source) {
        if (linkedOrNull != null) {
            return new IntakePreviewView.PropertyPart(linkedOrNull.code(), linkedOrNull.title(), linkedOrNull.assetTypeOrNull(),
                    linkedOrNull.layoutOrNull(), linkedOrNull.street(), linkedOrNull.wardName());
        }
        AssetType type = IntakeEvaluator.assetType(source);
        return new IntakePreviewView.PropertyPart(null, type.getLabel() + ", " + source.wardName(), type.name(),
                source.rentalScope(), source.addressText(), source.wardName());
    }

    private Map<String, UnitFacts> unitsBySpace(IntakeTarget target) {
        Map<String, UnitFacts> byId = inventory.units(target.mappings().stream().map(SpaceUnitMapping::unitId).toList()).stream()
                .collect(Collectors.toMap(UnitFacts::id, Function.identity()));
        Map<String, UnitFacts> bySpace = new HashMap<>();
        target.mappings().forEach(mapping -> bySpace.put(mapping.spaceId(), byId.get(mapping.unitId())));
        return bySpace;
    }

    private static List<IntakePreviewView.UnitPart> units(InventoryIntake row, IntakeSource source,
                                                          Map<String, UnitFacts> unitsBySpace) {
        Map<String, IntakeSource.Space> spaces = source.spaces().stream()
                .collect(Collectors.toMap(IntakeSource.Space::spaceId, Function.identity()));
        Map<String, AvailabilityItem> availability = row.getAvailability().stream()
                .collect(Collectors.toMap(AvailabilityItem::spaceId, Function.identity()));
        return row.getSelection().stream().map(selected -> {
            IntakeSource.Space space = spaces.get(selected.spaceId());
            return new IntakePreviewView.UnitPart(space.code(), unitCode(row, space, unitsBySpace), space.location(),
                    space.areaSqm(), space.bedrooms(), space.privateBathrooms(), space.condition(), space.amenities(),
                    availability.get(selected.spaceId()).state().unitState().name(),
                    space.sources().stream().map(item -> new IntakePreviewView.FieldSource(item.field(), item.source(), item.date()))
                            .toList());
        }).toList();
    }

    /** property-create intake spec 7.1: the canonical unit takes the property's code, which a create draws only at completion. */
    private static String unitCode(InventoryIntake row, IntakeSource.Space space, Map<String, UnitFacts> unitsBySpace) {
        IntakeTarget target = row.getTarget();
        if (target.mode() == TargetMode.LINK) {
            return unitsBySpace.get(space.spaceId()).code();
        }
        boolean canonical = target.canonicalSpaceIdOrNull() == null || target.canonicalSpaceIdOrNull().equals(space.spaceId());
        return canonical ? null : space.code().strip();
    }

    private static List<IntakePreviewView.TermPart> terms(IntakeSource source) {
        return source.terms().stream().map(term -> new IntakePreviewView.TermPart(term.name(), value(term), term.spaceCodeOrNull(),
                term.source(), term.date())).toList();
    }

    private static IntakeBlockView.TermValue value(IntakeSource.Term term) {
        boolean known = term.amount() != null || term.text() != null;
        return known ? new IntakeBlockView.TermValue(term.amount(), term.currency(), term.unit(), term.text()) : null;
    }

    private List<String> warnings(IntakeState state, IntakeSource source, PropertyFacts linkedOrNull,
                                  Map<String, UnitFacts> unitsBySpace, IntakeActor actor) {
        List<String> warnings = new ArrayList<>();
        if (!source.terms().isEmpty()) {
            warnings.add("TERMS_UNCONFIRMED");
        }
        if (linkedOrNull == null) {
            warnings.add("UNIT_UNCLASSIFIED");
        } else {
            if (differs(state.currentOrNull(), unitsBySpace)) {
                warnings.add("AVAILABILITY_DIFFERS");
            }
            if (linkedOrNull.intakeIdOrNull() == null) {
                warnings.add("LEGACY_PROPERTY");
            }
        }
        if (!leads.otherOpenLeadIdsOfPhone(state.lead().leadId(), actor.viewer()).isEmpty()) {
            warnings.add("SAME_PHONE_LEAD");
        }
        return warnings;
    }

    private static boolean differs(InventoryIntake row, Map<String, UnitFacts> unitsBySpace) {
        return row.getAvailability().stream().anyMatch(item -> !item.state().unitState().name()
                .equals(unitsBySpace.get(item.spaceId()).availabilityState()));
    }
}
