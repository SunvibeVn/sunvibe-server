package vn.sunvibe.listing.core.domain.model.listing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The checklist an approval confirms (listing-publish BR-7; E06-S4 spec E06S4-D6): version 1, five codes in order. */
class ReviewCheckTest {

    @Test
    void versionOne_fiveCodesInOrder() {
        assertEquals(1, ReviewCheck.CHECKLIST_VERSION);
        assertEquals(List.of("CONTENT_MATCHES_UNIT", "TEXT_FOLLOWS_RULES", "TERMS_MATCH_AGREEMENT", "NO_FRAUD_SIGNS",
                "AGREEMENT_END_AND_RIGHT"), ReviewCheck.VERSION_1.stream().map(Enum::name).toList());
        assertEquals(List.of("CONTENT_MISMATCH", "TEXT_RULE_VIOLATION", "MISSING_INFORMATION", "TERMS_MISMATCH",
                "OTHER"), List.of(ChangeReason.values()).stream().map(Enum::name).toList());
        assertEquals(List.of("SUSPECTED_FRAUD", "WRONG_UNIT", "SERIOUS_VIOLATION", "OTHER"),
                List.of(RejectionGroup.values()).stream().map(Enum::name).toList());
        assertEquals(List.of("NOT_A_REVIEWER", "AUTHOR", "ASSIGNED_SALE", "NOT_PENDING", "LISTING_ARCHIVED"),
                List.of(ReviewIneligibility.values()).stream().map(Enum::name).toList());
    }
}
