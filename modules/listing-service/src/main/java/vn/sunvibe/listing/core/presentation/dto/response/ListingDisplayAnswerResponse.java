package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Whether a revision's display conditions hold now (listing-publish BR-11; E06-S6 spec E06S6-D3, section 4.2): the
 * reasons that stop it in their fixed order, and E05's groups when the advertising right is one of them. Internal to
 * staff; its text names neither.
 */
@Schema(name = "ListingDisplayAnswer")
public record ListingDisplayAnswerResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "True exactly when reasons is empty")
        boolean holds,
        @ArraySchema(schema = @Schema(allowableValues = {"LISTING_STATE", "ADVERTISING_RIGHT", "TERMS_NOT_IN_EFFECT",
                "UNIT_NOT_AVAILABLE", "FRESHNESS"}), arraySchema = @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED, description = "What stops it, in this order"))
        List<String> reasons,
        @ArraySchema(schema = @Schema(allowableValues = {"NO_AGREEMENT", "NOT_IN_EFFECT", "NOT_STARTED", "SUSPENDED",
                "ENDED", "BASIS", "PROPERTY", "UNIT", "OWNER"}), arraySchema = @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "E05's reason groups, in E05's order; empty unless reasons holds ADVERTISING_RIGHT"))
        List<String> reasonGroups) {

    /** Its text names no reason or group (listing-publish BR-17), as {@link ListingReviewAdvertisingResponse} does. */
    @Override
    public String toString() {
        return "ListingDisplayAnswerResponse[hidden]";
    }
}
