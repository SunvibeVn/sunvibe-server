package vn.sunvibe.survey.core.domain.policy.checklist;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.checklist.AccessExtent;
import vn.sunvibe.survey.core.domain.model.checklist.ActualAccess;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsContent;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Field;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.PartsContent;
import vn.sunvibe.survey.core.domain.model.checklist.PartsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.YesNo;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static vn.sunvibe.survey.core.domain.policy.checklist.TemplateV1RulesIdentificationTest.assertPaths;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_1;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_2;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_3;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.TODAY;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.dimensions;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.dimensionsSpace;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identificationParts;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.observedSpace;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.ownerSaid;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.parts;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.partsSpace;

/**
 * The stateless body rules of G2 and G3 (survey-checklist spec 4.6, 5.5 save step 1; W16-6) and the canonical form of
 * per-space entries (spec PD-5).
 */
class TemplateV1RulesPartsDimensionsTest {

    private static final String PHONE = "gọi 0905 123 456";

    @Test
    void checkAndNormalise_validPartsAndDimensions_passUnchanged() {
        PartsContent parts = parts(SPACE_1, SPACE_2);
        DimensionsContent dimensions = dimensions(SPACE_1);

        assertEquals(parts, TemplateV1Rules.checkAndNormalise(parts, TODAY));
        assertEquals(dimensions, TemplateV1Rules.checkAndNormalise(dimensions, TODAY));
    }

    @Test
    void totalFloors_outOfRange_isRefused() {
        assertPaths(withFloors(SourcedField.of(0, ChecklistSource.OBSERVED_ON_SITE)), "content.totalFloors.value");
        assertPaths(withFloors(SourcedField.of(201, ChecklistSource.OBSERVED_ON_SITE)), "content.totalFloors.value");
        assertEquals(withFloors(SourcedField.of(200, ChecklistSource.OBSERVED_ON_SITE)),
                TemplateV1Rules.checkAndNormalise(withFloors(SourcedField.of(200, ChecklistSource.OBSERVED_ON_SITE)),
                        TODAY));
    }

    @Test
    void partsTexts_lengthAndPhoneRules() {
        assertPaths(withSpace(new PartsSpace(SPACE_1, Field.value("x".repeat(51)), Field.value("Bếp"),
                accessAll())), "content.spaces.0.floor.value");
        assertPaths(withSpace(new PartsSpace(SPACE_1, Field.value(PHONE), Field.value("Bếp"), accessAll())),
                "content.spaces.0.floor.value");
        assertPaths(withSpace(new PartsSpace(SPACE_1, Field.value("Tầng 2"), Field.value(PHONE), accessAll())),
                "content.spaces.0.privateAreas.value");
        PartsContent valid = parts(SPACE_1);
        assertPaths(new PartsContent(Field.value(PHONE), valid.sharedAreas(), valid.totalFloors(), valid.spaces()),
                "content.accessRoute.value");
        assertPaths(new PartsContent(valid.accessRoute(), Field.value(PHONE), valid.totalFloors(), valid.spaces()),
                "content.sharedAreas.value");
        assertPaths(new PartsContent(valid.accessRoute(), Field.notApplicable(PHONE), valid.totalFloors(),
                valid.spaces()), "content.sharedAreas.naReason");
        assertPaths(new PartsContent(valid.accessRoute(), Field.notApplicable(" "), valid.totalFloors(),
                valid.spaces()), "content.sharedAreas.naReason");
        assertPaths(new PartsContent(Field.notApplicable("Không có lối"), valid.sharedAreas(), valid.totalFloors(),
                valid.spaces()), "content.accessRoute.state");
    }

    @Test
    void actualAccess_enteredPartNeedsANote() {
        assertPaths(withSpace(new PartsSpace(SPACE_1, Field.value("Tầng 2"), Field.value("Bếp"),
                Field.value(new ActualAccess(AccessExtent.ENTERED_PART, null)))),
                "content.spaces.0.actualAccess.value.note");
        assertPaths(withSpace(new PartsSpace(SPACE_1, Field.value("Tầng 2"), Field.value("Bếp"),
                Field.value(new ActualAccess(AccessExtent.ENTERED_PART, PHONE)))),
                "content.spaces.0.actualAccess.value.note");
        assertPaths(withSpace(new PartsSpace(SPACE_1, Field.value("Tầng 2"), Field.value("Bếp"),
                Field.value(new ActualAccess(null, null)))), "content.spaces.0.actualAccess.value.extent");
        PartsContent partly = withSpace(new PartsSpace(SPACE_1, Field.value("Tầng 2"), Field.value("Bếp"),
                Field.value(new ActualAccess(AccessExtent.ENTERED_PART, "Phòng ngủ 2 khóa"))));
        assertEquals(partly, TemplateV1Rules.checkAndNormalise(partly, TODAY));
    }

