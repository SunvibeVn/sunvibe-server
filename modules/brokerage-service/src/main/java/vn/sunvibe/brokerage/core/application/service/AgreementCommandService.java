package vn.sunvibe.brokerage.core.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.brokerage.core.application.command.AgreementCreateCommand;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException;
import vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException;
import vn.sunvibe.brokerage.core.application.shared.external.account.OwnerAccountPort;
import vn.sunvibe.brokerage.core.application.shared.external.lead.BasisLeadFacts;
import vn.sunvibe.brokerage.core.application.shared.external.lead.BasisLeadPort;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisPort;
import vn.sunvibe.brokerage.core.application.usecase.AgreementCommandUseCase;
import vn.sunvibe.brokerage.core.application.usecase.AgreementCreateResult;
import vn.sunvibe.brokerage.core.application.usecase.StaffViewer;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementEventType;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementPeriod;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.model.eligibility.ReasonGroup;
import vn.sunvibe.brokerage.core.domain.policy.eligibility.EligibilityPolicy;
import vn.sunvibe.brokerage.core.domain.repository.AgreementDomainRepository;
import vn.sunvibe.brokerage.core.domain.repository.AgreementTrailDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

/**
 * Use Case Implementation - the commands of a brokerage agreement (E05-S2 spec 3.1, 5.2). The create is one read-write
 * transaction that every port joins: one locked lead read, one basis read (the readiness of the lead's latest completed
 * intake), at most one owner standing read, one unfinished-agreement lookup, then three inserts (the agreement, its
 * version 1 and one history row). It writes nothing else: no access log, no lead change, no row of another module
 * (brokerage-agreement BR-18).
 * <p>
 * Lock order of the agreement commands: the basis lead's row first (here, through the port's FOR NO KEY UPDATE), then an
 * agreement's row; no agreement command locks a lead row while it holds an agreement row (spec S2-D4).
 */
@Service
@RequiredArgsConstructor
public class AgreementCommandService implements AgreementCommandUseCase {

    private static final List<String> TRANSACTION_TYPES = Arrays.stream(TransactionType.values()).map(Enum::name)
            .toList();

    private final BasisLeadPort leads;
    private final IntakeBasisPort bases;
    private final OwnerAccountPort accounts;
    private final AgreementDomainRepository agreements;
    private final AgreementTrailDomainRepository trail;
    private final IdGenerator<String> ids;

    @Override
    @Transactional
    public AgreementCreateResult create(AgreementCreateCommand command, StaffViewer actor) {
        // brokerage-agreement BR-1: only long-term rental now; any other type is a request error, before any read.
        TransactionType type = TransactionType.of(command.transactionType())
                .orElseThrow(() -> AgreementRefusedException.invalid("transactionType", TRANSACTION_TYPES));
        // brokerage-agreement BR-6: both dates, the end at most 12 months after the start; checked before any read.
        AgreementPeriod period = new AgreementPeriod(command.startDate(), command.endDate());
        // The lead row stays locked to the commit, so a reassignment, a hold or a closing waits for this command or is
        // seen by it (spec S2-D2). A lead the actor may not see answers as an unknown one.
        BasisLeadFacts lead = leads.findVisibleForUpdate(command.leadId(), actor.userId(), actor.expandedAuthorities())
                .orElseThrow(BrokerageNotFoundException::new);
        // brokerage-agreement BR-16: only the lead's current assignee holding SALE or SALE_LEADER drafts; a manager
        // role alone does not draft for them.
        if (!EligibilityPolicy.assignedSale(lead.assignedUserIdOrNull(), actor.userId(), actor.expandedAuthorities())) {
            throw AgreementRefusedException.notAssignee();
        }
        // brokerage-agreement BR-2: an agreement rests on the lead's latest completed intake; without one, BASIS only.
        IntakeBasisFacts basis = bases.latestBasis(lead.leadId())
                .orElseThrow(() -> AgreementRefusedException.notEligible(List.of(ReasonGroup.BASIS)));
        // brokerage-agreement BR-2 to BR-4: the E05-S1 check of the unit reruns here; the command does not trust an
        // earlier eligibility read.
        List<ReasonGroup> groups = reasonGroups(command.unitId(), basis);
        if (!groups.isEmpty()) {
            throw AgreementRefusedException.notEligible(groups);
        }
        // brokerage-agreement BR-1: one unfinished agreement per unit and type. A concurrent create that passes this
        // lookup meets the database's partial unique index at the insert instead.
        if (!agreements.unfinishedOf(List.of(command.unitId()), type).isEmpty()) {
            throw AgreementRefusedException.exists();
        }
        Instant now = AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
        String unitCodeOrNull = basis.units().stream().filter(unit -> unit.unitId().equals(command.unitId()))
                .findFirst().map(IntakeBasisFacts.Unit::unitCodeOrNull).orElse(null);
        BrokerageAgreement agreement = BrokerageAgreement.create(ids.generateId(), command.unitId(), unitCodeOrNull,
                basis.propertyId(), type, basis.declaredOwnerIdOrNull(), lead.leadId(), basis.intakeId(),
                ids.generateId(), period, actor.userId(), now);
        agreements.create(agreement);
        trail.recordEvent(agreement.getId(), 1, AgreementEventType.CREATED, actor.userId(), now);
        return new AgreementCreateResult(agreement.getId());
    }

    /** The reason groups of the unit on this basis, as the eligibility read computes them. */
    private List<ReasonGroup> reasonGroups(String unitId, IntakeBasisFacts basis) {
        String declared = basis.declaredOwnerIdOrNull();
        boolean accountEligible = declared != null && accounts.eligibleOwner(declared);
        EligibilityPolicy.Basis policyBasis = new EligibilityPolicy.Basis(basis.state(), basis.units().stream()
                .map(unit -> new EligibilityPolicy.BasisUnit(unit.unitId(), unit.reasons())).toList());
        EligibilityPolicy.Owner owner = new EligibilityPolicy.Owner(declared, basis.holdings().stream()
                .map(holding -> new EligibilityPolicy.Holding(holding.userId(), holding.relation(), holding.verification()))
                .toList(), accountEligible);
        return EligibilityPolicy.reasonGroups(unitId, policyBasis, owner);
    }
}
