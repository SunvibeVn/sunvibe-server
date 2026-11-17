package vn.sunvibe.brokerage.core.domain.model.agreement;

import java.util.Arrays;
import java.util.Optional;

/**
 * Why a manager refused to activate a version (brokerage-agreement US-3 AC-3, E-7; E05-S6 spec S6-D4): the signed
 * document differs from the version, or the owner's side was not signed by the owner who confirmed it. Stored on the
 * version.
 */
public enum ActivationRefusalType {
    DOCUMENT_DIFFERS,
    SIGNER_NOT_OWNER;

    /** The type of a stored or sent code; empty for any other text. */
    public static Optional<ActivationRefusalType> of(String codeOrNull) {
        return Arrays.stream(values()).filter(type -> type.name().equals(codeOrNull)).findFirst();
    }
}
