package vn.sunvibe.property.core.domain.model.intake;

/**
 * Which unit a surveyed space became (property-create intake spec 5.2 file 8, D5-20): condition and amenities stay in the
 * revision and reach a unit through this row. {@code canonical} is false on a link.
 */
public record UnitMapping(String intakeId, String spaceId, String spaceCode, String unitId, boolean canonical) {

    @Override
    public String toString() {
        return "UnitMapping[" + intakeId + " " + spaceId + "]";
    }
}
