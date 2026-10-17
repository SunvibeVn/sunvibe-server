package vn.sunvibe.property.core.presentation.controller.area;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.property.core.application.usecase.area.AdministrativeAreaQueryUseCase;
import vn.sunvibe.property.core.presentation.dto.response.area.ProvinceResponse;
import vn.sunvibe.property.core.presentation.dto.response.area.WardResponse;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;

import java.util.List;

/**
 * The administrative-area catalog (acquisition-owner-intake brief BR-6) for the owner intake form. Anonymous on
 * purpose: both paths are public GET endpoints in SecurityConfig, and they expose only public catalog data.
 */
@RestController
@RequestMapping("/api/v1/public/provinces")
@Tag(name = "Administrative areas")
@RequiredArgsConstructor
public class AdministrativeAreaController {

    private final AdministrativeAreaQueryUseCase queryUseCase;

    @GetMapping
    @Operation(summary = "List the provinces of the catalog (anonymous), sorted by name")
    @ApiResponse(responseCode = "200", description = "Provinces")
    public List<ProvinceResponse> listProvinces() {
        return queryUseCase.findProvinces().stream().map(ProvinceResponse::from).toList();
    }

    @GetMapping("/{provinceCode}/wards")
    @Operation(summary = "List the wards of a province (anonymous), sorted by name")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Wards of the province",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = WardResponse.class)))),
            @ApiResponse(responseCode = "404", description = "Unknown province code (code NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<WardResponse> listWards(@PathVariable("provinceCode") String provinceCode) {
        return queryUseCase.findWards(provinceCode).stream().map(WardResponse::from).toList();
    }
}
