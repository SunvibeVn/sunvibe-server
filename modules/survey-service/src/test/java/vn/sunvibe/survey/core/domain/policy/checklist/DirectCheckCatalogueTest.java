package vn.sunvibe.survey.core.domain.policy.checklist;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Field;
import vn.sunvibe.survey.core.domain.model.checklist.Hazard;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.PartsContent;
import vn.sunvibe.survey.core.domain.model.checklist.PartsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.SafetyContent;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The direct checks of survey-checklist BR-26 as a list in code (spec A-28; W16-42). */
class DirectCheckCatalogueTest {

    @Test
    void catalogue_equalsSpecA28() {
        assertEquals(List.of("identification.propertyType", "identification.fit", "identification.rentalScope",
                "identification.spaces", "parts.accessRoute", "parts.sharedAreas", "parts.floor",
                "parts.privateAreas", "parts.actualAccess", "condition.occupancy", "condition.mainSpace",
                "condition.kitchen", "condition.bathroom", "condition.sharedKitchen", "condition.sharedBathroom",
                "safety.egress", "safety.accessRestrictions", "safety.hazards", "safety.hazards.OBSERVED_RESOLVED"),
                DirectCheckCatalogue.FIELDS);
    }

    /** spec A-28: no field of the catalogue has an owner source in template v1, so owner words never fill it. */
    @Test
    void catalogue_noFieldHasASourceInTemplateV1() {
        Map<String, Class<? extends Record>> records = Map.of("identification", IdentificationContent.class,
                "parts", PartsContent.class, "partsSpace", PartsSpace.class, "condition", ConditionContent.class,
                "conditionSpace", ConditionSpace.class, "safety", SafetyContent.class, "hazard", Hazard.class);
        for (String entry : DirectCheckCatalogue.FIELDS) {
            String[] parts = entry.split("\\.");
            String field = parts[1];
            Class<? extends Record> owner = records.get(parts[0]);
            if (!hasComponent(owner, field)) {
                owner = records.get(parts[0] + "Space");
            }
            assertFalse(owner == null || !hasComponent(owner, field), entry + " is not a template v1 field");
            RecordComponent component = component(owner, field);
            assertFalse(component.getType() == SourcedField.class, entry + " has a source");
            if (component.getType() != Field.class && component.getType() != List.class) {
                assertFalse(component.getType().getSimpleName().contains("Source"), entry);
            }
        }
        for (RecordComponent component : Hazard.class.getRecordComponents()) {
            assertFalse(component.getName().equals("source"), "a hazard has no source");
        }
    }

    private static boolean hasComponent(Class<? extends Record> type, String name) {
        return type != null && component(type, name) != null;
    }

    private static RecordComponent component(Class<? extends Record> type, String name) {
        for (RecordComponent component : type.getRecordComponents()) {
            if (component.getName().equals(name)) {
                return component;
            }
        }
        return null;
    }
}
