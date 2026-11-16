package vn.sunvibe.brokerage.core.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException;
import vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException;
import vn.sunvibe.brokerage.core.application.query.AgreementDetailView;
import vn.sunvibe.brokerage.core.application.query.SurveyTermsView;
import vn.sunvibe.brokerage.core.application.shared.external.lead.BasisLeadFacts;
import vn.sunvibe.brokerage.core.application.shared.external.lead.BasisLeadPort;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeOriginFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeOriginPort;
import vn.sunvibe.brokerage.core.application.shared.external.survey.SourceTermsPort;
import vn.sunvibe.brokerage.core.application.usecase.AgreementQueryUseCase;
import vn.sunvibe.brokerage.core.application.usecase.StaffViewer;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementAccessType;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementVersion;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.policy.eligibility.EligibilityPolicy;
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

    /**
     * An agreement id is a ULID: 26 characters of upper-case Crockford base 32 digits and letters. The commands check
     * it too, so a malformed id answers as an unknown one everywhere.
     */
    static final Pattern AGREEMENT_ID = Pattern.compile("^[0-9A-Z]{26}$");

    private final AgreementDomainRepository agreements;
    private final BasisLeadPort leads;
    private final AgreementTrailDomainRepository trail;
    private final IntakeOriginPort origins;
    private final SourceTermsPort sourceTerms;

    @Override
    @Transactional
    public AgreementDetailView detail(String agreementId, StaffViewer viewer) {
        BrokerageAgreement agreement = find(agreementId);
        // brokerage-agreement BR-16: staff who may see the agreement's basis lead read it; any other caller gets the
        // answer of an unknown id, and a refused read opens nothing, so it is not logged.
        BasisLeadFacts lead = visibleLead(agreement, viewer);
        // brokerage-agreement BR-17: opening the detail of an agreement is logged; no detail without its row.
        trail.recordAccess(viewer.userId(), agreement.getId(), AgreementAccessType.DETAIL_READ,
                AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS));
        return new AgreementDetailView(agreement.getId(), agreement.getStatus(), agreement.getTransactionType(),
                agreement.getUnitId(), agreement.getUnitCodeOrNull(), agreement.getBasisLeadId(),
                agreement.getCreatedAt(), agreement.getVersions().stream()
                .map(version -> new AgreementDetailView.VersionView(version.getVersionNo(), version.getStatus(),
                        version.getPeriod().startDate(), version.getPeriod().endDate(), version.getCreatedAt(),
                        version.getAdvertisingScope(), version.getPhotoConsent(), version.getTerms(), version.getFee(),
                        version.getSentAtOrNull()))
                .toList(),
                agreement.getRowVersionOrNull(),
                EligibilityPolicy.assignedSale(lead.assignedUserIdOrNull(), viewer.userId(),
                        viewer.expandedAuthorities()));
    }

    /**
     * One read-only transaction (survey's published read needs one): the agreement, the basis lead's visibility (no
     * lock), the origin of the draft's basis intake, then the current survey source of that lead and cycle, used only
     * when it is still the revision the intake rests on (E05-S3 spec S3-D11). It writes nothing: it opens no agreement
     * detail, so no access log (brokerage-agreement BR-17).
     */
    @Override
    @Transactional(readOnly = true)
    public SurveyTermsView surveyTerms(String agreementId, StaffViewer viewer) {
        BrokerageAgreement agreement = find(agreementId);
        visibleLead(agreement, viewer);
        AgreementVersion draft = agreement.draft().orElseThrow(AgreementRefusedException::notDraft);
        IntakeOriginFacts origin = origins.originOf(draft.getBasisIntakeId()).orElse(null);
        // The viewer-less survey read reaches only the lead whose visibility was checked above.
        if (origin == null || !origin.leadId().equals(draft.getBasisLeadId())) {
            return SurveyTermsView.EMPTY;
        }
        String spaceId = origin.spaceIdByUnitId().get(agreement.getUnitId());
        if (spaceId == null) {
            return SurveyTermsView.EMPTY;
        }
        // brokerage-agreement BR-2: a revision marked or replaced since the intake no longer stands as its source.
        return sourceTerms.currentTerms(origin.leadId(), origin.intentCycle())
                .filter(source -> source.revisionId().equals(origin.sourceRevisionId()))
                .map(source -> TermsProposalAssembler.proposal(source, spaceId))
                .orElse(SurveyTermsView.EMPTY);
    }

    /** The agreement of a well-formed id; a malformed or unknown one answers as not found, before any other read. */
    private BrokerageAgreement find(String agreementId) {
        if (agreementId == null || !AGREEMENT_ID.matcher(agreementId).matches()) {
            throw new BrokerageNotFoundException();
        }
        return agreements.findById(agreementId).orElseThrow(BrokerageNotFoundException::new);
    }

    private BasisLeadFacts visibleLead(BrokerageAgreement agreement, StaffViewer viewer) {
        return leads.findVisible(agreement.getBasisLeadId(), viewer.userId(), viewer.expandedAuthorities())
                .orElseThrow(BrokerageNotFoundException::new);
    }
}
