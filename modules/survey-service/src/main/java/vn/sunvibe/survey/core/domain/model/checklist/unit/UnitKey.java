package vn.sunvibe.survey.core.domain.model.checklist.unit;

import vn.sunvibe.survey.core.domain.model.checklist.AmenityCode;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The stable key of a provenance unit, built from ids, never from space codes or positions (survey-checklist spec 4.5,
 * plan Task A6): {@code dimensions/spaces/<spaceId>/area}, {@code safety/hazards/<hazardId>}, ... A G6 item's key
 * holds its amenity code at its placement, which is the item's identity (spec A-29, survey-checklist BR-28: the same
 * code at the same placement is the same item), as the itemId is an other item's and the hazardId a hazard's; such a
 * key comes back when the same identity is sent again after a removal. Keys reach the API in
 * {@code corrections[].units}, so their shape is part of the contract with the UI.
 */
public record UnitKey(String value) {

    private static final Pattern SPACE = Pattern.compile("^[a-z]+/spaces/([^/]+)(/.*)?$");

    public UnitKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A unit key is required.");
        }
    }

    /** A stored key read back; its group must be a stored group of the template. */
    public static UnitKey of(String value) {
        UnitKey key = new UnitKey(value);
        key.group();
        return key;
    }

    /** An asset-level field or plain field of the group: {@code <group>/<name>}. */
    public static UnitKey field(ChecklistGroup group, String name) {
        return new UnitKey(group.pathName() + "/" + name);
    }

    /** An observed space of G1. */
    public static UnitKey space(String spaceId) {
        return new UnitKey("identification/spaces/" + spaceId);
    }

    /** A per-space field: {@code <group>/spaces/<spaceId>/<name>}. */
    public static UnitKey spaceField(ChecklistGroup group, String spaceId, String name) {
        return new UnitKey(group.pathName() + "/spaces/" + spaceId + "/" + name);
    }

    /** The hazard list wrapper of G5 (spec A-19: "none seen" is a recorded value). */
    public static UnitKey hazardList() {
        return new UnitKey("safety/hazards");
    }

    public static UnitKey hazard(String hazardId) {
        return new UnitKey("safety/hazards/" + hazardId);
    }

    public static UnitKey sharedItem(AmenityCode code) {
        return new UnitKey("amenities/sharedItems/" + code.name());
    }

    public static UnitKey spaceItem(String spaceId, AmenityCode code) {
        return new UnitKey("amenities/spaces/" + spaceId + "/items/" + code.name());
    }

    public static UnitKey otherItem(String itemId) {
        return new UnitKey("amenities/otherItems/" + itemId);
    }

    /** A common G8 term: {@code terms/common/<name>}. */
    public static UnitKey commonTerm(String name) {
        return new UnitKey("terms/common/" + name);
    }

    /** One charge override of a space: {@code terms/spaces/<spaceId>/chargeOverrides/<charge>}. */
    public static UnitKey override(String spaceId, String charge) {
        return new UnitKey("terms/spaces/" + spaceId + "/chargeOverrides/" + charge);
    }

    /** The group the key belongs to. */
    public ChecklistGroup group() {
        String prefix = value.contains("/") ? value.substring(0, value.indexOf('/')) : value;
        return Arrays.stream(ChecklistGroup.values()).filter(ChecklistGroup::isStored)
                .filter(group -> group.pathName().equals(prefix)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Not a unit key of a stored group."));
    }

    /** The space a per-space key or a G1 space key belongs to; null for asset-level units and hazards. */
    public String spaceId() {
        Matcher matcher = SPACE.matcher(value);
        return matcher.matches() ? matcher.group(1) : null;
    }

    /** The last segment: the field name of a field key. */
    public String name() {
        return value.substring(value.lastIndexOf('/') + 1);
    }

    @Override
    public String toString() {
        return value;
    }
}
