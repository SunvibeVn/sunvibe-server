package vn.sunvibe.listing.core.domain.model.listing;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The reason groups of E05's advertising answer as listing reads them (E06-S1 spec E06S1-D9): E05's nine codes in E05's
 * order; a code listing does not know fails, it is never read as allowed.
 */
class AgreementReasonGroupTest {

    @Test
    void ofCode_eachOfTheNine_itsGroupInE05Order() {
        List<String> codes = List.of("NO_AGREEMENT", "NOT_IN_EFFECT", "NOT_STARTED", "SUSPENDED", "ENDED", "BASIS",
                "PROPERTY", "UNIT", "OWNER");
        assertEquals(codes, Arrays.stream(AgreementReasonGroup.values()).map(Enum::name).toList());
        assertEquals(Arrays.asList(AgreementReasonGroup.values()),
                codes.stream().map(AgreementReasonGroup::ofCode).toList());
    }

    @Test
    void ofCode_unknownOrNullCode_illegalState() {
        assertAll(
                () -> assertThrows(IllegalStateException.class, () -> AgreementReasonGroup.ofCode("OTHER")),
                () -> assertThrows(IllegalStateException.class, () -> AgreementReasonGroup.ofCode("ended")),
                () -> assertThrows(IllegalStateException.class, () -> AgreementReasonGroup.ofCode(null)));
    }
}
