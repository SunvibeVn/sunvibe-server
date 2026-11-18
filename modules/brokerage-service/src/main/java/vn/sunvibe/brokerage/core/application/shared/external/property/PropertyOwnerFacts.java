package vn.sunvibe.brokerage.core.application.shared.external.property;

import java.util.List;

/**
 * The owner data of one property for the brokerage-agreement BR-4 check (E05-S1 spec S1-D4; E05-S6 spec S6-D17; read 1b
 * of the delivery plan PLAN5-D3). It holds user ids, so its text names the property and the number of holdings only
 * (brokerage-agreement BR-17).
 *
 * @param declaredOwnerIdOrNull the property's declared owner; null when it has none
 * @param holdings              every ownership row of the property valid today, any relation and verification
 */
public record PropertyOwnerFacts(String propertyId, String declaredOwnerIdOrNull,
                                 List<IntakeBasisFacts.Holding> holdings) {

    public PropertyOwnerFacts {
        holdings = List.copyOf(holdings);
    }

    @Override
    public String toString() {
        return "PropertyOwnerFacts[" + propertyId + ", holdings=" + holdings.size() + "]";
    }
}
