package vn.sunvibe.survey.core.infrastructure.persistence.json;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.survey.core.domain.model.checklist.AmenitiesContent;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsContent;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedPlaceNames;
import vn.sunvibe.survey.core.domain.model.checklist.PartsContent;
import vn.sunvibe.survey.core.domain.model.checklist.SafetyContent;
import vn.sunvibe.survey.core.domain.model.checklist.TermsContent;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupDocument;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;
import vn.sunvibe.survey.core.domain.model.checklist.unit.Tombstone;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitKey;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitMeta;
import vn.sunvibe.survey.core.domain.policy.checklist.CorrectedUnit;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The stored JSON of checklist group documents, takeover checkpoints and corrected units (survey-checklist spec A-3,
 * A-29, PD-1): {@code {"schema": 2, "content": {...}, "meta": {"<unitKey>": UnitMeta}, "tombstones": {"<unitKey>":
 * UnitMeta fields + "ownerReport"}, "place": ...}}. Schema 1, written before removed hazards kept their fix report's
 * receiver and date, is still read, its tombstones without report facts; nothing is rewritten. A mapper of its own
 * reads the domain records by their fields in declaration order and fails on unknown and missing keys, so a document
 * written by another template version is never half read; money stays a JSON integer and areas keep their decimal.
 * Stored data that cannot be read is a server fault, never a value in a message.
 */
@Component
public class ChecklistDocumentCodec {

    /** The document layout written here; a later template version writes another one. */
    static final int SCHEMA = 2;

    /** The layout before tombstones kept report facts (survey-checklist spec A-29): read, never written. */
    static final int SCHEMA_WITHOUT_REPORT_FACTS = 1;

    private static final Map<ChecklistGroup, Class<? extends GroupContent>> CONTENT = Map.of(
            ChecklistGroup.IDENTIFICATION, IdentificationContent.class, ChecklistGroup.PARTS, PartsContent.class,
            ChecklistGroup.DIMENSIONS, DimensionsContent.class, ChecklistGroup.CONDITION, ConditionContent.class,
            ChecklistGroup.SAFETY, SafetyContent.class, ChecklistGroup.AMENITIES, AmenitiesContent.class,
            ChecklistGroup.TERMS, TermsContent.class);

    private final JsonMapper mapper = JsonMapper.builder()
            .changeDefaultVisibility(visibility -> visibility
                    .withVisibility(PropertyAccessor.ALL, Visibility.NONE)
                    .withVisibility(PropertyAccessor.FIELD, Visibility.ANY)
                    .withVisibility(PropertyAccessor.CREATOR, Visibility.ANY))
            .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            // Content goes through a tree: decimals stay BigDecimal with their scale (pins 6, areas 1), never a
            // double, so a stored value compares equal to the same value sent again (survey-checklist spec A-17)
            .enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();

    /** The stored layout of a group document. */
    record StoredDocument(int schema, JsonNode content, Map<String, UnitMeta> meta,
                          Map<String, StoredTombstone> tombstones, ObservedPlaceNames place) {
    }

    /** The stored layout of a tombstone: the {@link UnitMeta} fields and a removed hazard's report facts or null. */
    record StoredTombstone(String enteredByUserId, Instant enteredAt, String observedByUserId, LocalDate observedOn,
                           String formField, ChecklistSource lastSource, Long correctionSaveId,
                           Tombstone.OwnerReportFacts ownerReport) {
    }

    /** The schema 1 layout of a group document: its tombstones are plain metadata. */
    record StoredDocumentWithoutReportFacts(int schema, JsonNode content, Map<String, UnitMeta> meta,
                                            Map<String, UnitMeta> tombstones, ObservedPlaceNames place) {
    }

    /** The stored layout of one group of a checkpoint. */
    record StoredGroup(long version, Instant updatedAt, String updatedByUserId, JsonNode document) {
    }

    /** The stored layout of a checkpoint: every stored group by its enum name. */
    record StoredSnapshot(int schema, Map<String, StoredGroup> groups) {
    }

    public String write(GroupDocument document) {
        return mapper.writeValueAsString(stored(document));
    }

    public GroupDocument read(ChecklistGroup group, String json) {
        try {
            return document(group, mapper.readTree(json));
        } catch (JacksonException e) {
            throw unreadable(e);
        }
    }

    public String writeSnapshot(Map<ChecklistGroup, GroupState> groups) {
        Map<String, StoredGroup> stored = new LinkedHashMap<>();
        groups.forEach((group, state) -> stored.put(group.name(), new StoredGroup(state.version(), state.updatedAt(),
                state.updatedByUserId(), mapper.valueToTree(stored(state.document())))));
        return mapper.writeValueAsString(new StoredSnapshot(SCHEMA, stored));
    }

