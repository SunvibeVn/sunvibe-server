package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How a brokerage fee is set, and where each kind is taken (brokerage-agreement BR-7; E05-S4 spec S4-D3): the fee takes
 * a percentage of the rent, a fixed amount or free of charge; the renewal takes no charge, a percentage or a fixed amount.
 */
class FeeFormulaTypeTest {

    @Test
    void places_feeAndRenewal_listsInOrder() {
        assertEquals(List.of("PERCENT_OF_RENT", "FIXED_AMOUNT", "FREE"), FeeFormulaType.FEE_TYPES);
        assertEquals(List.of("NOT_CHARGED", "PERCENT_OF_RENT", "FIXED_AMOUNT"), FeeFormulaType.RENEWAL_TYPES);
        for (FeeFormulaType type : FeeFormulaType.values()) {
            assertEquals(type != FeeFormulaType.NOT_CHARGED, type.forFee(), type.name());
            assertEquals(type != FeeFormulaType.FREE, type.forRenewal(), type.name());
            assertEquals(type.forFee(), FeeFormulaType.FEE_TYPES.contains(type.name()), type.name());
            assertEquals(type.forRenewal(), FeeFormulaType.RENEWAL_TYPES.contains(type.name()), type.name());
        }
        assertEquals(List.of("NO_REFUND", "PARTIAL_REFUND"), EarlyLeaseEndType.NAMES);
    }

    @Test
    void of_unknown_empty() {
        assertEquals(Optional.of(FeeFormulaType.FREE), FeeFormulaType.of("FREE"));
        assertEquals(Optional.empty(), FeeFormulaType.of("MONTHLY"));
        assertEquals(Optional.empty(), FeeFormulaType.of("free"));
        assertEquals(Optional.empty(), FeeFormulaType.of(null));
        assertEquals(Optional.of(EarlyLeaseEndType.PARTIAL_REFUND), EarlyLeaseEndType.of("PARTIAL_REFUND"));
        assertEquals(Optional.empty(), EarlyLeaseEndType.of("FULL_REFUND"));
        assertEquals(Optional.empty(), EarlyLeaseEndType.of(null));
        assertEquals(Optional.of(FeePayer.OWNER), FeePayer.of("OWNER"));
        assertEquals(Optional.empty(), FeePayer.of("TENANT"));
        assertEquals(Optional.of(FeeChargePoint.LEASE_SIGNED), FeeChargePoint.of("LEASE_SIGNED"));
        assertEquals(Optional.empty(), FeeChargePoint.of(null));
    }
}
