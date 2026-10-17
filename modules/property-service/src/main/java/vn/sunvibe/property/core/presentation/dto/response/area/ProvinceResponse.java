package vn.sunvibe.property.core.presentation.dto.response.area;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.property.core.application.usecase.area.ProvinceView;

@Schema(description = "A province or centrally run city of the official catalog")
public record ProvinceResponse(
        @Schema(description = "Official code", example = "48") String code,
        @Schema(description = "Name", example = "Đà Nẵng") String name) {

    public static ProvinceResponse from(ProvinceView view) {
        return new ProvinceResponse(view.code(), view.name());
    }
}