    public Map<ChecklistGroup, GroupState> readSnapshot(String json) {
        try {
            StoredSnapshot snapshot = mapper.readValue(json, StoredSnapshot.class);
            requireSchema(snapshot.schema());
            Map<ChecklistGroup, GroupState> groups = new EnumMap<>(ChecklistGroup.class);
            snapshot.groups().forEach((name, group) -> {
                ChecklistGroup key = ChecklistGroup.valueOf(name);
                groups.put(key, new GroupState(document(key, group.document()), group.version(), group.updatedAt(),
                        group.updatedByUserId()));
            });
            return groups;
        } catch (JacksonException | IllegalArgumentException e) {
            throw unreadable(e);
        }
    }

    public String writeCorrectedUnits(List<CorrectedUnit> units) {
        return mapper.writeValueAsString(units);
    }

    public List<CorrectedUnit> readCorrectedUnits(String json) {
        try {
            return mapper.readValue(json, new TypeReference<List<CorrectedUnit>>() {
            });
        } catch (JacksonException e) {
            throw unreadable(e);
        }
    }

    private StoredDocument stored(GroupDocument document) {
        Map<String, StoredTombstone> tombstones = new LinkedHashMap<>();
        document.tombstones().forEach((key, tombstone) -> tombstones.put(key.value(), new StoredTombstone(
                tombstone.enteredByUserId(), tombstone.enteredAt(), tombstone.observedByUserId(),
                tombstone.observedOn(), tombstone.formField(), tombstone.lastSource(), tombstone.correctionSaveId(),
                tombstone.ownerReportOrNull())));
        return new StoredDocument(SCHEMA, mapper.valueToTree(document.content()), keys(document.meta()), tombstones,
                document.placeNamesOrNull());
    }

    /** A stored document in the layout its own {@code schema} names. */
    private GroupDocument document(ChecklistGroup group, JsonNode stored) {
        JsonNode schema = stored.get("schema");
        if (schema == null || !schema.isInt()) {
            throw new IllegalStateException("Stored checklist document has no schema.");
        }
        requireSchema(schema.intValue());
        Map<UnitKey, Tombstone> tombstones = new LinkedHashMap<>();
        if (schema.intValue() == SCHEMA_WITHOUT_REPORT_FACTS) {
            StoredDocumentWithoutReportFacts old = mapper.treeToValue(stored, StoredDocumentWithoutReportFacts.class);
            unitKeys(old.tombstones()).forEach((key, meta) -> tombstones.put(key, Tombstone.of(meta)));
            return new GroupDocument(group, content(group, old.content()), unitKeys(old.meta()), tombstones,
                    old.place());
        }
        StoredDocument document = mapper.treeToValue(stored, StoredDocument.class);
        document.tombstones().forEach((key, tombstone) -> tombstones.put(UnitKey.of(key), new Tombstone(
                tombstone.enteredByUserId(), tombstone.enteredAt(), tombstone.observedByUserId(),
                tombstone.observedOn(), tombstone.formField(), tombstone.lastSource(), tombstone.correctionSaveId(),
                tombstone.ownerReport())));
        return new GroupDocument(group, content(group, document.content()), unitKeys(document.meta()), tombstones,
                document.place());
    }

    private GroupContent content(ChecklistGroup group, JsonNode content) {
        return mapper.treeToValue(content, CONTENT.get(group));
    }

    private static void requireSchema(int schema) {
        if (schema != SCHEMA && schema != SCHEMA_WITHOUT_REPORT_FACTS) {
            throw new IllegalStateException("Stored checklist document has schema " + schema + ", expected "
                    + SCHEMA_WITHOUT_REPORT_FACTS + " or " + SCHEMA + ".");
        }
    }

    private static Map<String, UnitMeta> keys(Map<UnitKey, UnitMeta> meta) {
        Map<String, UnitMeta> stored = new LinkedHashMap<>();
        meta.forEach((key, value) -> stored.put(key.value(), value));
        return stored;
    }

    private static Map<UnitKey, UnitMeta> unitKeys(Map<String, UnitMeta> stored) {
        Map<UnitKey, UnitMeta> meta = new LinkedHashMap<>();
        stored.forEach((key, value) -> meta.put(UnitKey.of(key), value));
        return meta;
    }

    /** The cause names the broken token at most; stored values never reach the message. */
    private static IllegalStateException unreadable(Exception cause) {
        return new IllegalStateException("Stored checklist document cannot be read: " + cause.getClass()
                .getSimpleName(), null);
    }
}
