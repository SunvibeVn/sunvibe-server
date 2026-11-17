package vn.sunvibe.brokerage.core.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException;
import vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException;
import vn.sunvibe.brokerage.core.application.query.AgreementDetailView;
import vn.sunvibe.brokerage.core.application.query.OwnerAgreementView;
import vn.sunvibe.brokerage.core.application.query.OwnerAgreementsView;
import vn.sunvibe.brokerage.core.application.query.SurveyTermsView;
import vn.sunvibe.brokerage.core.application.shared.external.account.OwnerAccountPort;
import vn.sunvibe.brokerage.core.application.shared.external.lead.BasisLeadFacts;
import vn.sunvibe.brokerage.core.application.shared.external.lead.BasisLeadPort;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeOriginFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeOriginPort;
import vn.sunvibe.brokerage.core.application.shared.external.property.UnitLocationFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.UnitLocationPort;
import vn.sunvibe.brokerage.core.application.shared.external.survey.SourceTermsPort;
import vn.sunvibe.brokerage.core.application.usecase.AgreementQueryUseCase;
import vn.sunvibe.brokerage.core.application.usecase.StaffViewer;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementAccessType;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatement;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementVersion;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.OwnerAnswer;
import vn.sunvibe.brokerage.core.domain.policy.eligibility.EligibilityPolicy;
import vn.sunvibe.brokerage.core.domain.repository.AgreementDomainRepository;
import vn.sunvibe.brokerage.core.domain.repository.AgreementTrailDomainRepository;
import vn.sunvibe.shared.time.AppClock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Use Case Implementation - the reads of a brokerage agreement (E05-S2 spec 3.3, 5.2). The detail is one read-write
 * transaction on purpose: it inserts the access-log row, which a read-only transaction cannot (spec S2-D9). Statements:
 * one agreement read with its versions, one lead read (no lock: a read changes nothing), one insert. Everything the
 * view needs is loaded inside the transaction. Both the detail and the owner's read answer the effective state at the
 * wall clock's {@code now} (E05-S5 spec S5-D8, S5-D19).
 */
@Service
@RequiredArgsConstructor
public class AgreementQueryService implements AgreementQueryUseCase {

    /**
     * An agreement id is a ULID: 26 characters of upper-case Crockford base 32 digits and letters. The commands check
     * it too, so a malformed id answers as an unknown one everywhere.
     */
    static final Pattern AGREEMENT_ID = Pattern.compile("^[0-9A-Z]{26}$");

    /** E05-S5 spec S5-D1: the owner's read answers at most this many agreements and says when there are more. */
    static final int OWNER_PAGE = 50;

    private final AgreementDomainRepository agreements;
    private final BasisLeadPort leads;
    private final AgreementTrailDomainRepository trail;
    private final IntakeOriginPort origins;
    private final SourceTermsPort sourceTerms;
    private final OwnerAccountPort accounts;
    private final UnitLocationPort locations;

    @Override
    @Transactional
    public AgreementDetailView detail(String agreementId, StaffViewer viewer) {
        BrokerageAgreement stored = find(agreementId);
        // brokerage-agreement BR-16: staff who may see the agreement's basis lead read it; any other caller gets the
        // answer of an unknown id, and a refused read opens nothing, so it is not logged.
        BasisLeadFacts lead = visibleLead(stored, viewer);
        Instant now = AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
        // brokerage-agreement BR-17: opening the detail of an agreement is logged; no detail without its row.
        trail.recordAccess(viewer.userId(), stored.getId(), AgreementAccessType.DETAIL_READ, now);
        // brokerage-agreement BR-10 (E05-S5 spec S5-D8): a version past its 7 days reads LAPSED from its deadline on,
        // whether or not the lapse job has stored it yet; the data version stays the stored row's, so the Sale's next
        // command matches it.
        BrokerageAgreement agreement = stored.withDueLapse(now);
        return new AgreementDetailView(agreement.getId(), agreement.getStatus(), agreement.getTransactionType(),
                agreement.getUnitId(), agreement.getUnitCodeOrNull(), agreement.getBasisLeadId(),
                agreement.getCreatedAt(), agreement.getVersions().stream().map(AgreementQueryService::versionView)
                .toList(),
                stored.getRowVersionOrNull(),
                EligibilityPolicy.assignedSale(lead.assignedUserIdOrNull(), viewer.userId(),
                        viewer.expandedAuthorities()));
    }

