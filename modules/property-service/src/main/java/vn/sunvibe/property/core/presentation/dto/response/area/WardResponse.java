package vn.sunvibe.property.core.presentation.dto.response.area;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.property.core.application.usecase.area.WardView;
import vn.sunvibe.property.core.domain.model.area.WardType;

@Schema(description = "A ward, commune or special zone of a province")
public record WardResponse(
        @Schema(description = "Official code", example = "20242") String code,
        @Schema(description = "Name", example = "Hải Châu") String name,
        @Schema(description = "WARD (Phường), COMMUNE (Xã) or SPECIAL_ZONE (Đặc khu)", example = "WARD") WardType type) {

    public static WardResponse from(WardView view) {
        return new WardResponse(view.code(), view.name(), WardType.valueOf(view.type()));
    }
}
