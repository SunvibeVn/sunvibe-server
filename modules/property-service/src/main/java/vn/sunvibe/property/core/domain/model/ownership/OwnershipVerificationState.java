package vn.sunvibe.property.core.domain.model.ownership;

/**
 * Whether the ownership was checked against documents (brief BR-9). W8 writes only {@link #UNVERIFIED} (BR-10);
 * the others belong to a later verification feature. Stored as text in {@code property_ownerships.verification_state}.
 */
public enum OwnershipVerificationState {
    UNVERIFIED,
    VERIFIED,
    REJECTED
}
