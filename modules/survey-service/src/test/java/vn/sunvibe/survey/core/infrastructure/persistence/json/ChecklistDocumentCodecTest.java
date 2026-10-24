package vn.sunvibe.survey.core.infrastructure.persistence.json;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesContent;
import vn.sunvibe.survey.core.domain.model.checklist.AmenityCode;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeOverrides;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeUnit;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsContent;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.Fee;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.Hazard;
import vn.sunvibe.survey.core.domain.model.checklist.HazardLink;
import vn.sunvibe.survey.core.domain.model.checklist.HazardSeverity;
import vn.sunvibe.survey.core.domain.model.checklist.HazardStatus;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.Money;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedLocation;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedPlaceNames;
import vn.sunvibe.survey.core.domain.model.checklist.OtherItem;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.TermsContent;
import vn.sunvibe.survey.core.domain.model.checklist.YesNo;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupDocument;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;
import vn.sunvibe.survey.core.domain.model.checklist.unit.Tombstone;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitKey;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitMeta;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SALE;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_1;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.SPACE_2;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.amenities;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.charge;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.conditionParts;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.dimensions;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.identificationParts;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.observedSpace;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.ownerSaid;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.parts;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.safety;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.terms;
import static vn.sunvibe.survey.core.testsupport.ChecklistFixture.termsSpace;

/** The stored JSON of a group document and of a takeover checkpoint (survey-checklist spec PD-1, A-3). */
class ChecklistDocumentCodecTest {

    private static final ChecklistDocumentCodec CODEC = new ChecklistDocumentCodec();
    private static final UnitMeta META = new UnitMeta(SALE, Instant.parse("2026-10-24T03:04:05.123456Z"), SALE,
            LocalDate.parse("2026-10-20"), "address", ChecklistSource.OWNER_FORM, 42L);
    private static final String SALE_A = "01JA00000000000000000000A1";
    private static final String SALE_B = "01JB00000000000000000000B1";
    /** A hazard tombstone of BR-30: the remover, the observer and the receiver and date of its fix report. */
    private static final Tombstone REPORTED = new Tombstone(SALE_B, Instant.parse("2026-10-25T03:04:05.654321Z"),
            SALE, LocalDate.parse("2026-10-20"), null, null, 44L,
            new Tombstone.OwnerReportFacts(SALE_B, LocalDate.parse("2026-10-19")));
    /** A G4 document exactly as the W16a wave commit 6d5d53f wrote it (schema 1). */
    private static final String SCHEMA_1_DOCUMENT = "{\"schema\":1,\"content\":{\"sharedKitchen\":null,"
            + "\"sharedBathroom\":null,\"spaces\":[]},\"meta\":{},\"tombstones\":{\"condition/sharedKitchen\":{"
            + "\"enteredByUserId\":\"" + SALE_A + "\",\"enteredAt\":\"2026-10-20T05:00:00.123456Z\","
            + "\"observedByUserId\":\"" + SALE_A + "\",\"observedOn\":\"2026-10-20\",\"formField\":null,"
            + "\"lastSource\":null,\"correctionSaveId\":7}},\"place\":null}";
    /** A takeover checkpoint holding that document, as 6d5d53f wrote it (schema 1). */
    private static final String SCHEMA_1_CHECKPOINT = "{\"schema\":1,\"groups\":{\"CONDITION\":{\"version\":2,"
            + "\"updatedAt\":\"2026-10-21T01:02:03Z\",\"updatedByUserId\":\"" + SALE_A + "\",\"document\":"
            + SCHEMA_1_DOCUMENT + "}}}";
    private static final Tombstone SCHEMA_1_TOMBSTONE = new Tombstone(SALE_A,
            Instant.parse("2026-10-20T05:00:00.123456Z"), SALE_A, LocalDate.parse("2026-10-20"), null, null, 7L,
            null);

    @Test
    void everyGroupWithEveryValueFamily_roundTripsEqual() {
        for (GroupContent content : contents()) {
            GroupDocument document = new GroupDocument(content.group(), content,
                    Map.of(UnitKey.field(content.group(), "x"), META),
                    Map.of(UnitKey.field(content.group(), "y"), Tombstone.of(META)));

            assertEquals(document, CODEC.read(content.group(), CODEC.write(document)), content.group().name());
        }
    }

