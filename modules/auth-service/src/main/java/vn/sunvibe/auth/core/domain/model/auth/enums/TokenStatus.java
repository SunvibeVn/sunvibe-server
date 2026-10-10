package vn.sunvibe.auth.core.domain.model.auth.enums;

/**
 * Lifecycle of a single refresh token. Expiry is not a status: it is derived
 * from the token's timestamps at the moment it is presented.
 */
public enum TokenStatus {

    /** Not redeemed yet; may be exchanged for a new token pair. */
    ACTIVE,
    /** Already exchanged (rotated). Presenting it again is a reuse. */
    USED,
    /** Ended by logout, password change/reset, reuse detection or account state. */
    REVOKED

}
