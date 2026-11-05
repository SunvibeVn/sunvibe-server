package vn.sunvibe.auth.core.testsupport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The identifier corpus shared with the web app (auth-email-access spec A-15), copied byte for byte: every case states
 * the raw input and what the policy must make of it.
 */
public final class IdentifierCorpus {

    public static final String RESOURCE = "/auth/identifier-corpus.json";

    private static final JsonNode ROOT = JsonMapper.builder().build().readTree(bytes());

    private IdentifierCorpus() {
    }

    /** One corpus line; {@code national} is null for every email case. */
    public record Case(String id, String raw, boolean valid, String canonical, String national, String note) {
    }

    public static List<Case> email() {
        return cases("email");
    }

    public static List<Case> phone() {
        return cases("phone");
    }

    public static byte[] bytes() {
        try (InputStream in = IdentifierCorpus.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource " + RESOURCE);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Case> cases(String section) {
        List<Case> cases = new ArrayList<>();
        for (JsonNode node : ROOT.get(section)) {
            cases.add(new Case(node.get("id").asString(), node.get("raw").asString(), node.get("valid").asBoolean(),
                    text(node, "canonical"), text(node, "national"), node.get("note").asString()));
        }
        return cases;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}
