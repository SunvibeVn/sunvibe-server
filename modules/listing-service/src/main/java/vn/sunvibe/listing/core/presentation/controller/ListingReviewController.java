package vn.sunvibe.listing.core.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.listing.core.application.usecase.ListingReviewCommandUseCase;
import vn.sunvibe.listing.core.application.usecase.ListingReviewQueryUseCase;
import vn.sunvibe.listing.core.presentation.converter.ListingRequestConverter;
import vn.sunvibe.listing.core.presentation.converter.ListingResponseConverter;
import vn.sunvibe.listing.core.presentation.dto.request.ListingRevisionDecisionRequest;
import vn.sunvibe.listing.core.presentation.dto.response.ListingReviewQueueResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingRevisionChangeResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingRevisionDecisionResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingRevisionReviewResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingUnitLocationResponse;
import vn.sunvibe.listing.core.presentation.resolver.ListingViewerResolver;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;

/**
 * The independent review of a submitted revision (listing-publish US-4 AC-1 to AC-6, BR-6, BR-7; E06-S4 spec section 4,
 * E06S4-D1, E06S4-D2). Each operation carries its own role gate, which answers 403 for every id; the independence from
 * the authors and the assigned Sale, the scope and the revision's state are checked in the use cases, and the actor is
 * the principal, never a body field. Every path runs without open-in-view (ADR-012 decision 8). Nothing here is public.
 */
@RestController
@RequestMapping(ListingController.PATH)
@RequiredArgsConstructor
@Tag(name = "Listing")
public class ListingReviewController {

    private static final String REVIEW_READERS = "FORBIDDEN: the caller holds none of SALE_LEADER, SALES_MANAGER,"
            + " ADMIN, LISTING_REVIEWER (SALE alone included); the same for every id";
    private static final String DECIDERS = "FORBIDDEN: the caller holds neither LISTING_REVIEWER nor ADMIN; the same"
            + " for every id. REVIEWER_NOT_INDEPENDENT: the caller is an author of the revision or its agreement's"
            + " assigned Sale now, whatever roles they hold; one entry per reason, reasons.AUTHOR then"
            + " reasons.ASSIGNED_SALE";
    private static final String UNKNOWN = "NOT_FOUND: the listing does not exist, an id or the revision number is"
            + " malformed, or the listing has no such revision: one answer for all";

    private final ListingReviewCommandUseCase commands;
    private final ListingReviewQueryUseCase queries;
    private final ListingViewerResolver viewers;
    private final ListingRequestConverter requests;
    private final ListingResponseConverter responses;

    /** listing-publish US-4 AC-1: the roles that read every listing (E06S4-D2, E06S4-D10). */
    @GetMapping("/review-queue")
    @PreAuthorize("hasAnyRole('SALE_LEADER','SALES_MANAGER','ADMIN','LISTING_REVIEWER')")
    @Operation(operationId = "getListingReviewQueue",
            summary = "The revisions waiting for review, oldest submission first, with whether the caller may review"
                    + " each")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "At most 50 Pending review revisions of listings not"
                    + " archived, oldest submission first, and whether more wait"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = REVIEW_READERS,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ListingReviewQueueResponse queue() {
        return responses.toReviewQueue(queries.queue(viewers.current()));
    }

    /**
     * listing-publish US-4 AC-2, AC-4, AC-5 (E06S4-D11): the review context of any revision; the content is the revision
     * read's, the real location is opened apart.
     */
    @GetMapping("/{listingId}/revisions/{revisionNo}/review")
    @PreAuthorize("hasAnyRole('SALE_LEADER','SALES_MANAGER','ADMIN','LISTING_REVIEWER')")
    @Operation(operationId = "getListingRevisionReview",
            summary = "What a reviewer reads beside a revision: eligibility, the advertising answer, the terms check,"
                    + " the unit, the freshness and the checklist")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The review context; never the real location"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = REVIEW_READERS,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = UNKNOWN,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR, also for a reason group of the"
                    + " advertising answer, an availability of the unit or terms this server does not know; nothing"
                    + " is shown",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ListingRevisionReviewResponse review(@PathVariable("listingId") String listingId,
                                                @PathVariable("revisionNo") String revisionNo) {
        return responses.toRevisionReview(queries.review(listingId, ListingRequestConverter.revisionNoOrNull(revisionNo),
                viewers.current()));
    }

