package vn.sunvibe.brokerage.core.presentation.converter;

import org.springframework.stereotype.Component;
import vn.sunvibe.brokerage.core.application.query.EligibilityView;
import vn.sunvibe.brokerage.core.domain.model.eligibility.ReasonGroup;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageEligibilityResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageEligibilityUnitResponse;

/** The eligibility read model to its response; a pure function of its argument. */
@Component
public class EligibilityResponseConverter {

    public BrokerageEligibilityResponse toResponse(EligibilityView view) {
        return new BrokerageEligibilityResponse(view.basisState(), view.viewerIsAssignedSale(), view.units().stream()
                .map(unit -> new BrokerageEligibilityUnitResponse(unit.unitId(), unit.unitCodeOrNull(), unit.eligible(),
                        unit.reasonGroups().stream().map(ReasonGroup::name).toList()))
                .toList());
    }
}
