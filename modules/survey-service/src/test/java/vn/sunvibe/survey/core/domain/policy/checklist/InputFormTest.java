package vn.sunvibe.survey.core.domain.policy.checklist;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeOverrides;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsContent;
import vn.sunvibe.survey.core.domain.model.checklist.Field;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.TemplateV1;
import vn.sunvibe.survey.core.domain.model.checklist.TermsContent;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupDocument;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_1;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_2;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.condition;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.dimensions;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.dimensionsSpace;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identificationParts;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identificationWhole;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.observedSpace;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.terms;

/** The stored content as GET shows it and a save compares it, without metadata (survey-checklist spec PD-4, PD-5). */
class InputFormTest {

    private static final IdentificationContent PARTS = identificationParts(observedSpace(SPACE_1, "P101"),
            observedSpace(SPACE_2, "P102"));

    @Test
    void ofStored_partsNullSharedWetAreas_readAsUnknown() {
        ConditionContent stored = condition(SPACE_1, SPACE_2);

        ConditionContent read = (ConditionContent) InputForm.ofStored(document(stored), PARTS);

        assertEquals(Field.unknown(), read.sharedKitchen());
        assertEquals(Field.unknown(), read.sharedBathroom());
    }

    @Test
    void ofStored_wholeNullSharedWetAreas_stayNull() {
        ConditionContent read = (ConditionContent) InputForm.ofStored(document(condition(SPACE_1)),
                identificationWhole());

        assertNull(read.sharedKitchen());
        assertNull(read.sharedBathroom());
    }

    @Test
    void ofStored_partsNullChargeOverrides_readAsFiveNulls() {
        TermsContent read = (TermsContent) InputForm.ofStored(document(terms(SPACE_1, SPACE_2)), PARTS);

        assertEquals(ChargeOverrides.none(), read.spaces().get(0).chargeOverrides());
        assertEquals(ChargeOverrides.none(), read.spaces().get(1).chargeOverrides());
        assertNull(read.spaces().get(0).spaceFees());
    }

    @Test
    void ofStored_g1SpaceWithoutStoredEntry_getsAnEmptyOne() {
        DimensionsContent read = (DimensionsContent) InputForm.ofStored(document(dimensions(SPACE_2)), PARTS);

        assertEquals(List.of(TemplateV1.emptyDimensionsSpace(SPACE_1), dimensionsSpace(SPACE_2)), read.spaces());
    }

    @Test
    void ofStored_identification_isItsContent() {
        assertEquals(PARTS, InputForm.ofStored(document(PARTS), PARTS));
    }

    private static GroupDocument document(vn.sunvibe.survey.core.domain.model.checklist.GroupContent content) {
        return new GroupDocument(content.group(), content, Map.of(), Map.of());
    }
}
