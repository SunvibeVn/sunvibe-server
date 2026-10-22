package vn.sunvibe.acquisition.core.domain.model.lead;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The six property types Sunvibe takes on the intake form, in form order (acquisition-owner-intake BR-3, Q-16): no
 * "other" value, and none of the old unit layouts, land or townhouse.
 */
class OwnerLeadPropertyTypeTest {

    @Test
    void values_areTheSixSunvibeTypesInFormOrder() {
        assertEquals(List.of("HOUSE", "WHOLE_BUILDING", "VILLA", "HOMESTAY", "CONDOTEL", "APARTMENT"),
                Arrays.stream(OwnerLeadPropertyType.values()).map(Enum::name).toList());
    }
}
