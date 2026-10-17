package vn.sunvibe.property.core.domain.model.ownership;

/**
 * Whether the ownership was checked against documents (property-units BR-9). Only {@link #UNVERIFIED} is written
 * for now (property-units BR-10); the others belong to a later verification feature. Stored as text in
 * {@code property_ownerships.verification_state}.
 */
public enum OwnershipVerificationState {
    UNVERIFIED,
    VERIFIED,
    REJECTED
}