    @Test
    void parts_spaceIdTwiceOrNotAUlid_isRefused() {
        assertPaths(new PartsContent(parts().accessRoute(), parts().sharedAreas(), parts().totalFloors(),
                List.of(partsSpace(SPACE_1), partsSpace(SPACE_1))), "content.spaces.1.spaceId");
        assertPaths(new PartsContent(parts().accessRoute(), parts().sharedAreas(), parts().totalFloors(),
                List.of(partsSpace("P101"))), "content.spaces.0.spaceId");
    }

    @Test
    void area_outOfRangeOrTwoDecimals_isRefused() {
        for (String area : List.of("0", "0.05", "45.25", "100000", "-1")) {
            assertPaths(withArea(SourcedField.of(new BigDecimal(area), ChecklistSource.MEASURED)),
                    "content.spaces.0.area.value");
        }
        for (String area : List.of("0.1", "99999.9", "45.50")) {
            DimensionsContent accepted = withArea(SourcedField.of(new BigDecimal(area), ChecklistSource.ESTIMATED));
            TemplateV1Rules.checkAndNormalise(accepted, TODAY);
        }
    }

    /** Review Focus 1: an area sent as 45 after it was stored as 45.0 is the same value. */
    @Test
    void area_wholeNumberAndOneDecimal_giveTheSameRecord() {
        DimensionsContent whole = withArea(SourcedField.of(new BigDecimal("45"), ChecklistSource.MEASURED));
        DimensionsContent decimal = withArea(SourcedField.of(new BigDecimal("45.0"), ChecklistSource.MEASURED));

        assertEquals(TemplateV1Rules.checkAndNormalise(decimal, TODAY), TemplateV1Rules.checkAndNormalise(whole, TODAY));
        assertEquals(new BigDecimal("45.0"), ((DimensionsContent) TemplateV1Rules.checkAndNormalise(whole, TODAY))
                .spaces().get(0).area().value());
    }

    @Test
    void counts_outOfRange_areRefused() {
        DimensionsSpace valid = dimensionsSpace(SPACE_1);
        assertPaths(new DimensionsContent(List.of(new DimensionsSpace(SPACE_1, valid.area(),
                SourcedField.of(51, ChecklistSource.OBSERVED_ON_SITE), valid.privateBathrooms(),
                valid.sharedBathroomUse(), null))), "content.spaces.0.bedrooms.value");
        assertPaths(new DimensionsContent(List.of(new DimensionsSpace(SPACE_1, valid.area(), valid.bedrooms(),
                SourcedField.of(-1, ChecklistSource.OBSERVED_ON_SITE), valid.sharedBathroomUse(), null))),
                "content.spaces.0.privateBathrooms.value");
        DimensionsContent studio = new DimensionsContent(List.of(new DimensionsSpace(SPACE_1, valid.area(),
                SourcedField.of(0, ChecklistSource.OBSERVED_ON_SITE), valid.privateBathrooms(),
                valid.sharedBathroomUse(), null)));
        assertEquals(studio, TemplateV1Rules.checkAndNormalise(studio, TODAY));
    }

    @Test
    void sources_ofTheCountAndAreaFamilies() {
        DimensionsSpace valid = dimensionsSpace(SPACE_1);
        DimensionsContent sharedUseOnSite = new DimensionsContent(List.of(new DimensionsSpace(SPACE_1, valid.area(),
                valid.bedrooms(), valid.privateBathrooms(), SourcedField.of(YesNo.YES, ChecklistSource.OBSERVED_ON_SITE),
                null)));
        assertEquals(sharedUseOnSite, TemplateV1Rules.checkAndNormalise(sharedUseOnSite, TODAY));

        assertPaths(new DimensionsContent(List.of(new DimensionsSpace(SPACE_1, valid.area(),
                SourcedField.of(2, ChecklistSource.MEASURED), valid.privateBathrooms(), valid.sharedBathroomUse(),
                null))), "content.spaces.0.bedrooms.source");
        assertPaths(withArea(SourcedField.of(new BigDecimal("45.5"), ChecklistSource.OBSERVED_ON_SITE)),
                "content.spaces.0.area.source");
        assertPaths(withFloors(SourcedField.of(5, ChecklistSource.OWNER_FORM)), "content.totalFloors.source");

        DimensionsContent ownerStated = withArea(ownerSaid(new BigDecimal("60"), TODAY.minusDays(3)));
        assertEquals(new BigDecimal("60.0"), ((DimensionsContent) TemplateV1Rules.checkAndNormalise(ownerStated,
                TODAY)).spaces().get(0).area().value());
    }

