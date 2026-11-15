package vn.sunvibe.property.core.application.usecase.intake;

import java.util.List;

/**
 * The owner data of one property for the brokerage-agreement BR-4 check (E05-S1 S1-D4), as {@link PropertyOwnershipUseCase}
 * publishes it. It holds user ids, so its text names the property and the number of holdings only: a log line never
 * carries an owner (brokerage-agreement BR-17).
 *
 * @param propertyId    the property
 * @param ownerIdOrNull its declared owner; null when it has none
 * @param holdings      every ownership row of the property valid today in Vietnam, any relation and verification, ordered
 *                      by user and relation
 */
public record PropertyOwnershipView(String propertyId, String ownerIdOrNull, List<Holding> holdings) {

    public PropertyOwnershipView {
        holdings = List.copyOf(holdings);
    }

    @Override
    public String toString() {
        return "PropertyOwnershipView[" + propertyId + ", holdings=" + holdings.size() + "]";
    }

    /** One ownership row valid today, its values as stored. */
    public record Holding(String userId, String relationType, String verificationState) {

        @Override
        public String toString() {
            return "Holding[hidden]";
        }
    }
}