    /**
     * One read-write transaction (E05-S5 plan E05S5-P5: it writes the access rows). Statements when the caller is an
     * eligible owner: auth's standing, the ids of at most 51 agreements and the three fetches of their versions, fee rows
     * and tier rows, one location read for the units of the 50 answered, one batch of access rows. When not: auth's
     * standing only.
     */
    @Override
    @Transactional
    public OwnerAgreementsView ownerAgreements(String ownerUserId) {
        // brokerage-agreement US-2 AC-1: a staff account, a locked account or another non-owner gets the same answer as
        // an owner with nothing, and nothing of an agreement is read for it.
        if (!accounts.eligibleOwner(ownerUserId)) {
            return new OwnerAgreementsView(List.of(), false);
        }
        Instant now = AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
        List<BrokerageAgreement> found = agreements.sentToOwner(ownerUserId, OWNER_PAGE + 1);
        List<BrokerageAgreement> page = found.size() > OWNER_PAGE ? found.subList(0, OWNER_PAGE) : found;
        if (page.isEmpty()) {
            return new OwnerAgreementsView(List.of(), false);
        }
        Map<String, UnitLocationFacts> located = new HashMap<>();
        locations.locate(page.stream().map(BrokerageAgreement::getUnitId).distinct().toList())
                .forEach(facts -> located.put(facts.unitId(), facts));
        List<OwnerAgreementView> items = page.stream()
                .map(agreement -> ownerView(agreement.withDueLapse(now), located.get(agreement.getUnitId()), now))
                .toList();
        // brokerage-agreement BR-17: the owner's opening of each agreement's detail is logged.
        trail.recordAccesses(ownerUserId, page.stream().map(BrokerageAgreement::getId).toList(),
                AgreementAccessType.OWNER_DETAIL_READ, now);
        return new OwnerAgreementsView(items, found.size() > OWNER_PAGE);
    }

    /**
     * The owner's view of one agreement in its effective state: only what was sent to them (brokerage-agreement BR-5: a
     * draft is never shown), and the statement of the version they may answer now (BR-10; E05-S5 spec S5-D3).
     */
    private static OwnerAgreementView ownerView(BrokerageAgreement agreement, UnitLocationFacts locationOrNull,
                                                Instant now) {
        OwnerAgreementsView.StatementView statement = agreement.getVersions().stream()
                .filter(version -> version.answerableAt(now))
                .findFirst()
                .map(version -> new OwnerAgreementsView.StatementView(AgreementStatement.VERSION,
                        AgreementStatement.text(version.getVersionNo(), agreement.getUnitCodeOrNull())))
                .orElse(null);
        return new OwnerAgreementView(agreement.getId(), agreement.getStatus(), agreement.getTransactionType(),
                agreement.getUnitCodeOrNull(),
                locationOrNull == null ? null : locationOrNull.locationLabelOrNull(),
                locationOrNull == null ? null : locationOrNull.streetOrNull(),
                locationOrNull == null ? null : locationOrNull.wardNameOrNull(),
                agreement.getVersions().stream().filter(version -> version.getSentAtOrNull() != null)
                        .map(AgreementQueryService::versionView).toList(),
                statement);
    }

    private static AgreementDetailView.VersionView versionView(AgreementVersion version) {
        OwnerAnswer answer = version.getOwnerAnswerOrNull();
        return new AgreementDetailView.VersionView(version.getVersionNo(), version.getStatus(),
                version.getPeriod().startDate(), version.getPeriod().endDate(), version.getCreatedAt(),
                version.getAdvertisingScope(), version.getPhotoConsent(), version.getTerms(), version.getFee(),
                version.getSentAtOrNull(), version.answerByOrNull(), answer == null ? null : answer.answeredAt(),
                answer == null ? null : answer.rejectionReasonOrNull());
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
