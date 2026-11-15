package vn.sunvibe.brokerage.core.presentation.converter;

import org.springframework.stereotype.Component;
import vn.sunvibe.brokerage.core.application.query.AgreementDetailView;
import vn.sunvibe.brokerage.core.application.usecase.AgreementCreateResult;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementCreatedResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementDetailResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementVersionResponse;

/** The agreement results and views to their responses; a pure function of its argument. */
@Component
public class AgreementResponseConverter {

    public BrokerageAgreementCreatedResponse toCreated(AgreementCreateResult result) {
        return new BrokerageAgreementCreatedResponse(result.agreementId());
    }

    public BrokerageAgreementDetailResponse toDetail(AgreementDetailView view) {
        return new BrokerageAgreementDetailResponse(view.agreementId(), view.status().name(),
                view.transactionType().name(), view.unitId(), view.unitCodeOrNull(), view.basisLeadId(), view.createdAt(),
                view.versions().stream()
                        .map(version -> new BrokerageAgreementVersionResponse(version.versionNo(), version.status().name(),
                                version.startDate(), version.endDate(), version.createdAt()))
                        .toList());
    }
}