    @Test
    void layoutNote_phoneOrTooLong_isRefused() {
        DimensionsSpace valid = dimensionsSpace(SPACE_1);
        assertPaths(new DimensionsContent(List.of(new DimensionsSpace(SPACE_1, valid.area(), valid.bedrooms(),
                valid.privateBathrooms(), valid.sharedBathroomUse(), PHONE))), "content.spaces.0.layoutNote");
        assertPaths(new DimensionsContent(List.of(new DimensionsSpace(SPACE_1, valid.area(), valid.bedrooms(),
                valid.privateBathrooms(), valid.sharedBathroomUse(), "x".repeat(1001)))),
                "content.spaces.0.layoutNote");
    }

    @Test
    void dimensions_spaceIdTwice_isRefused() {
        assertPaths(new DimensionsContent(List.of(dimensionsSpace(SPACE_2), dimensionsSpace(SPACE_2))),
                "content.spaces.1.spaceId");
    }

    // ==================== canonical form (spec PD-5) ====================

    @Test
    void canonical_entriesInG1OrderAndMissingSpacesFilled() {
        IdentificationContent g1 = identificationParts(observedSpace(SPACE_1, "P101"), observedSpace(SPACE_2, "P102"),
                observedSpace(SPACE_3, "P103"));
        DimensionsContent sent = new DimensionsContent(List.of(dimensionsSpace(SPACE_3), dimensionsSpace(SPACE_1)));

        DimensionsContent canonical = (DimensionsContent) TemplateV1Rules.canonical(sent, g1);

        assertEquals(List.of(dimensionsSpace(SPACE_1), emptyDimensions(SPACE_2), dimensionsSpace(SPACE_3)),
                canonical.spaces());
    }

    @Test
    void canonical_unknownSpaceEntry_keptAfterKnownOnes() {
        IdentificationContent g1 = identificationParts(observedSpace(SPACE_1, "P101"));
        PartsContent sent = new PartsContent(parts().accessRoute(), parts().sharedAreas(), parts().totalFloors(),
                List.of(partsSpace(SPACE_3), partsSpace(SPACE_2), partsSpace(SPACE_1)));

        PartsContent canonical = (PartsContent) TemplateV1Rules.canonical(sent, g1);

        assertEquals(List.of(partsSpace(SPACE_1), partsSpace(SPACE_3), partsSpace(SPACE_2)), canonical.spaces());
    }

    @Test
    void canonical_identification_isUnchanged() {
        IdentificationContent g1 = identificationParts(observedSpace(SPACE_2, "P102"), observedSpace(SPACE_1, "P101"));

        assertEquals(g1, TemplateV1Rules.canonical(g1, g1));
    }

    // ==================== helpers ====================

    private static DimensionsSpace emptyDimensions(String spaceId) {
        return new DimensionsSpace(spaceId, SourcedField.unknown(), SourcedField.unknown(), SourcedField.unknown(),
                SourcedField.unknown(), null);
    }

    private static Field<ActualAccess> accessAll() {
        return Field.value(new ActualAccess(AccessExtent.ENTERED_ALL, null));
    }

    private static PartsContent withFloors(SourcedField<Integer> floors) {
        PartsContent valid = parts(SPACE_1);
        return new PartsContent(valid.accessRoute(), valid.sharedAreas(), floors, valid.spaces());
    }

    private static PartsContent withSpace(PartsSpace space) {
        PartsContent valid = parts();
        return new PartsContent(valid.accessRoute(), valid.sharedAreas(), valid.totalFloors(), List.of(space));
    }

    private static DimensionsContent withArea(SourcedField<BigDecimal> area) {
        DimensionsSpace valid = dimensionsSpace(SPACE_1);
        return new DimensionsContent(List.of(new DimensionsSpace(SPACE_1, area, valid.bedrooms(),
                valid.privateBathrooms(), valid.sharedBathroomUse(), valid.layoutNote())));
    }
}
