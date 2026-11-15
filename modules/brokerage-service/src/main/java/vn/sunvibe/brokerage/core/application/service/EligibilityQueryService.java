package vn.sunvibe.brokerage.core.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException;
import vn.sunvibe.brokerage.core.application.query.EligibilityView;
import vn.sunvibe.brokerage.core.application.shared.external.account.OwnerAccountPort;
import vn.sunvibe.brokerage.core.application.shared.external.lead.BasisLeadFacts;
import vn.sunvibe.brokerage.core.application.shared.external.lead.BasisLeadPort;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisPort;
import vn.sunvibe.brokerage.core.application.usecase.EligibilityQueryUseCase;
import vn.sunvibe.brokerage.core.application.usecase.StaffViewer;
import vn.sunvibe.brokerage.core.domain.policy.eligibility.EligibilityPolicy;

import java.util.List;
import java.util.Optional;

/**
 * Use Case Implementation - the agreement eligibility of the units of a lead's intake basis (E05-S1 spec 5.2). One
 * read-only transaction that every port joins: one lead read, one readiness computation of the basis with its ownership
 * rows, and one owner standing read. It writes nothing: no access log, History entry or event (spec S1-D7).
 */
@Service
@RequiredArgsConstructor
public class EligibilityQueryService implements EligibilityQueryUseCase {

    private static final String NO_BASIS = "NONE";

    private final BasisLeadPort leads;
    private final IntakeBasisPort bases;
    private final OwnerAccountPort accounts;

    @Override
    @Transactional(readOnly = true)
    public EligibilityView forLead(String leadId, StaffViewer viewer) {
        // brokerage-agreement BR-16: the staff who may see the lead read it; any other lead answers as an unknown one.
        BasisLeadFacts lead = leads.findVisible(leadId, viewer.userId(), viewer.expandedAuthorities())
                .orElseThrow(BrokerageNotFoundException::new);
        boolean assignedSale = EligibilityPolicy.assignedSale(lead.assignedUserIdOrNull(), viewer.userId(),
                viewer.expandedAuthorities());
        Optional<IntakeBasisFacts> found = bases.latestBasis(lead.leadId());
        if (found.isEmpty()) {
            return new EligibilityView(NO_BASIS, assignedSale, List.of());
        }
        IntakeBasisFacts basis = found.get();
        String declared = basis.declaredOwnerIdOrNull();
        boolean accountEligible = declared != null && accounts.eligibleOwner(declared);
        EligibilityPolicy.Basis policyBasis = new EligibilityPolicy.Basis(basis.state(), basis.units().stream()
                .map(unit -> new EligibilityPolicy.BasisUnit(unit.unitId(), unit.reasons())).toList());
        EligibilityPolicy.Owner owner = new EligibilityPolicy.Owner(declared, basis.holdings().stream()
                .map(holding -> new EligibilityPolicy.Holding(holding.userId(), holding.relation(), holding.verification()))
                .toList(), accountEligible);
        List<EligibilityView.UnitEligibility> units = basis.units().stream()
                .map(unit -> new EligibilityView.UnitEligibility(unit.unitId(), unit.unitCodeOrNull(),
                        EligibilityPolicy.reasonGroups(unit.unitId(), policyBasis, owner)))
                .toList();
        return new EligibilityView(basis.state(), assignedSale, units);
    }
}
