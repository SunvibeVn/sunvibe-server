package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** The signed-in account's agreements with a version sent to them (E05-S5 spec S5-D1, 4). */
@Schema(name = "BrokerageAgreementOwnerList")
public record BrokerageAgreementOwnerListResponse(
        @Schema(description = "At most 50, newest sending first; empty for an account that is not an owner")
        List<BrokerageAgreementOwnerItemResponse> items,
        @Schema(description = "Whether more agreements exist than the list shows")
        boolean more) {
}
