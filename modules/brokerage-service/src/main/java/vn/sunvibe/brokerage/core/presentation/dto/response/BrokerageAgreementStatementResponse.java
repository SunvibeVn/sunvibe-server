package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** The statement the owner confirms a version with (brokerage-agreement BR-10; E05-S5 spec S5-D3). */
@Schema(name = "BrokerageAgreementStatement")
public record BrokerageAgreementStatementResponse(
        @Schema(description = "The statement version a confirmation sends back as statementVersion")
        String version,
        @Schema(description = "The text, naming the version number and the unit's code")
        String text) {
}
