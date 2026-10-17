package vn.sunvibe.property.core.domain.model.ownership;

/**
 * How a user relates to a property (property-units BR-9). Only {@link #OWNER} is written for now
 * (property-units BR-10).
 * Stored as text in {@code property_ownerships.relation_type}.
 */
public enum OwnershipRelationType {
    OWNER,
    CO_OWNER,
    AUTHORIZED_REPRESENTATIVE
}
