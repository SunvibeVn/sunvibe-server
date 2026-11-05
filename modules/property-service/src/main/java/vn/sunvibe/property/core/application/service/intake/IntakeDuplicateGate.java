package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.application.exception.IntakeError;
import vn.sunvibe.property.core.application.exception.IntakeRefusedException;
import vn.sunvibe.property.core.domain.model.enums.AssetType;
import vn.sunvibe.property.core.domain.model.intake.CaseSignal;
import vn.sunvibe.property.core.domain.model.intake.DuplicateCase;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.model.intake.SelectedSpace;
import vn.sunvibe.property.core.domain.model.intake.UnitMapping;
import vn.sunvibe.property.core.domain.policy.AddressNormalizer;
import vn.sunvibe.property.core.domain.policy.intake.CasePolicy.Reading;
import vn.sunvibe.property.core.domain.policy.intake.DuplicateSignal;
import vn.sunvibe.property.core.domain.policy.intake.DuplicateSignal.Place;
import vn.sunvibe.property.core.domain.policy.intake.PairKey;
import vn.sunvibe.property.core.domain.repository.DuplicateCaseDomainRepository;
import vn.sunvibe.property.core.domain.repository.IntakeUnitMappingDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryIntakeDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository.PropertyFacts;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository.UnitFacts;
import vn.sunvibe.shared.generator.id.IdGenerator;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The duplicate gate (property-create intake spec 4.10, property-create BR-21). It runs over every property of the ward, whoever owns it, by
 * scalar read, and over the live competing intakes, read with no viewer: a Sale's own view would hide them. The insert is safe
 * outside any ward lock: the pair key stands alone, and the row read after it decides.
 */
@Component
@RequiredArgsConstructor
public class IntakeDuplicateGate {

    private final InventoryReadRepository inventory;
    private final InventoryIntakeDomainRepository intakes;
    private final DuplicateCaseDomainRepository cases;
    private final IntakeUnitMappingDomainRepository mappings;
    private final IntakeEvaluator evaluator;
    private final IdGenerator<String> ulidGenerator;

    /**
     * A create target or completion.
     *
     * @param wardName the ward of the source, which matches a legacy row by name
     * @return whether a case now holds the row in CASE_OPEN
     * @throws IntakeRefusedException INTAKE_CREATE_NOT_ALLOWED when a decided pair forces a property on this row
     */
    public boolean holdsCreate(InventoryIntake row, String wardName, Instant now) {
        Place place = place(row);
        boolean held = false;
        String ward = row.getSource().wardCode();
        for (PropertyFacts property : inventory.propertiesOfWard(ward, AddressNormalizer.normalize(wardName))) {
            Optional<CaseSignal> signal = signal(place, property);
            if (signal.isPresent()) {
                String scope = row.scopeHash();
                held |= answer(row.getId(), DuplicateCase.open(ulidGenerator.generateId(), row.getId(), property.id(), null,
                        PairKey.property(property.id(), row.getId(), scope), scope, null, signal.get(), now));
            }
        }
        for (InventoryIntake other : intakes.findOpenOfWard(ward)) {
            if (!other.getId().equals(row.getId()) && DuplicateSignal.hit(place, place(other)).isPresent()
                    && evaluator.sideLive(other)) {
                String scope = row.scopeHash();
                String otherScope = other.scopeHash();
                held |= answer(row.getId(), DuplicateCase.open(ulidGenerator.generateId(), row.getId(), null, other.getId(),
                        PairKey.intakes(row.getId(), scope, other.getId(), otherScope), scope, otherScope, CaseSignal.INTAKE, now));
            }
        }
        return held;
    }

    /**
     * A link whose place differs from the source's opens a case, because one asset can be typed two ways (spec 4.7).
     *
     * @return whether a case now holds the row in CASE_OPEN
     */
    public boolean holdsLink(InventoryIntake row, String propertyId, Instant now) {
        PropertyFacts property = inventory.property(propertyId)
                .orElseThrow(() -> new IntakeRefusedException(IntakeError.LINK_MAPPING_INVALID));
        if (DuplicateSignal.samePlace(place(row), place(property, Set.of()))) {
            return false;
        }
        String scope = row.scopeHash();
        return answer(row.getId(), DuplicateCase.open(ulidGenerator.generateId(), row.getId(), propertyId, null,
                PairKey.linkPlace(propertyId, row.getId(), scope), scope, null, CaseSignal.LINK_PLACE, now));
    }

    /** spec 4.10: no row is no hit, because the other side may have just left; a stored row answers by how it reads now. */
    private boolean answer(String intakeId, DuplicateCase fresh) {
        cases.insertIfAbsent(fresh);
        return cases.findLiveByPairKey(fresh.getPairKey()).map(stored -> holds(intakeId, stored)).orElse(false);
    }

    private boolean holds(String intakeId, DuplicateCase stored) {
        Reading reading = IntakeEvaluator.readingFor(intakeId, evaluator.caseFacts(stored));
        if (reading == Reading.FORCED && stored.getSignal() != CaseSignal.LINK_PLACE) {
            throw new IntakeRefusedException(IntakeError.INTAKE_CREATE_NOT_ALLOWED);
        }
        return reading == Reading.OPEN || reading == Reading.WAITING;
    }

    /** The place first: only a row that shares it needs its unit codes read. */
    private Optional<CaseSignal> signal(Place place, PropertyFacts property) {
        if (!DuplicateSignal.samePlace(place, place(property, Set.of()))) {
            return Optional.empty();
        }
        List<UnitFacts> units = inventory.unitsOf(property.id());
        Set<String> codes = units.stream().map(UnitFacts::code).collect(Collectors.toCollection(HashSet::new));
        // The canonical unit of a result carries the code of the property; the surveyed code stays in its mapping (D5-14)
        mappings.findByUnitIds(units.stream().map(UnitFacts::id).toList()).stream().map(UnitMapping::spaceCode).forEach(codes::add);
        return DuplicateSignal.hit(place, place(property, codes));
    }

    private static Place place(InventoryIntake row) {
        return new Place(row.getAssetType(), row.getSource().addressNorm(), row.getSource().latitude(),
                row.getSource().longitude(), row.getSelection().stream().map(SelectedSpace::code).collect(Collectors.toSet()));
    }

    /** A legacy row has no normalized address, and an odd asset type must not fail the ward: it reads as no type. */
    private static Place place(PropertyFacts property, Set<String> codes) {
        String address = property.addressNormOrNull() != null ? property.addressNormOrNull()
                : AddressNormalizer.normalize(property.street());
        return new Place(assetType(property.assetTypeOrNull()), address, property.latitude(), property.longitude(), codes);
    }

    private static AssetType assetType(String code) {
        return Arrays.stream(AssetType.values()).filter(type -> type.name().equals(code)).findFirst().orElse(null);
    }
}
