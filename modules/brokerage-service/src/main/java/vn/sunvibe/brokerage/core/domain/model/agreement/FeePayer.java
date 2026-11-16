package vn.sunvibe.brokerage.core.domain.model.agreement;

import java.util.Arrays;
import java.util.Optional;

/**
 * Who pays the brokerage fee (brokerage-agreement BR-7, owner G2 D-23): the owner, always. It is answered, never entered
 * or stored (E05-S4 spec S4-D3).
 */
public enum FeePayer {
    OWNER;

    /** The payer of this exact name; empty for null or any other text. */
    public static Optional<FeePayer> of(String codeOrNull) {
        return Arrays.stream(values()).filter(payer -> payer.name().equals(codeOrNull)).findFirst();
    }
}
