package vn.sunvibe.brokerage.core.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException;
import vn.sunvibe.brokerage.core.application.query.AgreementHistoryEntryView;
import vn.sunvibe.brokerage.core.application.query.AgreementHistoryView;
import vn.sunvibe.brokerage.core.application.shared.external.account.StaffDirectoryPort;
import vn.sunvibe.brokerage.core.application.shared.external.lead.BasisLeadPort;
import vn.sunvibe.brokerage.core.application.usecase.AgreementHistoryQuery;
import vn.sunvibe.brokerage.core.application.usecase.AgreementHistoryUseCase;
import vn.sunvibe.brokerage.core.application.usecase.StaffViewer;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementActorType;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementEventType;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.repository.AgreementDomainRepository;
import vn.sunvibe.brokerage.core.domain.repository.AgreementHistoryDomainRepository;
import vn.sunvibe.brokerage.core.domain.repository.AgreementHistoryPage;
import vn.sunvibe.brokerage.core.domain.repository.AgreementHistoryRow;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Use Case Implementation - the history of an agreement (E05-S7 spec S7-D7, S7-D8, 5.2). One read-only transaction: the
 * agreement, its basis lead's visibility for the caller (no lock), one page of rows and its count, and one directory
 * call for the page's staff actors (none for a page without one). It writes nothing (spec S7-D6).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgreementHistoryService implements AgreementHistoryUseCase {

    /**
     * An agreement id is a ULID: 26 characters of upper-case Crockford base 32 digits and letters, the rule of the
     * detail, so a malformed id answers as an unknown one before any read (E05-S2 spec S2-D10).
     */
    static final Pattern AGREEMENT_ID = Pattern.compile("^[0-9A-Z]{26}$");

    /** E05-S7 spec S7-D8: a stored event code this version of the server does not know. */
    static final String OTHER = "OTHER";

    private static final Set<String> KNOWN_EVENTS = Arrays.stream(AgreementEventType.values()).map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    private final AgreementDomainRepository agreements;
    private final BasisLeadPort leads;
    private final AgreementHistoryDomainRepository histories;
    private final StaffDirectoryPort staff;

    @Override
    public AgreementHistoryView history(AgreementHistoryQuery query, StaffViewer viewer) {
        if (!AGREEMENT_ID.matcher(query.agreementId()).matches()) {
            throw new BrokerageNotFoundException();
        }
        BrokerageAgreement agreement = agreements.findById(query.agreementId())
                .orElseThrow(BrokerageNotFoundException::new);
        // brokerage-agreement BR-16: the detail's readers, staff who may see the basis lead now; any other caller gets
        // the answer of an unknown id, and no row is read for them.
        leads.findVisible(agreement.getBasisLeadId(), viewer.userId(), viewer.expandedAuthorities())
                .orElseThrow(BrokerageNotFoundException::new);
        AgreementHistoryPage page = histories.history(agreement.getId(), query.page(), query.size());
        // brokerage-agreement BR-17: only staff actors are named; the owner is never named nor asked for, and no reason
        // or user id is answered.
        List<String> staffIds = page.rows().stream()
                .filter(row -> actorType(row, agreement) == AgreementActorType.STAFF)
                .map(AgreementHistoryRow::actorUserIdOrNull).distinct().toList();
        Map<String, String> names = staffIds.isEmpty() ? Map.of() : staff.displayNames(staffIds);
        List<AgreementHistoryEntryView> entries = page.rows().stream().map(row -> {
            AgreementActorType actorType = actorType(row, agreement);
            return new AgreementHistoryEntryView(KNOWN_EVENTS.contains(row.eventType()) ? row.eventType() : OTHER,
                    row.versionNoOrNull(), actorType,
                    actorType == AgreementActorType.STAFF ? names.get(row.actorUserIdOrNull()) : null, row.occurredAt());
        }).toList();
        return new AgreementHistoryView(entries, query.page(), query.size(), page.totalElements());
    }

    private static AgreementActorType actorType(AgreementHistoryRow row, BrokerageAgreement agreement) {
        return AgreementActorType.of(row.actorUserIdOrNull(), agreement.getOwnerUserId());
    }
}
