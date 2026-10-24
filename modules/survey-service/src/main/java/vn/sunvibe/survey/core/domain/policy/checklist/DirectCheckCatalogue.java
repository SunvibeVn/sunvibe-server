package vn.sunvibe.survey.core.domain.policy.checklist;

import java.util.List;

/**
 * The direct checks of survey-checklist BR-26 (spec A-28, D-AI-40): fields that only an on-site check at the draft's
 * own visit fills. Kept as a list, separate from whether a wrapper has a source, so adding a source to a field can
 * never remove a gate. Owner-sourced G1 address and G3 area and counts complete only their own fields; dates, G6,
 * authored notes and owner fix reports are never direct checks.
 */
public final class DirectCheckCatalogue {

    public static final List<String> FIELDS = List.of(
            "identification.propertyType", "identification.fit", "identification.rentalScope",
            "identification.spaces",
            "parts.accessRoute", "parts.sharedAreas", "parts.floor", "parts.privateAreas", "parts.actualAccess",
            "condition.occupancy", "condition.mainSpace", "condition.kitchen", "condition.bathroom",
            "condition.sharedKitchen", "condition.sharedBathroom",
            "safety.egress", "safety.accessRestrictions", "safety.hazards", "safety.hazards.OBSERVED_RESOLVED");

    private DirectCheckCatalogue() {
    }

    public static boolean contains(String groupPathName, String field) {
        return FIELDS.contains(groupPathName + "." + field);
    }
}
