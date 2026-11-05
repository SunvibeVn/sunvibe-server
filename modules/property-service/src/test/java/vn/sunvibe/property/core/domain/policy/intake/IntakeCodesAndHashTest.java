package vn.sunvibe.property.core.domain.policy.intake;

import org.junit.jupiter.api.Test;
import vn.sunvibe.property.core.domain.model.intake.ScopeHash;
import vn.sunvibe.property.core.domain.policy.intake.PreviewHash.Inputs;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

/** The code, the phrase and the hashes of property-create intake A-5, A-14 and section 7.1. */
class IntakeCodesAndHashTest {

    private static final Inputs BASE = new Inputs("sha", List.of("s1", "s2"), "LINK", "p1", "c1",
            Map.of("s1", "u1", "s2", "u2"), Map.of("property", 1L, "u1", 2L), List.of("s1|VACANT|2026-11-06"), "sale", "proof");

    @Test
    void code_eightCrockfordCharacters() {
        String code = IntakeCodes.newCode();

        assertEquals(8, code.length());
        assertTrue(code.matches("[0-9A-HJKMNP-TV-Z]{8}"), code);
    }

    @Test
    void phrase_sixCrockfordCharacters() {
        assertTrue(IntakeCodes.newPhrase().matches("[0-9A-HJKMNP-TV-Z]{6}"));
    }

    /** A-5: the hash is salted by the row, so one code on two rows stores two hashes. */
    @Test
    void hash_saltedByRow() {
        assertNotEquals(IntakeCodes.hash("row1", "ABCD1234"), IntakeCodes.hash("row2", "ABCD1234"));
        assertEquals(64, IntakeCodes.hash("row1", "ABCD1234").length());
    }

    @Test
    void matches_rightAndWrong() {
        String stored = IntakeCodes.hash("row1", "ABCD1234");

        assertTrue(IntakeCodes.matches("row1", "ABCD1234", stored));
        assertFalse(IntakeCodes.matches("row1", "ABCD1235", stored));
        assertFalse(IntakeCodes.matches("row2", "ABCD1234", stored));
        assertFalse(IntakeCodes.matches("row1", null, stored));
    }

    /** A-14: read out by phone, so case does not count, O reads as 0 and I, L as 1. */
    @Test
    void phrase_foldsCaseAndAliases() {
        String stored = IntakeCodes.hash("row1", "0A1B1C");

        assertTrue(IntakeCodes.matches("row1", "oa1b1c", stored));
        assertTrue(IntakeCodes.matches("row1", "OAIBLC", stored));
        assertTrue(IntakeCodes.matches("row1", " 0a1b1c ", stored));
    }

    @Test
    void scopeHash_ignoresOrder() {
        assertEquals(ScopeHash.of(List.of("a", "b")), ScopeHash.of(List.of("b", "a")));
        assertNotEquals(ScopeHash.of(List.of("a", "b")), ScopeHash.of(List.of("a")));
    }

    /** section 7.1: a change to any input of the preview makes the old hash stale. */
    @Test
    void previewHash_changesOnEachInput() {
        String base = PreviewHash.of(BASE);
        List<UnaryOperator<Inputs>> changes = List.of(
                i -> new Inputs("other", i.selection(), i.targetMode(), i.propertyIdOrNull(), i.canonicalSpaceIdOrNull(),
                        i.mappings(), i.versions(), i.availability(), i.assignee(), i.proofId()),
                i -> new Inputs(i.sourceSha(), List.of("s1"), i.targetMode(), i.propertyIdOrNull(),
                        i.canonicalSpaceIdOrNull(), i.mappings(), i.versions(), i.availability(), i.assignee(), i.proofId()),
                i -> new Inputs(i.sourceSha(), i.selection(), "CREATE", i.propertyIdOrNull(), i.canonicalSpaceIdOrNull(),
                        i.mappings(), i.versions(), i.availability(), i.assignee(), i.proofId()),
                i -> new Inputs(i.sourceSha(), i.selection(), i.targetMode(), "p2", i.canonicalSpaceIdOrNull(), i.mappings(),
                        i.versions(), i.availability(), i.assignee(), i.proofId()),
                i -> new Inputs(i.sourceSha(), i.selection(), i.targetMode(), i.propertyIdOrNull(), "c2", i.mappings(),
                        i.versions(), i.availability(), i.assignee(), i.proofId()),
                i -> new Inputs(i.sourceSha(), i.selection(), i.targetMode(), i.propertyIdOrNull(),
                        i.canonicalSpaceIdOrNull(), Map.of("s1", "u9", "s2", "u2"), i.versions(), i.availability(),
                        i.assignee(), i.proofId()),
                i -> new Inputs(i.sourceSha(), i.selection(), i.targetMode(), i.propertyIdOrNull(),
                        i.canonicalSpaceIdOrNull(), i.mappings(), Map.of("property", 2L, "u1", 2L), i.availability(),
                        i.assignee(), i.proofId()),
                i -> new Inputs(i.sourceSha(), i.selection(), i.targetMode(), i.propertyIdOrNull(),
                        i.canonicalSpaceIdOrNull(), i.mappings(), i.versions(), List.of("s1|OCCUPIED|2026-11-06"),
                        i.assignee(), i.proofId()),
                i -> new Inputs(i.sourceSha(), i.selection(), i.targetMode(), i.propertyIdOrNull(),
                        i.canonicalSpaceIdOrNull(), i.mappings(), i.versions(), i.availability(), "other", i.proofId()),
                i -> new Inputs(i.sourceSha(), i.selection(), i.targetMode(), i.propertyIdOrNull(),
                        i.canonicalSpaceIdOrNull(), i.mappings(), i.versions(), i.availability(), i.assignee(), "proof2"));

        Set<String> hashes = new java.util.HashSet<>(Set.of(base));
        for (UnaryOperator<Inputs> change : changes) {
            assertTrue(hashes.add(PreviewHash.of(change.apply(BASE))), "an input left the hash as it was");
        }
    }

    @Test
    void previewHash_sameInputs_sameHash() {
        assertEquals(PreviewHash.of(BASE), PreviewHash.of(new Inputs("sha", List.of("s2", "s1"), "LINK", "p1", "c1",
                Map.of("s2", "u2", "s1", "u1"), Map.of("u1", 2L, "property", 1L), List.of("s1|VACANT|2026-11-06"), "sale",
                "proof")));
    }
}
