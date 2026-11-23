package vn.sunvibe.listing.core.presentation.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The shape of a decision at the edge (listing-publish US-4 AC-3, BR-7; E06-S4 spec E06S4-D6, RV-6): every broken shape
 * answers the entries of E06S4-D6's table, read as {@code ListingExceptionHandler.handleInvalidBody} reads them (the
 * field names Spring derives from the violations, each once); a list above five elements answers its own entry and
 * nothing else; the note never reaches the request's text.
 */
class ListingRevisionDecisionRequestTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();
    private static final List<String> FIVE_CHECKS = List.of("CONTENT_MATCHES_UNIT", "TEXT_FOLLOWS_RULES",
            "TERMS_MATCH_AGREEMENT", "NO_FRAUD_SIGNS", "AGREEMENT_END_AND_RIGHT");
    private static final String NOTE = "Mô tả thiếu diện tích phòng.";
    private static final String MATCH = "decisionMatchesOutcome";

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    static Stream<Arguments> brokenShapes() {
        return Stream.of(
                Arguments.of("dataVersion missing", new ListingRevisionDecisionRequest(null, "APPROVED", FIVE_CHECKS,
                        null, null, null), List.of("dataVersion")),
                Arguments.of("dataVersion negative", new ListingRevisionDecisionRequest(-1L, "APPROVED", FIVE_CHECKS,
                        null, null, null), List.of("dataVersion")),
                Arguments.of("outcome missing", new ListingRevisionDecisionRequest(3L, null, FIVE_CHECKS, null, null,
                        null), List.of("outcome")),
                Arguments.of("outcome PUBLISHED", new ListingRevisionDecisionRequest(3L, "PUBLISHED", FIVE_CHECKS, null,
                        null, null), List.of("outcome")),
                Arguments.of("approved four checks", approved(FIVE_CHECKS.subList(0, 4)), List.of(MATCH)),
                Arguments.of("approved a check twice", approved(List.of("CONTENT_MATCHES_UNIT", "TEXT_FOLLOWS_RULES",
                        "TERMS_MATCH_AGREEMENT", "NO_FRAUD_SIGNS", "NO_FRAUD_SIGNS")), List.of(MATCH)),
                Arguments.of("approved with a note", new ListingRevisionDecisionRequest(3L, "APPROVED", FIVE_CHECKS,
                        null, null, NOTE), List.of(MATCH)),
                Arguments.of("approved with reasons", new ListingRevisionDecisionRequest(3L, "APPROVED", FIVE_CHECKS,
                        List.of("OTHER"), null, null), List.of(MATCH)),
                Arguments.of("approved an unknown third check", approved(List.of("CONTENT_MATCHES_UNIT",
                        "TEXT_FOLLOWS_RULES", "PHOTOS_OK", "NO_FRAUD_SIGNS", "AGREEMENT_END_AND_RIGHT")),
                        List.of("checks[2]")),
                Arguments.of("returned without reasons", returned(null, NOTE), List.of(MATCH)),
                Arguments.of("returned with no reason", returned(List.of(), NOTE), List.of(MATCH)),
                Arguments.of("returned a reason twice", returned(List.of("OTHER", "OTHER"), NOTE), List.of(MATCH)),
                Arguments.of("returned without a note", returned(List.of("OTHER"), null), List.of(MATCH)),
                Arguments.of("returned a blank note", returned(List.of("OTHER"), " \r\n "), List.of(MATCH)),
                Arguments.of("returned a photo reason", returned(List.of("PHOTO_NOT_ACCEPTED"), NOTE),
                        List.of("changeReasons[0]")),
                Arguments.of("rejected without a group", rejected(null, null, NOTE), List.of(MATCH)),
                Arguments.of("rejected with checks", rejected("WRONG_UNIT", FIVE_CHECKS, NOTE), List.of(MATCH)),
                Arguments.of("rejected SPAM", rejected("SPAM", null, NOTE), List.of("rejectionGroup")),
                Arguments.of("note of 1,001 code points", returned(List.of("OTHER"), "a".repeat(1_001)),
                        List.of("note")),
                Arguments.of("note of 4,001 UTF-16 units", returned(List.of("OTHER"), "a".repeat(4_001)),
                        List.of("note")),
                Arguments.of("note with U+0007", returned(List.of("OTHER"), "Thiếu\u0007ảnh"), List.of("note")),
                Arguments.of("approved six checks", approved(Stream.concat(FIVE_CHECKS.stream(),
                        Stream.of("NO_FRAUD_SIGNS")).toList()), List.of("checks")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenShapes")
    void validate_brokenShape_entriesAsTable(String name, ListingRevisionDecisionRequest request,
                                             List<String> entries) {
        assertEquals(entries, entries(request), name);
    }

    @Test
    void validate_listsAboveFive_onlyBoundEntries() {
        List<String> sixUnknown = List.of("A", "B", "C", "D", "E", "F");
        List<String> sixReasons = List.of("CONTENT_MISMATCH", "TEXT_RULE_VIOLATION", "MISSING_INFORMATION",
                "TERMS_MISMATCH", "OTHER", "OTHER");

        assertEquals(List.of("checks"),
                entries(new ListingRevisionDecisionRequest(null, "APPROVED", sixUnknown, null, null, null)));
        assertEquals(List.of("changeReasons"),
                entries(new ListingRevisionDecisionRequest(3L, "NEEDS_CHANGES", null, sixReasons, null, NOTE)));
        // both lists above five, every other rule broken too: the two bound entries and nothing else
        assertEquals(Set.of("checks", "changeReasons"), Set.copyOf(entries(new ListingRevisionDecisionRequest(null,
                "PUBLISHED", sixUnknown, sixReasons, "SPAM", "\u0007"))));
        assertEquals(2, entries(new ListingRevisionDecisionRequest(null, "PUBLISHED", sixUnknown, sixReasons, "SPAM",
                "\u0007")).size());
    }

    @Test
    void validate_validShapes_noViolation() {
        assertEquals(List.of(), entries(approved(FIVE_CHECKS)));
        assertEquals(List.of(), entries(approved(FIVE_CHECKS.reversed())));
        assertEquals(List.of(), entries(new ListingRevisionDecisionRequest(0L, "APPROVED", FIVE_CHECKS, List.of(),
                null, "  ")));
        assertEquals(List.of(), entries(returned(List.of("TERMS_MISMATCH", "CONTENT_MISMATCH", "TEXT_RULE_VIOLATION",
                "MISSING_INFORMATION", "OTHER"), NOTE)));
        assertEquals(List.of(), entries(returned(List.of("OTHER"), "a".repeat(1_000))));
        assertEquals(List.of(), entries(rejected("SUSPECTED_FRAUD", null, "Dòng một\r\nDòng hai")));
        assertEquals(List.of(), entries(rejected("OTHER", List.of(), NOTE)));
    }

    @Test
    void toString_namesNoNote() {
        ListingRevisionDecisionRequest request = rejected("WRONG_UNIT", null, "SECRET-NOTE");

        assertEquals("ListingRevisionDecisionRequest[hidden]", request.toString());
        assertFalse(request.toString().contains("SECRET-NOTE"));
    }

    private static ListingRevisionDecisionRequest approved(List<String> checks) {
        return new ListingRevisionDecisionRequest(3L, "APPROVED", checks, null, null, null);
    }

    private static ListingRevisionDecisionRequest returned(List<String> reasons, String note) {
        return new ListingRevisionDecisionRequest(3L, "NEEDS_CHANGES", null, reasons, null, note);
    }

    private static ListingRevisionDecisionRequest rejected(String group, List<String> checks, String note) {
        return new ListingRevisionDecisionRequest(3L, "REJECTED", checks, null, group, note);
    }

    /**
     * The entries of the 400: each violation's field as Spring's validator adapter names it (a property, then
     * {@code [i]} for an element of a list), each once; sorted, since Bean Validation reports in no fixed order.
     */
    private static List<String> entries(ListingRevisionDecisionRequest request) {
        Set<String> fields = new LinkedHashSet<>();
        for (ConstraintViolation<ListingRevisionDecisionRequest> violation : VALIDATOR.validate(request)) {
            fields.add(fieldOf(violation.getPropertyPath()));
        }
        List<String> sorted = new ArrayList<>(fields);
        sorted.sort(null);
        return sorted;
    }

    private static String fieldOf(Path path) {
        StringBuilder field = new StringBuilder();
        boolean first = true;
        for (Path.Node node : path) {
            if (node.isInIterable() && !first) {
                field.append('[').append(node.getIndex() == null ? node.getKey() : node.getIndex()).append(']');
            }
            String name = node.getName();
            if (name != null && node.getKind() == ElementKind.PROPERTY && !name.startsWith("<")) {
                if (!first) {
                    field.append('.');
                }
                first = false;
                field.append(name);
            }
        }
        return field.toString();
    }
}