    @Test
    void identification_keepsThePlaceNames() {
        IdentificationContent g1 = withPin();
        GroupDocument document = new GroupDocument(ChecklistGroup.IDENTIFICATION, g1, Map.of(), Map.of(),
                new ObservedPlaceNames("Đà Nẵng", "Hải Châu"));

        GroupDocument read = CODEC.read(ChecklistGroup.IDENTIFICATION, CODEC.write(document));

        assertEquals(document, read);
        assertEquals(new BigDecimal("16.054456"), ((IdentificationContent) read.content()).observedAddress().value()
                .latitude());
    }

    /**
     * The no-op check compares records (survey-checklist spec A-17, plan PD-5), so a pin at scale 6 and an area at
     * scale 1 must come back with the same scale, also with trailing zeros, in a document and in a checkpoint.
     */
    @Test
    void trailingZerosOfPinsAndAreas_surviveTheRoundTrip() {
        IdentificationContent g1 = withPin();
        ObservedLocation pinned = new ObservedLocation(g1.observedAddress().value().addressText(), "48", "20242",
                new BigDecimal("16.067800"), new BigDecimal("108.220800"));
        IdentificationContent zeros = new IdentificationContent(
                SourcedField.of(pinned, ChecklistSource.OBSERVED_ON_SITE), g1.differenceNote(), g1.propertyType(),
                g1.fit(), g1.rentalScope(), g1.spaces(), g1.otherPartsNote(), g1.generalNote());
        GroupDocument identification = new GroupDocument(ChecklistGroup.IDENTIFICATION, zeros, Map.of(), Map.of());
        DimensionsSpace space = dimensions(SPACE_1).spaces().get(0);
        GroupDocument dimensions = new GroupDocument(ChecklistGroup.DIMENSIONS, new DimensionsContent(List.of(
                new DimensionsSpace(space.spaceId(), SourcedField.of(new BigDecimal("45.0"), ChecklistSource.MEASURED),
                        space.bedrooms(), space.privateBathrooms(), space.sharedBathroomUse(), space.layoutNote()))),
                Map.of(), Map.of());
        Map<ChecklistGroup, GroupState> snapshot = new EnumMap<>(ChecklistGroup.class);
        snapshot.put(ChecklistGroup.IDENTIFICATION, new GroupState(identification, 1, null, null));
        snapshot.put(ChecklistGroup.DIMENSIONS, new GroupState(dimensions, 1, null, null));

        assertEquals(identification, CODEC.read(ChecklistGroup.IDENTIFICATION, CODEC.write(identification)));
        assertEquals(dimensions, CODEC.read(ChecklistGroup.DIMENSIONS, CODEC.write(dimensions)));
        assertEquals(snapshot, CODEC.readSnapshot(CODEC.writeSnapshot(snapshot)));
    }

    @Test
    void storedDocument_withAnUnknownOrMissingKey_failsToRead() {
        String json = CODEC.write(GroupDocument.initial(ChecklistGroup.DIMENSIONS));

        assertThrows(IllegalStateException.class, () -> CODEC.read(ChecklistGroup.DIMENSIONS,
                json.replace("\"schema\":2", "\"schema\":2,\"extra\":true")));
        assertThrows(IllegalStateException.class, () -> CODEC.read(ChecklistGroup.DIMENSIONS,
                json.replace("\"spaces\":[]", "\"spaces\":[],\"surprise\":1")));
        assertThrows(IllegalStateException.class, () -> CODEC.read(ChecklistGroup.DIMENSIONS,
                json.replace("\"schema\":2,", "")));
    }

