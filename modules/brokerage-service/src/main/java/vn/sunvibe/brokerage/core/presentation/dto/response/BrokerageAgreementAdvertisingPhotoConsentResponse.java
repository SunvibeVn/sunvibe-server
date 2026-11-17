package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Which public photos the owner allows now (brokerage-agreement BR-9, BR-12; E05-S6 spec S6-D7): each true only while
 * Sunvibe may advertise the unit and the version that counts gives it.
 */
@Schema(name = "BrokerageAgreementAdvertisingPhotoConsent")
public record BrokerageAgreementAdvertisingPhotoConsentResponse(
        @Schema(description = "Photos of the interior and common areas may be public now")
        boolean interior,
        @Schema(description = "Photos of the exterior may be public now")
        boolean exterior) {
}
