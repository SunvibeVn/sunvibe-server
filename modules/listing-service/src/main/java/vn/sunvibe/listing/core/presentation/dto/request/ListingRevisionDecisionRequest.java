package vn.sunvibe.listing.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.GroupSequence;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import vn.sunvibe.listing.core.domain.model.listing.ReviewNote;
import vn.sunvibe.listing.core.presentation.validation.StorableReviewNote;

import java.util.HashSet;
import java.util.List;

/**
 * A reviewer's decision on a revision (listing-publish US-4 AC-3, BR-7; E06-S4 spec section 4.3, E06S4-D6): the data
 * version the reviewer read, the outcome, and exactly the fields of that outcome. The codes travel as strings, each
 * checked against its closed list, so an unknown code is a field entry, never an unreadable body. The two lists are
 * bounded first, alone ({@link ListBounds}, the request's redefined default group sequence): a list above five elements
 * answers its own entry and nothing else. No actor and no time: the server sets both. Unknown properties are ignored.
 * Its text names no note, reason or group.
 */
@Schema(name = "ListingRevisionDecisionRequest")
@GroupSequence({ListingRevisionDecisionRequest.ListBounds.class, ListingRevisionDecisionRequest.class})
public record ListingRevisionDecisionRequest(
        @NotNull @PositiveOrZero
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "int64", minimum = "0",
                description = "The listing's data version the reviewer read (listing BR-3)")
        Long dataVersion,
        @NotNull @Pattern(regexp = "APPROVED|NEEDS_CHANGES|REJECTED")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"APPROVED", "NEEDS_CHANGES",
                "REJECTED"})
        String outcome,
        @Size(max = 5, groups = ListingRevisionDecisionRequest.ListBounds.class)
        @ArraySchema(maxItems = 5, schema = @Schema(allowableValues = {"CONTENT_MATCHES_UNIT",
                "TEXT_FOLLOWS_RULES", "TERMS_MATCH_AGREEMENT", "NO_FRAUD_SIGNS", "AGREEMENT_END_AND_RIGHT"}),
                arraySchema = @Schema(description = "APPROVED: each of the five checklist codes once, in any order;"
                        + " otherwise absent"))
        @Schema(nullable = true)
        List<@NotNull @Pattern(regexp = "CONTENT_MATCHES_UNIT|TEXT_FOLLOWS_RULES|TERMS_MATCH_AGREEMENT|NO_FRAUD_SIGNS"
                + "|AGREEMENT_END_AND_RIGHT") String> checks,
        @Size(max = 5, groups = ListingRevisionDecisionRequest.ListBounds.class)
        @ArraySchema(maxItems = 5, schema = @Schema(allowableValues = {"CONTENT_MISMATCH",
                "TEXT_RULE_VIOLATION", "MISSING_INFORMATION", "TERMS_MISMATCH", "OTHER"}),
                arraySchema = @Schema(description = "NEEDS_CHANGES: 1 to 5 distinct reasons; otherwise absent"))
        @Schema(nullable = true)
        List<@NotNull @Pattern(regexp = "CONTENT_MISMATCH|TEXT_RULE_VIOLATION|MISSING_INFORMATION|TERMS_MISMATCH"
                + "|OTHER") String> changeReasons,
        @Pattern(regexp = "SUSPECTED_FRAUD|WRONG_UNIT|SERIOUS_VIOLATION|OTHER")
        @Schema(nullable = true, allowableValues = {"SUSPECTED_FRAUD", "WRONG_UNIT", "SERIOUS_VIOLATION", "OTHER"},
                description = "REJECTED: required; otherwise absent")
        String rejectionGroup,
        @Size(max = ReviewNote.EDGE_UNITS) @StorableReviewNote
        @Schema(nullable = true, maxLength = ReviewNote.EDGE_UNITS,
                description = "Private. NEEDS_CHANGES and REJECTED: 1 to 1,000 characters (code points after NFC,"
                        + " trimmed), line feeds allowed, no other control or format character; APPROVED: absent;"
                        + " blank is none")
        String note) {

    /** E06S4-D6: at most five elements in either list, checked first and alone (the bound of the header's lists). */
    public static final int MAX_ELEMENTS = 5;

    /** The bounds of the two lists, the first group of the request's default sequence (E06S4-D6). */
    public interface ListBounds {
    }

    /**
     * listing-publish US-4 AC-3, BR-7 (E06S4-D6): exactly the fields of the outcome. Holds while the outcome is not one
     * of the three (that is the outcome's own entry). It counts elements and their distinctness; whether each element is
     * a known code is the element's own entry. An empty list and a blank note are none.
     */
    @AssertTrue
    private boolean isDecisionMatchesOutcome() {
        if (outcome == null) {
            return true;
        }
        return switch (outcome) {
            case "APPROVED" -> checks != null && checks.size() == MAX_ELEMENTS && distinct(checks)
                    && isEmpty(changeReasons) && rejectionGroup == null && !hasNote();
            case "NEEDS_CHANGES" -> !isEmpty(changeReasons) && distinct(changeReasons) && isEmpty(checks)
                    && rejectionGroup == null && hasNote();
            case "REJECTED" -> rejectionGroup != null && isEmpty(checks) && isEmpty(changeReasons) && hasNote();
            default -> true;
        };
    }

    /** A note above the edge bound is present (its own entry answers it) and is never normalized. */
    private boolean hasNote() {
        return note != null && (note.length() > ReviewNote.EDGE_UNITS || ReviewNote.normalized(note) != null);
    }

    private static boolean isEmpty(List<String> valuesOrNull) {
        return valuesOrNull == null || valuesOrNull.isEmpty();
    }

    private static boolean distinct(List<String> values) {
        return new HashSet<>(values).size() == values.size();
    }

    /** listing BR-2, E06S4-D20: never the note, a reason or a group. */
    @Override
    public String toString() {
        return "ListingRevisionDecisionRequest[hidden]";
    }
}