    /** spec A-29, PD-1: G6 item, other item and hazard tombstones, the hazard's with its report facts, as schema 2. */
    @Test
    void tombstones_schema2RoundTrip() {
        Tombstone stamped = new Tombstone(SALE_B, Instant.parse("2026-10-25T03:04:05.654321Z"), SALE,
                LocalDate.parse("2026-10-20"), null, ChecklistSource.OWNER_STATED, 43L, null);
        GroupDocument amenities = new GroupDocument(ChecklistGroup.AMENITIES, amenities(SPACE_1), Map.of(),
                Map.of(UnitKey.sharedItem(AmenityCode.WATER_HEATER), stamped,
                        UnitKey.spaceItem(SPACE_1, AmenityCode.DESK), stamped,
                        UnitKey.otherItem("01JTEM000000000000000000X1"), stamped));
        GroupDocument safety = new GroupDocument(ChecklistGroup.SAFETY, safety(), Map.of(),
                Map.of(UnitKey.hazard("01JHAZARD00000000000000002"), REPORTED));

        String json = CODEC.write(safety);

        assertTrue(json.startsWith("{\"schema\":2,"), json);
        assertTrue(json.contains("\"ownerReport\":{\"receivedByUserId\":\"" + SALE_B + "\",\"reportedOn\":"
                + "\"2026-10-19\"}"), json);
        assertEquals(safety, CODEC.read(ChecklistGroup.SAFETY, json));
        assertEquals(amenities, CODEC.read(ChecklistGroup.AMENITIES, CODEC.write(amenities)));
    }

    /** spec A-29: a document as the W16a wave wrote it (schema 1) is read with no report facts, nothing rewritten. */
    @Test
    void readsSchema1Document_ownerReportNull() {
        GroupDocument read = CODEC.read(ChecklistGroup.CONDITION, SCHEMA_1_DOCUMENT);

        assertEquals(new GroupDocument(ChecklistGroup.CONDITION, GroupDocument.initial(ChecklistGroup.CONDITION)
                .content(), Map.of(), Map.of(UnitKey.field(ChecklistGroup.CONDITION, "sharedKitchen"),
                SCHEMA_1_TOMBSTONE)), read);
        assertNull(read.tombstone(UnitKey.field(ChecklistGroup.CONDITION, "sharedKitchen")).ownerReportOrNull());
    }

    @Test
    void readsSchema1Checkpoint_ownerReportNull() {
        Map<ChecklistGroup, GroupState> read = CODEC.readSnapshot(SCHEMA_1_CHECKPOINT);

        assertEquals(Map.of(ChecklistGroup.CONDITION, new GroupState(new GroupDocument(ChecklistGroup.CONDITION,
                GroupDocument.initial(ChecklistGroup.CONDITION).content(), Map.of(),
                Map.of(UnitKey.field(ChecklistGroup.CONDITION, "sharedKitchen"), SCHEMA_1_TOMBSTONE)), 2L,
                Instant.parse("2026-10-21T01:02:03Z"), SALE_A)), read);
    }

    @Test
    void checkpoint_schema2RoundTrip() {
        Map<ChecklistGroup, GroupState> groups = new EnumMap<>(ChecklistGroup.class);
        groups.put(ChecklistGroup.SAFETY, new GroupState(new GroupDocument(ChecklistGroup.SAFETY, safety(), Map.of(),
                Map.of(UnitKey.hazard("01JHAZARD00000000000000002"), REPORTED)), 4L,
                Instant.parse("2026-10-24T03:00:00Z"), SALE));

        String json = CODEC.writeSnapshot(groups);

        assertTrue(json.startsWith("{\"schema\":2,"), json);
        assertEquals(groups, CODEC.readSnapshot(json));
    }

    /** A layout this server does not know, or a schema 1 tombstone carrying report facts, is never half read. */
    @Test
    void unknownSchema_isRefused() {
        String document = CODEC.write(GroupDocument.initial(ChecklistGroup.DIMENSIONS));
        String checkpoint = CODEC.writeSnapshot(Map.of(ChecklistGroup.DIMENSIONS, new GroupState(
                GroupDocument.initial(ChecklistGroup.DIMENSIONS), 0L, null, null)));

        assertThrows(IllegalStateException.class, () -> CODEC.read(ChecklistGroup.DIMENSIONS,
                document.replace("\"schema\":2", "\"schema\":3")));
        assertThrows(IllegalStateException.class, () -> CODEC.readSnapshot(
                checkpoint.replace("{\"schema\":2,\"groups\"", "{\"schema\":3,\"groups\"")));
        assertThrows(IllegalStateException.class, () -> CODEC.readSnapshot(checkpoint.replace(
                "\"document\":{\"schema\":2", "\"document\":{\"schema\":3")));
        assertThrows(IllegalStateException.class, () -> CODEC.read(ChecklistGroup.CONDITION,
                SCHEMA_1_DOCUMENT.replace("\"correctionSaveId\":7}", "\"correctionSaveId\":7,\"ownerReport\":null}")));
    }

