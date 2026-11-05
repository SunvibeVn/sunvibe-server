package vn.sunvibe.property.core.domain.policy.intake;

import vn.sunvibe.property.core.domain.model.intake.ScopeHash;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The hash a completion must name (property-create intake spec 7.1, US-6 AC-1): a change to the source, selection, target,
 * a property or unit version, availability, assignee or proof makes an old preview stale.
 */
public final class PreviewHash {

    private static final String SEPARATOR = "\u001F";

    private PreviewHash() {
    }

    /**
     * @param selection    the selected space ids
     * @param mappings     space id to unit id, on a link
     * @param versions     property and unit versions the preview read
     * @param availability one stable line per availability item
     */
    public record Inputs(String sourceSha, List<String> selection, String targetMode, String propertyIdOrNull,
                         String canonicalSpaceIdOrNull, Map<String, String> mappings, Map<String, Long> versions,
                         List<String> availability, String assignee, String proofId) {

        @Override
        public String toString() {
            return "PreviewHash.Inputs[hidden]";
        }
    }

    public static String of(Inputs inputs) {
        return ScopeHash.sha256(String.join(SEPARATOR, inputs.sourceSha(), sorted(inputs.selection()),
                String.valueOf(inputs.targetMode()), String.valueOf(inputs.propertyIdOrNull()),
                String.valueOf(inputs.canonicalSpaceIdOrNull()), new TreeMap<>(inputs.mappings()).toString(),
                new TreeMap<>(inputs.versions()).toString(), sorted(inputs.availability()), inputs.assignee(),
                inputs.proofId()));
    }

    private static String sorted(List<String> values) {
        return String.join(",", values.stream().sorted().toList());
    }
}
