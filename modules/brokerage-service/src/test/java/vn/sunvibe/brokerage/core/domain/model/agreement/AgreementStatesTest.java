package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The vocabulary of brokerage-agreement BR-5 (E05-S2 spec S2-D4) and the one transaction type of BR-1 (S2-D5).
 */
class AgreementStatesTest {

    @Test
    void agreementStatus_unfinished_theFirstFour() {
        assertEquals(List.of("DRAFTING", "AWAITING_CONFIRMATION", "ACTIVE", "SUSPENDED", "EXPIRED", "TERMINATED",
                "CANCELLED"), Arrays.stream(AgreementStatus.values()).map(Enum::name).toList());
        assertEquals(List.of(AgreementStatus.DRAFTING, AgreementStatus.AWAITING_CONFIRMATION, AgreementStatus.ACTIVE,
                        AgreementStatus.SUSPENDED),
                Arrays.stream(AgreementStatus.values()).filter(AgreementStatus::isUnfinished).toList());
    }

    @Test
    void versionStatus_pending_draftToActivated() {
        assertEquals(List.of("DRAFT", "AWAITING_OWNER", "OWNER_CONFIRMED", "ACTIVATED", "EFFECTIVE", "SUPERSEDED",
                        "OWNER_REJECTED", "LAPSED", "ACTIVATION_REFUSED", "CANCELLED"),
                Arrays.stream(VersionStatus.values()).map(Enum::name).toList());
        assertEquals(List.of(VersionStatus.DRAFT, VersionStatus.AWAITING_OWNER, VersionStatus.OWNER_CONFIRMED,
                        VersionStatus.ACTIVATED),
                Arrays.stream(VersionStatus.values()).filter(VersionStatus::isPending).toList());
    }

    @Test
    void transactionType_of_longTermRentOnly() {
        assertEquals(Optional.of(TransactionType.LONG_TERM_RENT), TransactionType.of("LONG_TERM_RENT"));
        for (String other : Arrays.asList("SALE", "SHORT_TERM_RENT", "long_term_rent", "", null)) {
            assertEquals(Optional.empty(), TransactionType.of(other), String.valueOf(other));
        }
    }
}
