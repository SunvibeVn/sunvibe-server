package vn.sunvibe.listing.core.presentation.converter;

import org.springframework.stereotype.Component;
import vn.sunvibe.listing.core.application.command.ListingCreateCommand;
import vn.sunvibe.listing.core.presentation.dto.request.ListingCreateRequest;

/** Request DTO to command: a pure function of its argument. */
@Component
public class ListingRequestConverter {

    public ListingCreateCommand toCommand(ListingCreateRequest request) {
        return new ListingCreateCommand(request.agreementId());
    }
}
