package vn.sunvibe.acquisition.core.application.command;

import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadPropertyType;
import vn.sunvibe.acquisition.core.domain.model.lead.PriceUnit;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Command - OwnerIntakeCommand
 * <p>
 * One submission of the public owner intake form, as received: text is not yet normalized, the phone is as typed.
 * {@code clientIp} and {@code userAgent} are only hashed into the consent record; {@code submissionSnapshot} is the
 * request body as typed, stored verbatim on the lead (acquisition-owner-intake BR-5). Optional values are null when
 * the owner left them out.
 */
public record OwnerIntakeCommand(
        String idempotencyKey,
        String contactName,
        String phone,
        String email,
        OwnerLeadIntent intent,
        String provinceCode,
        String wardCode,
        String addressDetail,
        BigDecimal latitude,
        BigDecimal longitude,
        OwnerLeadPropertyType propertyType,
        Long askingPriceAmount,
        String askingPriceCurrency,
        PriceUnit askingPriceUnit,
        LocalDate availableFrom,
        String notes,
        String preferredCallbackTime,
        boolean consentAccepted,
        String consentNoticeVersion,
        String clientIp,
        String userAgent,
        String submissionSnapshot) {

    /** The command is personal data (contact, address, position, IP, user agent): never print it. */
    @Override
    public String toString() {
        return "OwnerIntakeCommand[hidden]";
    }
}
