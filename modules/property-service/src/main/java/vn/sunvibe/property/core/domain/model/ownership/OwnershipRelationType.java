package vn.sunvibe.property.core.domain.model.ownership;

/**
 * How a user relates to a property (brief BR-9). W8 writes only {@link #OWNER} (BR-10).
 * Stored as text in {@code property_ownerships.relation_type}.
 */
public enum OwnershipRelationType {
    OWNER,
    CO_OWNER,
    AUTHORIZED_REPRESENTATIVE
}
