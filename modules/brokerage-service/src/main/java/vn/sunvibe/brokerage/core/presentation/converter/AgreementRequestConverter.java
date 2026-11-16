package vn.sunvibe.brokerage.core.presentation.converter;

import org.springframework.stereotype.Component;
import vn.sunvibe.brokerage.core.application.command.AgreementCreateCommand;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementCreateRequest;

/** The agreement requests to their commands; a pure function of its argument. */
@Component
public class AgreementRequestConverter {

    public AgreementCreateCommand toCommand(BrokerageAgreementCreateRequest request) {
        return new AgreementCreateCommand(request.leadId(), request.unitId(), request.transactionType(),
                request.startDate(), request.endDate());
    }
}
