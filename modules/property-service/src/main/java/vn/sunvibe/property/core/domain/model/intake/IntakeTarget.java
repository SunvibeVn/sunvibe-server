package vn.sunvibe.property.core.domain.model.intake;

import java.util.List;

/**
 * Where an intake goes (property-create intake spec 4.7, 4.8).
 *
 * @param propertyIdOrNull       the property of a LINK
 * @param canonicalSpaceIdOrNull the canonical space of a CREATE in PARTS
 * @param mappings               the mappings of a LINK; empty on a CREATE
 */
public record IntakeTarget(TargetMode mode, String propertyIdOrNull, String canonicalSpaceIdOrNull,
                           List<SpaceUnitMapping> mappings) {

    public IntakeTarget {
        mappings = mappings == null ? List.of() : List.copyOf(mappings);
    }
}
