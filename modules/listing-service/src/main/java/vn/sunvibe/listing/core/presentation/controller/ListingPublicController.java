package vn.sunvibe.listing.core.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.listing.core.application.usecase.ListingPublicQueryUseCase;
import vn.sunvibe.listing.core.presentation.converter.ListingPublicResponseConverter;
import vn.sunvibe.listing.core.presentation.dto.response.ListingPublicResponse;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;

/**
 * The anonymous read of a listing by its public code (listing-publish E06-S7 spec section 4.1, E06S7-D5 to D8;
 * listing-publish BR-14 without photos; ADR-012 decisions 1, 4, 8). No token is needed and a token changes nothing; the
 * path is listed by name in the security chain's public GETs and has its own rate rule. {@code ListingPublicReadFilter}
 * sets {@code Cache-Control: no-store} on every answer of the path and answers the one 404 itself while the switch is off
 * or the code is not of the code's form, so this controller sees only well-formed codes. The use case evaluates every
 * display condition at the read and answers the same 404 for an unknown or a hidden listing. Runs without open-in-view.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Public listing")
public class ListingPublicController {

    /** The one public path; {@code ListingPublicReadFilter} matches on it as MVC and Security do. */
    public static final String PATH = "/api/v1/public/listings/{code}";

    private static final String NO_STORE = "no-store on every answer of the path";

    private final ListingPublicQueryUseCase queries;
    private final ListingPublicResponseConverter responses;

    @GetMapping(PATH)
    @Operation(operationId = "getPublicListing", summary = "The public data of a shown listing, by its public code")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The listing is shown now: its public data and the date"
                    + " of its latest valid confirmation",
                    headers = @Header(name = "Cache-Control", description = NO_STORE,
                            schema = @Schema(type = "string")),
                    content = @Content(schema = @Schema(implementation = ListingPublicResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: the public read is switched off, the code is"
                    + " not of the code's form, or no listing with that code is shown now: one answer for all",
                    headers = @Header(name = "Cache-Control", description = NO_STORE,
                            schema = @Schema(type = "string")),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED: too many reads from this address; the"
                    + " Retry-After header gives the seconds to wait",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: a read failed, or a condition answered a"
                    + " value this server does not know; nothing is shown",
                    headers = @Header(name = "Cache-Control", description = NO_STORE,
                            schema = @Schema(type = "string")),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ListingPublicResponse> publicListing(@PathVariable("code") String code) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(responses.toResponse(queries.publicListing(code)));
    }
}
