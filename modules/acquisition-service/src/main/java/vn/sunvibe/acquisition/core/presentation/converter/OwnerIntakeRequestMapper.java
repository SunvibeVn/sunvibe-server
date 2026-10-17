package vn.sunvibe.acquisition.core.presentation.converter;

import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.application.command.OwnerIntakeCommand;
import vn.sunvibe.acquisition.core.presentation.dto.request.OwnerIntakeRequest;
import vn.sunvibe.acquisition.core.presentation.dto.request.OwnerIntakeRequest.AskingPriceRequest;
import vn.sunvibe.acquisition.core.presentation.dto.request.OwnerIntakeRequest.ConsentRequest;

/**
 * Converter - OwnerIntakeRequestMapper
 * <p>
 * Request DTO -> OwnerIntakeCommand. Values are passed as typed; the service normalizes them. The amount was
 * validated as a whole number of at most 15 digits, so it fits a {@code Long}.
 */
@Component
public class OwnerIntakeRequestMapper {

    public OwnerIntakeCommand toCommand(OwnerIntakeRequest request, String idempotencyKey, String clientIp,
                                        String userAgent, String snapshot) {
        AskingPriceRequest price = request.askingPrice();
        ConsentRequest consent = request.consent();
        return new OwnerIntakeCommand(
                idempotencyKey,
                request.contactName(),
                request.phone(),
                request.email(),
                request.intent(),
                request.provinceCode(),
                request.wardCode(),
                request.addressDetail(),
                request.latitude(),
                request.longitude(),
                request.propertyType(),
                price == null || price.amount() == null ? null : price.amount().longValueExact(),
                price == null ? null : price.currency(),
                price == null ? null : price.unit(),
                request.availableFrom(),
                request.notes(),
                request.preferredCallbackTime(),
                consent != null && Boolean.TRUE.equals(consent.accepted()),
                consent == null ? null : consent.noticeVersion(),
                clientIp,
                userAgent,
                snapshot);
    }
}
