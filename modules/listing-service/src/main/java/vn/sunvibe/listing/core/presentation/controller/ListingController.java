package vn.sunvibe.listing.core.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.listing.core.application.usecase.ListingCommandUseCase;
import vn.sunvibe.listing.core.application.usecase.ListingQueryUseCase;
import vn.sunvibe.listing.core.presentation.converter.ListingRequestConverter;
import vn.sunvibe.listing.core.presentation.converter.ListingResponseConverter;
import vn.sunvibe.listing.core.presentation.dto.request.ListingCreateRequest;
import vn.sunvibe.listing.core.presentation.dto.response.AgreementListingsResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingCreatedResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingDetailResponse;
import vn.sunvibe.listing.core.presentation.resolver.ListingViewerResolver;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;

/**
 * Listings in the staff workspace (listing-publish US-1 AC-1 to AC-5; E06-S1 spec section 4, E06S1-D1, E06S1-D2,
 * E06S1-D8). {@code /api/v1/staff/**} has no path rule, so each operation carries its own role gate, which answers 403
 * for every id; the assignee and the scope are checked in the use cases, one 404 per caller for anything they may not
 * see, and the actor is the principal, never a body field (listing BR-16). Every path runs without open-in-view
 * (ADR-012 decision 8). Nothing here is public.
 */
@RestController
@RequestMapping(ListingController.PATH)
@RequiredArgsConstructor
@Tag(name = "Listing")
public class ListingController {

    public static final String PATH = "/api/v1/staff/listings";

    private final ListingCommandUseCase commands;
    private final ListingQueryUseCase queries;
    private final ListingViewerResolver viewers;
    private final ListingRequestConverter requests;
    private final ListingResponseConverter responses;

    /** listing-publish US-1 AC-1 to AC-4; only the agreement's assigned Sale (listing BR-16). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SALE','SALE_LEADER')")
    @Operation(operationId = "createListing",
            summary = "Create a listing under an agreement whose advertising answer allows it, for its assigned Sale")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The listing was created, Unpublished with revision 1"
                    + " Draft; its id and public code are answered"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED (one entry, field agreementId) or"
                    + " MALFORMED_REQUEST (the body cannot be read)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN: the caller holds neither SALE nor SALE_LEADER;"
                    + " the same for every agreement id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: the agreement does not exist, is of another"
                    + " transaction type, or the caller does not hold its basis lead now: one answer for all three",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "LISTING_NOT_ALLOWED (one reasonGroups.<GROUP> entry per"
                    + " group, in the order NO_AGREEMENT, NOT_IN_EFFECT, NOT_STARTED, SUSPENDED, ENDED, BASIS,"
                    + " PROPERTY, UNIT, OWNER), LISTING_EXISTS (the agreement already has a listing; no id is"
                    + " answered) or CONCURRENT_MODIFICATION",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ListingCreatedResponse create(@Valid @RequestBody ListingCreateRequest request) {
        return responses.toCreated(commands.create(requests.toCommand(request), viewers.current()));
    }

    /** listing BR-5: reviewers and managers read every listing, a SALE only while the assignee (E06S1-D8). */
    @GetMapping("/{listingId}")
    @PreAuthorize("hasAnyRole('SALE','SALE_LEADER','SALES_MANAGER','ADMIN','LISTING_REVIEWER')")
    @Operation(operationId = "getListing", summary = "A listing with its newest revisions; nothing of it is public")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The listing and at most its 20 newest revisions"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN: the caller holds none of SALE, SALE_LEADER,"
                    + " SALES_MANAGER, ADMIN, LISTING_REVIEWER; the same for every id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: the listing does not exist, the id is"
                    + " malformed, or the caller may not read it: one answer for all three",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ListingDetailResponse listing(@PathVariable("listingId") String listingId) {
        return responses.toDetail(queries.listing(listingId, viewers.current()));
    }

    /**
     * The agreement page's listing section: E05's own agreement scope (brokerage-agreement brief section 2), so
     * LISTING_REVIEWER is not admitted (E06S1-D8).
     */
    @GetMapping("/by-agreement/{agreementId}")
    @PreAuthorize("hasAnyRole('SALE','SALE_LEADER','SALES_MANAGER','ADMIN')")
    @Operation(operationId = "getAgreementListings",
            summary = "The listings of an agreement and whether the caller may create one now")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "At most the agreement's 20 newest listings and the"
                    + " creation part"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN: the caller holds none of SALE, SALE_LEADER,"
                    + " SALES_MANAGER, ADMIN (LISTING_REVIEWER alone included); the same for every id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: the agreement does not exist, the id is"
                    + " malformed, or the caller may not read it: one answer for all three",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR, also for a reason group of the"
                    + " advertising answer this server does not know",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AgreementListingsResponse ofAgreement(@PathVariable("agreementId") String agreementId) {
        return responses.toAgreementListings(queries.ofAgreement(agreementId, viewers.current()));
    }
}
