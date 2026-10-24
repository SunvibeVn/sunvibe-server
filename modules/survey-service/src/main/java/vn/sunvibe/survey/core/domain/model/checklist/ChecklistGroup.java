package vn.sunvibe.survey.core.domain.model.checklist;

import java.util.Arrays;
import java.util.List;

/**
 * The groups of the long-term checklist template, in template order (survey-checklist BR-11, spec 4.6). Each stored
 * group is saved on its own; EVIDENCE has no content until survey photos exist (spec C-1).
 */
public enum ChecklistGroup {
    /** G1: address, type, fit, rental scope and the observed spaces. */
    IDENTIFICATION("identification"),
    /** G2: access route, shared and private parts, floors, actual access. */
    PARTS("parts"),
    /** G3: area, rooms and bathrooms per space. */
    DIMENSIONS("dimensions"),
    /** G4: occupancy, availability and the area checks. */
    CONDITION("condition"),
    /** G5: egress, access restrictions and hazards. */
    SAFETY("safety"),
    /** G6: furniture and amenities inventory. */
    AMENITIES("amenities"),
    /** G7: photos; no content in this slice (survey-media, spec C-1). */
    EVIDENCE("evidence"),
    /** G8: surveyed or offered rental terms. */
    TERMS("terms");

    private final String pathName;

    ChecklistGroup(String pathName) {
        this.pathName = pathName;
    }

    /** Whether the group has a stored document; false only for EVIDENCE. */
    public boolean isStored() {
        return this != EVIDENCE;
    }

    /** The group's name in paths, unit keys and JSON ({@code identification} ... {@code terms}). */
    public String pathName() {
        return pathName;
    }

    /** The stored groups in template order. */
    public static List<ChecklistGroup> stored() {
        return Arrays.stream(values()).filter(ChecklistGroup::isStored).toList();
    }
}