    @Test
    void moneyStaysIntegerAndAreaKeepsScaleOne() {
        String terms = CODEC.write(new GroupDocument(ChecklistGroup.TERMS, terms(SPACE_1), Map.of(), Map.of()));
        String dimensions = CODEC.write(new GroupDocument(ChecklistGroup.DIMENSIONS, dimensions(SPACE_1), Map.of(),
                Map.of()));

        assertTrue(terms.contains("\"amount\":12000000,"), terms);
        assertTrue(dimensions.contains("\"value\":45.5,"), dimensions);
        assertEquals(new BigDecimal("45.5"), ((DimensionsContent)
                CODEC.read(ChecklistGroup.DIMENSIONS, dimensions).content()).spaces().get(0).area().value());
    }

    @Test
    void snapshot_ofTheSevenGroups_roundTrips() {
        Map<ChecklistGroup, GroupState> groups = new EnumMap<>(ChecklistGroup.class);
        for (GroupContent content : contents()) {
            groups.put(content.group(), new GroupState(new GroupDocument(content.group(), content, Map.of(),
                    Map.of()), 3L, Instant.parse("2026-10-24T03:00:00Z"), SALE));
        }
        groups.put(ChecklistGroup.PARTS, new GroupState(GroupDocument.initial(ChecklistGroup.PARTS), 0L, null,
                null));

        assertEquals(groups, CODEC.readSnapshot(CODEC.writeSnapshot(groups)));
    }

    // ==================== helpers ====================

    private static IdentificationContent withPin() {
        IdentificationContent parts = identificationParts(observedSpace(SPACE_1, "P101"), observedSpace(SPACE_2, "P2"));
        return new IdentificationContent(SourcedField.ownerStated(new ObservedLocation("12 Bạch Đằng", "48", "20194",
                new BigDecimal("16.054456"), new BigDecimal("108.202167")), LocalDate.parse("2026-10-18"), SALE,
                "Ghi chú cuộc gọi"), "Khác số nhà", parts.propertyType(), parts.fit(), parts.rentalScope(),
                parts.spaces(), "Tầng thượng", "Ghi chú chung");
    }

    private static List<GroupContent> contents() {
        AmenitiesContent amenities = amenities(SPACE_1);
        return List.of(withPin(), parts(SPACE_1, SPACE_2), dimensions(SPACE_1),
                conditionParts(SPACE_1),
                safety(new Hazard("01JHAZARD00000000000000001", SPACE_1, new HazardLink("01JCHECK000000000000000001",
                        "01JHAZARD00000000000000009"), "Dây điện hở", HazardSeverity.IMPORTANT,
                        HazardStatus.OWNER_REPORTED_FIXED, "Chủ nói đã sửa", LocalDate.parse("2026-10-21"), SALE,
                        null)),
                new AmenitiesContent(amenities.sharedItems(), amenities.spaces(), List.of(new OtherItem(
                        "01JTEM000000000000000000A1", "Máy lọc nước", SPACE_1, YesNo.YES, ChecklistSource.OWNER_STATED,
                        LocalDate.parse("2026-10-19"), SALE, null))),
                new TermsContent(terms().common(), List.of(termsSpace(SPACE_1, new ChargeOverrides(
                        charge(4_000, ChargeUnit.PER_KWH), null, ownerSaid(null, LocalDate.parse("2026-10-19")), null,
                        null), SourcedField.of(List.of(new Fee("Phí thang máy", Money.vnd(30_000),
                        ChargeUnit.PER_MONTH)), ChecklistSource.OBSERVED_ON_SITE)))));
    }
}
