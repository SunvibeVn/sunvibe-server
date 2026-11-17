package vn.sunvibe.brokerage.core.domain.model.agreement;

import java.util.Arrays;
import java.util.Optional;

/**
 * When the brokerage fee arises (brokerage-agreement BR-7, owner G2 D-23): when a lease is signed, always. It is
 * answered, never entered or stored (E05-S4 spec S4-D3).
 */
public enum FeeChargePoint {
    LEASE_SIGNED;

    /** The moment of this exact name; empty for null or any other text. */
    public static Optional<FeeChargePoint> of(String codeOrNull) {
        return Arrays.stream(values()).filter(point -> point.name().equals(codeOrNull)).findFirst();
    }
}
