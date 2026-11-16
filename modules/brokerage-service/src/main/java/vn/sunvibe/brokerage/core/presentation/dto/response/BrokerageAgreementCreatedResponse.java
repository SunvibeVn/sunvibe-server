package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** The draft agreement just created: its id only; its detail is read, and logged, on its own page (E05-S2 spec 4). */
@Schema(name = "BrokerageAgreementCreated")
public record BrokerageAgreementCreatedResponse(String agreementId) {
}
