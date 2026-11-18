package vn.sunvibe.brokerage.core.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.brokerage.core.application.query.AgreementListItemView;
import vn.sunvibe.brokerage.core.application.query.AgreementPageView;
import vn.sunvibe.brokerage.core.application.shared.external.lead.BasisLeadScopePort;
import vn.sunvibe.brokerage.core.application.shared.external.lead.LeadScopeFacts;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightView;
import vn.sunvibe.brokerage.core.application.usecase.AgreementListQuery;
import vn.sunvibe.brokerage.core.application.usecase.AgreementListUseCase;
import vn.sunvibe.brokerage.core.application.usecase.StaffViewer;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementVersion;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.repository.AgreementDomainRepository;
import vn.sunvibe.brokerage.core.domain.repository.AgreementIdPage;
import vn.sunvibe.brokerage.core.domain.repository.AgreementListCriteria;
import vn.sunvibe.brokerage.core.domain.repository.AgreementListDomainRepository;
import vn.sunvibe.shared.time.AppClock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Use Case Implementation - the staff agreement list (E05-S7 spec S7-D1 to S7-D5, 5.2). One read-only transaction with
 * one {@code now}; a fixed number of statements per page whatever its size: the caller's lead scope, the page of ids and
 * its count, the three set loads of those agreements, and the assembler's reads of other modules. It never calls the
 * published AdvertisingRightUseCase (ADR-012 decision 2), and it writes nothing (spec S7-D6).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgreementListService implements AgreementListUseCase {

    /** brokerage-agreement BR-6 (E05-S7 spec S7-D4): "the next 30 days" are today and the 29 days after it. */
    static final int ENDING_DAYS = 30;

    private final BasisLeadScopePort scopes;
    private final AgreementListDomainRepository lists;
    private final AgreementDomainRepository agreements;
    private final AdvertisingRightAssembler assembler;

    @Override
    public AgreementPageView list(AgreementListQuery query, StaffViewer viewer) {
        return listAt(query, viewer, AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS));
    }

    /** The list as it reads at {@code now}; the clock is a parameter so the date rules can be tested (testing.md). */
    AgreementPageView listAt(AgreementListQuery query, StaffViewer viewer, Instant now) {
        // brokerage-agreement BR-16: the agreements whose basis lead the viewer may see, read in this transaction, so a
        // reassignment committed before the read shows at once; no lead at all is the empty page, with no other read.
        LeadScopeFacts scope = scopes.scopeOf(viewer.userId(), viewer.expandedAuthorities());
        if (!scope.allLeads() && scope.leadIds().isEmpty()) {
            return new AgreementPageView(List.of(), query.page(), query.size(), 0);
        }
        AgreementIdPage idPage = lists.idPage(criteria(query, scope, now), query.page(), query.size());
        if (idPage.agreementIds().isEmpty()) {
            return new AgreementPageView(List.of(), query.page(), query.size(), idPage.totalElements());
        }
        Map<String, BrokerageAgreement> loaded = new HashMap<>();
        agreements.findAllById(idPage.agreementIds()).forEach(agreement -> loaded.put(agreement.getId(), agreement));
        // E05-S7 spec S7-D3: each row is the agreement as it stands at the read, as the detail answers it; an id gone
        // since the page was read gives no row.
        List<BrokerageAgreement> effective = idPage.agreementIds().stream().map(loaded::get).filter(Objects::nonNull)
                .map(agreement -> agreement.effectiveAt(now)).toList();
        // brokerage-agreement BR-12: the answer is computed at this read, by the code path the detail uses (spec S7-D5).
        List<AdvertisingRightView> answers = assembler.answers(effective, now);
        List<AgreementListItemView> items = new ArrayList<>();
        for (int index = 0; index < effective.size(); index++) {
            items.add(row(effective.get(index), answers.get(index)));
        }
        return new AgreementPageView(items, query.page(), query.size(), idPage.totalElements());
    }

    /**
     * The criteria of one read: the stored states asked; every lead or the viewer's leads; and, for "ends within the next
     * 30 days", today to today + 29 in the Vietnam calendar (brokerage-agreement BR-6: the right ends at the end of the
     * end date, so now &lt; endsAt &lt;= now + 30 days is exactly that window; E05-S7 spec S7-D4).
     */
    static AgreementListCriteria criteria(AgreementListQuery query, LeadScopeFacts scope, Instant now) {
        LocalDate today = AgreementVersion.todayInVietnam(now);
        return new AgreementListCriteria(query.statuses(), scope.allLeads() ? null : scope.leadIds(),
                query.endsWithin30Days() ? today : null,
                query.endsWithin30Days() ? today.plusDays(ENDING_DAYS - 1) : null);
    }

    /**
     * brokerage-agreement BR-17 (E05-S7 spec S7-D5): of the answer a row keeps the flag, the groups and the three dates;
     * never the terms or the photo consent.
     */
    private static AgreementListItemView row(BrokerageAgreement agreement, AdvertisingRightView answer) {
        AgreementVersion latest = agreement.getVersions().stream()
                .max(Comparator.comparingInt(AgreementVersion::getVersionNo)).orElseThrow();
        return new AgreementListItemView(agreement.getId(), agreement.getUnitCodeOrNull(),
                agreement.getTransactionType(), agreement.getStatus(), latest.getVersionNo(), latest.getStatus(),
                agreement.getCreatedAt(), answer.allowed(), answer.reasonGroups(), answer.endDateOrNull(),
                answer.endsAtOrNull(), answer.pendingStartDateOrNull());
    }
}
