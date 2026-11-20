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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.listing.core.application.usecase.ListingCommandUseCase;
import vn.sunvibe.listing.core.application.usecase.ListingQueryUseCase;
import vn.sunvibe.listing.core.presentation.converter.ListingRequestConverter;
import vn.sunvibe.listing.core.presentation.converter.ListingResponseConverter;
import vn.sunvibe.listing.core.presentation.dto.request.ListingCreateRequest;
import vn.sunvibe.listing.core.presentation.dto.request.ListingRevisionCommandRequest;
import vn.sunvibe.listing.core.presentation.dto.request.ListingRevisionContentRequest;
import vn.sunvibe.listing.core.presentation.dto.request.ListingTermsCaptureRequest;
import vn.sunvibe.listing.core.presentation.dto.response.AgreementListingsResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingCreatedResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingDetailResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingRevisionChangeResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingRevisionDetailResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingTermsSourcesResponse;
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

    private static final String BAD_BODY = "VALIDATION_FAILED (one entry per field: dataVersion missing or negative,"
            + " or a field of the body) or MALFORMED_REQUEST (the body cannot be read); answered before the role";
    private static final String DRAFTER_ROLES = "FORBIDDEN: the caller holds neither SALE nor SALE_LEADER; the same for"
            + " every id";
    private static final String NOT_THE_DRAFTER = "NOT_FOUND: the listing does not exist, an id or the revision number"
            + " is malformed, the caller does not hold the agreement's basis lead now, or the listing has no such"
            + " revision: one answer for all";

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

    /**
     * listing BR-5 (listing-publish E06-S2 spec E06S2-D2, E06S2-D14): the gate and scope of the listing read, so
     * reviewers and managers read each revision; the number is read as text (plan E06S2-P3).
     */
    @GetMapping("/{listingId}/revisions/{revisionNo}")
    @PreAuthorize("hasAnyRole('SALE','SALE_LEADER','SALES_MANAGER','ADMIN','LISTING_REVIEWER')")
    @Operation(operationId = "getListingRevision",
            summary = "One revision of a listing, with the problems a Draft's submission would answer now")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The revision; no user id"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN: the caller holds none of SALE, SALE_LEADER,"
                    + " SALES_MANAGER, ADMIN, LISTING_REVIEWER; the same for every id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: the listing does not exist, an id or the"
                    + " revision number is malformed, the caller may not read the listing, or it has no such revision:"
                    + " one answer for all",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ListingRevisionDetailResponse revision(@PathVariable("listingId") String listingId,
                                                  @PathVariable("revisionNo") String revisionNo) {
        return responses.toRevisionDetail(queries.revision(listingId, ListingRequestConverter.revisionNoOrNull(revisionNo),
                viewers.current()));
    }

    /** listing-publish US-2 AC-6, BR-6: a new empty Draft, for the assigned Sale only (E06S2-D2, E06S2-D10). */
    @PostMapping("/{listingId}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SALE','SALE_LEADER')")
    @Operation(operationId = "startListingRevision",
            summary = "Start a new empty Draft when the listing has no Draft or Pending review revision")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The new Draft, numbered after the greatest number, and"
                    + " the listing's new data version"),
            @ApiResponse(responseCode = "400", description = BAD_BODY,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = DRAFTER_ROLES,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = NOT_THE_DRAFTER,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "LISTING_ARCHIVED, CONCURRENT_MODIFICATION (the data"
                    + " version is stale) or OPEN_REVISION_EXISTS",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ListingRevisionChangeResponse startRevision(@PathVariable("listingId") String listingId,
                                                       @Valid @RequestBody ListingRevisionCommandRequest request) {
        return responses.toChange(commands.startRevision(requests.toStartCommand(listingId, request),
                viewers.current()));
    }

    /** listing-publish US-2 AC-1, AC-3, BR-2, BR-3: a Draft's text and features, saved as a whole (E06S2-D4). */
    @PutMapping("/{listingId}/revisions/{revisionNo}/content")
    @PreAuthorize("hasAnyRole('SALE','SALE_LEADER')")
    @Operation(operationId = "saveListingRevisionContent",
            summary = "Save a Draft's title, description and features; minimum lengths and the machine check are the"
                    + " submission's")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Saved; the listing's new data version"),
            @ApiResponse(responseCode = "400", description = BAD_BODY + "; the entry title, description or"
                    + " features.<name> for a text or code the revision cannot store",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = DRAFTER_ROLES,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = NOT_THE_DRAFTER,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "LISTING_ARCHIVED, CONCURRENT_MODIFICATION or"
                    + " REVISION_NOT_DRAFT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ListingRevisionChangeResponse saveContent(@PathVariable("listingId") String listingId,
                                                     @PathVariable("revisionNo") String revisionNo,
                                                     @Valid @RequestBody ListingRevisionContentRequest request) {
        return responses.toChange(commands.saveContent(
                requests.toCommand(listingId, ListingRequestConverter.revisionNoOrNull(revisionNo), request),
                viewers.current()));
    }

    /** listing-publish US-2 AC-2, BR-4: a value copy of E05's terms; nothing of them can be typed (E06S2-D6). */
    @PutMapping("/{listingId}/revisions/{revisionNo}/terms")
    @PreAuthorize("hasAnyRole('SALE','SALE_LEADER')")
    @Operation(operationId = "captureListingRevisionTerms",
            summary = "Take a Draft's terms snapshot from the agreement's version in effect or waiting to start")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Taken; an earlier snapshot and its fees are replaced"),
            @ApiResponse(responseCode = "400", description = BAD_BODY + "; the entry source for another value",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = DRAFTER_ROLES,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = NOT_THE_DRAFTER,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "LISTING_ARCHIVED, CONCURRENT_MODIFICATION,"
                    + " REVISION_NOT_DRAFT or TERMS_SOURCE_ABSENT (the agreement has no such version now)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: the agreement's terms break the"
                    + " contract; nothing is written",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ListingRevisionChangeResponse captureTerms(@PathVariable("listingId") String listingId,
                                                      @PathVariable("revisionNo") String revisionNo,
                                                      @Valid @RequestBody ListingTermsCaptureRequest request) {
        return responses.toChange(commands.captureTerms(
                requests.toCommand(listingId, ListingRequestConverter.revisionNoOrNull(revisionNo), request),
                viewers.current()));
    }

    /** listing-publish US-2 AC-4, AC-5, BR-2, BR-4, E-4: every violation at once, or Pending review (E06S2-D7). */
    @PostMapping("/{listingId}/revisions/{revisionNo}/submission")
    @PreAuthorize("hasAnyRole('SALE','SALE_LEADER')")
    @Operation(operationId = "submitListingRevision",
            summary = "Submit a complete Draft for review with the unit and property fields of this moment")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pending review; the content is locked"),
            @ApiResponse(responseCode = "400", description = BAD_BODY,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = DRAFTER_ROLES,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = NOT_THE_DRAFTER,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "LISTING_ARCHIVED, CONCURRENT_MODIFICATION or"
                    + " REVISION_NOT_DRAFT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "REVISION_NOT_SUBMITTABLE: one entry per violation, in"
                    + " order, its field <part>.<rule> (title.TOO_SHORT, terms.REQUIRED); never the matched text",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: the unit's profile cannot be read;"
                    + " nothing is written",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ListingRevisionChangeResponse submit(@PathVariable("listingId") String listingId,
                                                @PathVariable("revisionNo") String revisionNo,
                                                @Valid @RequestBody ListingRevisionCommandRequest request) {
        return responses.toChange(commands.submit(
                requests.toSubmitCommand(listingId, ListingRequestConverter.revisionNoOrNull(revisionNo), request),
                viewers.current()));
    }

    /** listing-publish US-2 AC-5, listing section 3.3: Pending review withdrawn before a decision (E06S2-D10). */
    @PostMapping("/{listingId}/revisions/{revisionNo}/withdrawal")
    @PreAuthorize("hasAnyRole('SALE','SALE_LEADER')")
    @Operation(operationId = "withdrawListingRevision",
            summary = "Withdraw a Pending review revision before a decision; its content is kept")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancelled"),
            @ApiResponse(responseCode = "400", description = BAD_BODY,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = DRAFTER_ROLES,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = NOT_THE_DRAFTER,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "LISTING_ARCHIVED, CONCURRENT_MODIFICATION or"
                    + " REVISION_NOT_PENDING",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ListingRevisionChangeResponse withdraw(@PathVariable("listingId") String listingId,
                                                  @PathVariable("revisionNo") String revisionNo,
                                                  @Valid @RequestBody ListingRevisionCommandRequest request) {
        return responses.toChange(commands.withdraw(
                requests.toWithdrawCommand(listingId, ListingRequestConverter.revisionNoOrNull(revisionNo), request),
                viewers.current()));
    }

    /** listing-publish US-2 AC-2: E05's private terms, for the assigned Sale only (E06S2-D2, E06S2-D14). */
    @GetMapping("/{listingId}/terms-sources")
    @PreAuthorize("hasAnyRole('SALE','SALE_LEADER')")
    @Operation(operationId = "getListingTermsSources",
            summary = "What a terms snapshot can be taken from now: the version in effect and the one waiting to start")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Each source present only when the agreement has it;"
                    + " none once the agreement ended"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = DRAFTER_ROLES,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: the listing does not exist, the id is"
                    + " malformed, or the caller does not hold its agreement's basis lead now: one answer for all",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: the agreement's terms break the contract",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ListingTermsSourcesResponse termsSources(@PathVariable("listingId") String listingId) {
        return responses.toTermsSources(queries.termsSources(listingId, viewers.current()));
    }
}
