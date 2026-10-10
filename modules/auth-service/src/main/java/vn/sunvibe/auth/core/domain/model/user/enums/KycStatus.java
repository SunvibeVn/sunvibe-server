package vn.sunvibe.auth.core.domain.model.user.enums;

/**
 * Identity-verification state of a user's submitted ID card.
 * New registrations always start {@code UNVERIFIED}; later KYC
 * review flows (out of scope here) move this to PENDING/VERIFIED/REJECTED.
 */
public enum KycStatus {

    UNVERIFIED,
    PENDING,
    VERIFIED,
    REJECTED

}
