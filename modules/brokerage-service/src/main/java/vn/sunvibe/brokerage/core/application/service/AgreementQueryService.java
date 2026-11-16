package vn.sunvibe.brokerage.core.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException;
import vn.sunvibe.brokerage.core.application.query.AgreementDetailView;
import vn.sunvibe.brokerage.core.application.shared.external.lead.BasisLeadPort;
import vn.sunvibe.brokerage.core.application.usecase.AgreementQueryUseCase;
import vn.sunvibe.brokerage.core.application.usecase.StaffViewer;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementAccessType;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.repository.AgreementDomainRepository;
import vn.sunvibe.brokerage.core.domain.repository.AgreementTrailDomainRepository;
import vn.sunvibe.shared.time.AppClock;

import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

/**
 * Use Case Implementation - the reads of a brokerage agreement (E05-S2 spec 3.3, 5.2). The detail is one read-write
 * transaction on purpose: it inserts the access-log row, which a read-only transaction cannot (spec S2-D9). Statements:
 * one agreement read with its versions, one lead read (no lock: a read changes nothing), one insert. Everything the
 * view needs is loaded inside the transaction.
 */
@Service
@RequiredArgsConstructor
public class AgreementQueryService implements AgreementQueryUseCase {

    /** An agreement id is a ULID: 26 characters of upper-case Crockford base 32 digits and letters. */
    private static final Pattern AGREEMENT_ID = Pattern.compile("^[0-9A-Z]{26}$");

    private final AgreementDomainRepository agreements;
    private final BasisLeadPort leads;
    private final AgreementTrailDomainRepository trail;

    @Override
    @Transactional
    public AgreementDetailView detail(String agreementId, StaffViewer viewer) {
        if (agreementId == null || !AGREEMENT_ID.matcher(agreementId).matches()) {
            throw new BrokerageNotFoundException();
        }
        BrokerageAgreement agreement = agreements.findById(agreementId).orElseThrow(BrokerageNotFoundException::new);
        // brokerage-agreement BR-16: staff who may see the agreement's basis lead read it; any other caller gets the
        // answer of an unknown id, and a refused read opens nothing, so it is not logged.
        leads.findVisible(agreement.getBasisLeadId(), viewer.userId(), viewer.expandedAuthorities())
                .orElseThrow(BrokerageNotFoundException::new);
        // brokerage-agreement BR-17: opening the detail of an agreement is logged; no detail without its row.
        trail.recordAccess(viewer.userId(), agreement.getId(), AgreementAccessType.DETAIL_READ,
                AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS));
        return new AgreementDetailView(agreement.getId(), agreement.getStatus(), agreement.getTransactionType(),
                agreement.getUnitId(), agreement.getUnitCodeOrNull(), agreement.getBasisLeadId(),
                agreement.getCreatedAt(), agreement.getVersions().stream()
                .map(version -> new AgreementDetailView.VersionView(version.getVersionNo(), version.getStatus(),
                        version.getPeriod().startDate(), version.getPeriod().endDate(), version.getCreatedAt()))
                .toList());
    }
}
