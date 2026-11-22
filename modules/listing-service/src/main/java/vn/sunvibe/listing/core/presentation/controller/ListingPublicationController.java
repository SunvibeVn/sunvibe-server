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
import vn.sunvibe.listing.core.application.usecase.ListingPublicationCommandUseCase;
import vn.sunvibe.listing.core.application.usecase.ListingPublicationQueryUseCase;
import vn.sunvibe.listing.core.presentation.converter.ListingRequestConverter;
import vn.sunvibe.listing.core.presentation.converter.ListingResponseConverter;
import vn.sunvibe.listing.core.presentation.dto.request.ListingRevisionCommandRequest;
import vn.sunvibe.listing.core.presentation.dto.response.ListingLiveDifferenceResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingPublicationResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingRevisionChangeResponse;
import vn.sunvibe.listing.core.presentation.resolver.ListingViewerResolver;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;

/**
 * The publication of a listing (listing-publish US-2 AC-7, US-4 AC-2, US-5 AC-1, AC-3, AC-4, BR-11; E06-S6 spec section
 * 4, E06S6-D2, D5, D11, D12). Each operation carries the role gate of the operation it sits beside, which answers 403 for
 * every id: the put live the drafting commands', the publication read the listing read's, the difference the review
 * read's; the assignee and the scope are checked in the use cases, and the actor is the principal, never a body field.
 * Every path runs without open-in-view (ADR-012 decision 8). Nothing here is public.
 */
@RestController
@RequestMapping(ListingController.PATH)
@RequiredArgsConstructor
@Tag(name = "Listing")
public class ListingPublicationController {

    private static final String BAD_BODY = "VALIDATION_FAILED (one entry per field: dataVersion missing or negative)"
            + " or MALFORMED_REQUEST (the body cannot be read); answered before the role";
    private static final String UNKNOWN_REVISION = "NOT_FOUND: the listing does not exist, an id or the revision number"
            + " is malformed, or the listing has no such revision: one answer for all";

    private final ListingPublicationCommandUseCase commands;
    private final ListingPublicationQueryUseCase queries;
    private final ListingViewerResolver viewers;
    private final ListingRequestConverter requests;
    private final ListingResponseConverter responses;

    /**
     * listing-publish US-5 AC-1, AC-3, AC-4, E-8: the assigned Sale puts the Approved revision live when every display
     * condition holds now (E06S6-D5); the drafting commands' gate (E06S6-D2).
     */
    @PostMapping("/{listingId}/revisions/{revisionNo}/publication")
    @PreAuthorize("hasAnyRole('SALE','SALE_LEADER')")
    @Operation(operationId = "putListingRevisionLive",
            summary = "Put an Approved revision live when every display condition holds now")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Live: status LIVE and the listing's new data version; the"
                    + " former live revision is Replaced and an Unpublished listing Published"),
            @ApiResponse(responseCode = "400", description = BAD_BODY,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN: the caller holds neither SALE nor SALE_LEADER;"
                    + " the same for every id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: the listing does not exist, an id or the"
                    + " revision number is malformed, the caller does not hold the agreement's basis lead now, or the"
                    + " listing has no such revision: one answer for all",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "LISTING_ARCHIVED, CONCURRENT_MODIFICATION (the data"
                    + " version is stale, or another command moved it between the command's reads),"
                    + " REVISION_NOT_APPROVED or PUT_LIVE_REFUSED (one entry per reason that stops it, in the order"
                    + " reasons.LISTING_STATE, reasons.ADVERTISING_RIGHT, reasons.TERMS_NOT_IN_EFFECT,"
                    + " reasons.UNIT_NOT_AVAILABLE, reasons.FRESHNESS, each reasonGroups.<GROUP> of E05 right after"
                    + " reasons.ADVERTISING_RIGHT; nothing is written)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR (also for an E05 group, an availability"
                    + " code or terms this server does not know or that break the contract; nothing is written)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ListingRevisionChangeResponse putLive(@PathVariable("listingId") String listingId,
                                                 @PathVariable("revisionNo") String revisionNo,
                                                 @Valid @RequestBody ListingRevisionCommandRequest request) {
        return responses.toChange(commands.putLive(
                requests.toPutLiveCommand(listingId, ListingRequestConverter.revisionNoOrNull(revisionNo), request),
                viewers.current()));
    }

    /**
     * listing-publish US-2 AC-7, US-5 AC-3, BR-11 (E06S6-D11): the listing read's gate and scope; a hint, the command
     * decides.
     */
    @GetMapping("/{listingId}/publication")
    @PreAuthorize("hasAnyRole('SALE','SALE_LEADER','SALES_MANAGER','ADMIN','LISTING_REVIEWER')")
    @Operation(operationId = "getListingPublication",
            summary = "The live revision and the Approved revision waiting, each with whether its display conditions"
                    + " hold now, and what the caller may do")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The publication; a listing read as archived has neither"
                    + " revision and permits nothing"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN: the caller holds none of SALE, SALE_LEADER,"
                    + " SALES_MANAGER, ADMIN, LISTING_REVIEWER; the same for every id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: the listing does not exist, the id is"
                    + " malformed, or the caller may not read it: one answer for all three",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR, also for a reason group of the"
                    + " advertising answer, an availability of the unit or terms this server does not know; nothing"
                    + " is shown",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ListingPublicationResponse publication(@PathVariable("listingId") String listingId) {
        return responses.toPublication(queries.publication(listingId, viewers.current()));
    }

    /** listing-publish US-4 AC-2, BR-7 (E06S6-D12): the review read's gate and scope. */
    @GetMapping("/{listingId}/revisions/{revisionNo}/live-difference")
    @PreAuthorize("hasAnyRole('SALE_LEADER','SALES_MANAGER','ADMIN','LISTING_REVIEWER')")
    @Operation(operationId = "getListingRevisionLiveDifference",
            summary = "What a revision changes against the live revision: its title, description, features and terms"
                    + " by value")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "COMPARED with the live revision and the parts that"
                    + " differ, NO_LIVE_REVISION or IS_LIVE_REVISION"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN: the caller holds none of SALE_LEADER,"
                    + " SALES_MANAGER, ADMIN, LISTING_REVIEWER (SALE alone included); the same for every id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = UNKNOWN_REVISION,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR (also for a live revision read without its"
                    + " terms snapshot)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ListingLiveDifferenceResponse liveDifference(@PathVariable("listingId") String listingId,
                                                        @PathVariable("revisionNo") String revisionNo) {
        return responses.toLiveDifference(queries.liveDifference(listingId,
                ListingRequestConverter.revisionNoOrNull(revisionNo), viewers.current()));
    }
}
