package vn.sunvibe.listing.core.presentation.dto;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.presentation.dto.request.ListingFeaturesInput;
import vn.sunvibe.listing.core.presentation.dto.request.ListingRevisionContentRequest;
import vn.sunvibe.listing.core.presentation.dto.response.ListingDepositResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingFeaturesResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingFeeResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingPendingTermsResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingRentResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingRevisionDetailResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingTermsResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingTermsSnapshotResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingTermsSourcesResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingUnitSnapshotResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The twelve DTOs that carry unpublished text, E05's terms or the unit profile print their name and [hidden]
 * (listing-publish US-10 AC-4, BR-17; E06-S2 spec E06S2-D21; RD-16).
 */
class ListingRevisionDtoTest {

    private static final Instant NOW = Instant.parse("2026-11-20T03:15:00.123456Z");

    @Test
    void toString_dtosWithContentOrTerms_hidden() {
        ListingFeaturesInput featuresInput = new ListingFeaturesInput("FULL", true, true, true, "PRIVATE", "SHARED", true,
                true, true, true, true, "ALLOWED", true);
        ListingFeaturesResponse features = new ListingFeaturesResponse("FULL", true, true, true, "PRIVATE", "SHARED",
                true, true, true, true, true, "ALLOWED", true);
        ListingRentResponse rent = new ListingRentResponse(8_765_432L, "VND", "MONTH");
        ListingDepositResponse deposit = new ListingDepositResponse("AMOUNT", null, 7_654_321L, "VND");
        ListingFeeResponse fee = new ListingFeeResponse("secret-fee", "AMOUNT", 123_456L, "VND", "PER_KWH");
        ListingTermsResponse terms = new ListingTermsResponse(rent, deposit, 6, 1, List.of(fee));
        ListingTermsSnapshotResponse snapshot = new ListingTermsSnapshotResponse("IN_EFFECT", null, NOW, terms);
        ListingUnitSnapshotResponse unit = new ListingUnitSnapshotResponse("APARTMENT", "48", "Secret Province", "20194",
                "Secret Ward", 3, 2, 45.5);
        ListingPendingTermsResponse pending = new ListingPendingTermsResponse(LocalDate.parse("2026-12-01"), terms);
        Map<String, Object> dtos = new LinkedHashMap<>();
        dtos.put("ListingRevisionContentRequest", new ListingRevisionContentRequest(3L, "secret title",
                "secret description", featuresInput));
        dtos.put("ListingFeaturesInput", featuresInput);
        dtos.put("ListingRevisionDetailResponse", new ListingRevisionDetailResponse("01JLISTING0000000000000001", 1,
                "DRAFT", NOW, NOW, "secret title", "secret description", features, snapshot, unit, null, 3L, List.of()));
        dtos.put("ListingFeaturesResponse", features);
        dtos.put("ListingTermsSnapshotResponse", snapshot);
        dtos.put("ListingTermsResponse", terms);
        dtos.put("ListingRentResponse", rent);
        dtos.put("ListingDepositResponse", deposit);
        dtos.put("ListingFeeResponse", fee);
        dtos.put("ListingPendingTermsResponse", pending);
        dtos.put("ListingTermsSourcesResponse", new ListingTermsSourcesResponse(false, terms, pending));
        dtos.put("ListingUnitSnapshotResponse", unit);

        assertEquals(12, dtos.size());
        for (Map.Entry<String, Object> dto : dtos.entrySet()) {
            String text = dto.getValue().toString();
            assertEquals(dto.getKey() + "[hidden]", text);
            for (String value : List.of("secret", "Secret", "8765432", "7654321", "123456", "45.5", "2026", "FULL")) {
                assertFalse(text.contains(value), dto.getKey() + " " + value);
            }
        }
    }
}
