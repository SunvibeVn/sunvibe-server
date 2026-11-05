package vn.sunvibe.property.core.application.usecase.intake;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import vn.sunvibe.property.core.domain.model.intake.OwnerRequest;
import vn.sunvibe.property.core.presentation.dto.request.intake.OwnerConfirmationRequests;

/** property-create intake spec 4.12: records holding an address, a code, a password or a snapshot override toString. */
class OwnerViewsToStringTest {

    private static final String ADDRESS = "12 Bach Dang";
    private static final String LOCATION = "Floor 1";
    private static final String SECRET = "S3CRET-VALUE";

    @Test
    void toString_holdsNoAddress() {
        OwnerRequestSnapshot.Part part = new OwnerRequestSnapshot.Part("A1", LOCATION);
        OwnerRequestSnapshot snapshot = new OwnerRequestSnapshot("L-1", "Ward", "HOUSE", ADDRESS, List.of(part));
        OwnerConfirmationDetails details = new OwnerConfirmationDetails("L-1", "Ward", "HOUSE", ADDRESS, List.of(part),
                new OwnerConfirmationDetails.Statement("v", "text"));
        OwnerConfirmationItem item = OwnerConfirmationItem.confirmed("R", Instant.EPOCH, snapshot, false);
        OwnerRequest request = OwnerRequest.create("R", "I", "U", SECRET, ADDRESS, Instant.EPOCH, "S", Instant.EPOCH);

        for (Object value : List.of(part, snapshot, details, item, request)) {
            assertFalse(value.toString().contains(ADDRESS), value.getClass().getSimpleName());
            assertFalse(value.toString().contains(LOCATION), value.getClass().getSimpleName());
            assertFalse(value.toString().contains(SECRET), value.getClass().getSimpleName());
        }
    }

    @Test
    void toString_holdsNoCodeOrPassword() {
        List<Object> values = List.of(new OwnerRequestUseCase.IssuedRequest("R", SECRET, Instant.EPOCH),
                new OwnerConfirmationRequests.Ask(SECRET, 1L), new OwnerConfirmationRequests.Code(SECRET),
                new OwnerConfirmationRequests.Confirm(SECRET, SECRET, "v"), new OwnerConfirmationRequests.Withdrawal(SECRET));

        for (Object value : values) {
            assertFalse(value.toString().contains(SECRET), value.getClass().getSimpleName());
        }
    }
}
