package vn.sunvibe.property.core.infrastructure.persistence.json;

import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.application.shared.external.snapshot.OwnerSnapshotCodec;
import vn.sunvibe.property.core.application.usecase.intake.OwnerRequestSnapshot;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.property.core.domain.model.intake.AvailabilityItem;
import vn.sunvibe.property.core.domain.model.intake.SelectedSpace;
import vn.sunvibe.property.core.domain.model.intake.SpaceUnitMapping;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The stored JSON of an intake row (property-create intake spec 5.2 file 5): the records themselves, by their component
 * names. Stored data that cannot be read is a server fault, never a value in a message.
 */
@Component
public class IntakeJsonCodec implements OwnerSnapshotCodec {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<SelectedSpace>> SELECTION = new TypeReference<>() {
    };
    private static final TypeReference<List<SpaceUnitMapping>> MAPPINGS = new TypeReference<>() {
    };
    private static final TypeReference<List<AvailabilityItem>> AVAILABILITY = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Long>> VERSIONS = new TypeReference<>() {
    };
    private static final TypeReference<OwnerRequestSnapshot> SNAPSHOT = new TypeReference<>() {
    };

    @Override
    public String write(OwnerRequestSnapshot snapshot) {
        return write((Object) snapshot);
    }

    @Override
    public OwnerRequestSnapshot read(String stored) {
        return read(stored, SNAPSHOT);
    }

    public String selection(List<SelectedSpace> selection) {
        return write(selection);
    }

    public List<SelectedSpace> selection(String json) {
        return read(json, SELECTION);
    }

    public String mappings(List<SpaceUnitMapping> mappings) {
        return write(mappings);
    }

    public List<SpaceUnitMapping> mappings(String json) {
        return json == null ? List.of() : read(json, MAPPINGS);
    }

    /** An empty list is stored as no value: a cleared row holds nothing (property-create intake spec 4.1). */
    public String availability(List<AvailabilityItem> items) {
        return items.isEmpty() ? null : write(items);
    }

    public List<AvailabilityItem> availability(String json) {
        return json == null ? List.of() : read(json, AVAILABILITY);
    }

    public String versions(Map<String, Long> versions) {
        return versions.isEmpty() ? null : write(new TreeMap<>(versions));
    }

    public Map<String, Long> versions(String json) {
        return json == null ? Map.of() : read(json, VERSIONS);
    }

    private static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("An intake value could not be written.");
        }
    }

    private static <T> T read(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JacksonException e) {
            throw new IllegalStateException("A stored intake value could not be read.");
        }
    }
}