    /** listing-publish US-4 AC-3 to AC-5, BR-6, BR-7: the one decision of a Pending review revision (E06S4-D5 to D9). */
    @PostMapping("/{listingId}/revisions/{revisionNo}/decision")
    @PreAuthorize("hasAnyRole('LISTING_REVIEWER','ADMIN')")
    @Operation(operationId = "decideListingRevision",
            summary = "Approve, return or reject a Pending review revision; an approval tries to put it live")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Decided: status is the revision's status after the"
                    + " command (APPROVED, or LIVE when the approval put it live; NEEDS_CHANGES or REJECTED) and"
                    + " dataVersion the listing's new data version"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED (one entry per broken rule:"
                    + " dataVersion, outcome, checks or changeReasons alone for a list above five elements, checks[i],"
                    + " changeReasons[i], rejectionGroup, note, or decisionMatchesOutcome when the fields are not"
                    + " exactly those of the outcome) or MALFORMED_REQUEST (the body cannot be read); answered before"
                    + " the role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = DECIDERS,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = UNKNOWN,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "LISTING_ARCHIVED, CONCURRENT_MODIFICATION (the data"
                    + " version is stale, or another command moved it meanwhile), REVISION_NOT_PENDING or"
                    + " REVISION_TERMS_MISMATCH (an approval of terms equal to no version of the agreement)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: the agreement's terms break the"
                    + " contract, or the put live meets an E05 group or availability code listing does not know;"
                    + " nothing is written",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ListingRevisionChangeResponse decide(@PathVariable("listingId") String listingId,
                                                @PathVariable("revisionNo") String revisionNo,
                                                @Valid @RequestBody ListingRevisionDecisionRequest request) {
        return responses.toChange(commands.decide(
                requests.toDecisionCommand(listingId, ListingRequestConverter.revisionNoOrNull(revisionNo), request),
                viewers.current()));
    }

    /**
     * listing-publish US-4 AC-6 (E06S4-D15): the decision for whoever reads the listing, a SALE only while the
     * assignee; never who decided.
     */
    @GetMapping("/{listingId}/revisions/{revisionNo}/decision")
    @PreAuthorize("hasAnyRole('SALE','SALE_LEADER','SALES_MANAGER','ADMIN','LISTING_REVIEWER')")
    @Operation(operationId = "getListingRevisionDecision",
            summary = "The decision of a revision with its reasons and note; no reviewer id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The decision"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN: the caller holds none of SALE, SALE_LEADER,"
                    + " SALES_MANAGER, ADMIN, LISTING_REVIEWER; the same for every id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: the listing does not exist, an id or the"
                    + " revision number is malformed, the caller may not read the listing, it has no such revision, or"
                    + " the revision has no decision: one answer for all",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ListingRevisionDecisionResponse decision(@PathVariable("listingId") String listingId,
                                                    @PathVariable("revisionNo") String revisionNo) {
        return responses.toRevisionDecision(queries.decision(listingId,
                ListingRequestConverter.revisionNoOrNull(revisionNo), viewers.current()));
    }

    /**
     * listing-publish US-4 AC-2 (E06S4-D12): the real location, only to a caller who may decide the revision now; each
     * opening writes one access log row with no location value.
     */
    @PostMapping("/{listingId}/revisions/{revisionNo}/unit-location")
    @PreAuthorize("hasAnyRole('LISTING_REVIEWER','ADMIN')")
    @Operation(operationId = "openListingUnitLocation",
            summary = "Open the real location of a Pending review revision's unit; the opening is logged")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The unit's code, its location label, the street and the"
                    + " ward; one access log row is written"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = DECIDERS,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = UNKNOWN,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "LISTING_ARCHIVED or REVISION_NOT_PENDING",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: the unit's location cannot be read;"
                    + " nothing is logged",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ListingUnitLocationResponse openUnitLocation(@PathVariable("listingId") String listingId,
                                                        @PathVariable("revisionNo") String revisionNo) {
        return responses.toUnitLocation(commands.openUnitLocation(
                requests.toLocationCommand(listingId, ListingRequestConverter.revisionNoOrNull(revisionNo)),
                viewers.current()));
    }
}
