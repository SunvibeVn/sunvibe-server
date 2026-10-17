package vn.sunvibe.acquisition.core.application.usecase.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.sunvibe.acquisition.core.application.exception.OwnerLeadNotFoundException;
import vn.sunvibe.acquisition.core.application.query.ConsentView;
import vn.sunvibe.acquisition.core.application.query.DuplicateCandidateView;
import vn.sunvibe.acquisition.core.application.query.LeadActivityView;
import vn.sunvibe.acquisition.core.application.query.LeadListQuery;
import vn.sunvibe.acquisition.core.application.query.LeadScope;
import vn.sunvibe.acquisition.core.application.query.OwnerLeadDetail;
import vn.sunvibe.acquisition.core.application.query.OwnerLeadSummary;
import vn.sunvibe.acquisition.core.application.shared.LeadVisibility;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivity;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentChannel;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentPurpose;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentRecord;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadAccess;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadChange;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadViewer;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.acquisition.core.domain.model.log.OwnerLeadAccessLog;
import vn.sunvibe.acquisition.core.domain.repository.PageResult;
import vn.sunvibe.acquisition.core.testsupport.InMemoryConsentRecordRepository;
import vn.sunvibe.acquisition.core.testsupport.InMemoryDuplicateCandidateRepository;
import vn.sunvibe.acquisition.core.testsupport.InMemoryOwnerLeadAccessLogRepository;
import vn.sunvibe.acquisition.core.testsupport.InMemoryOwnerLeadActivityRepository;
import vn.sunvibe.acquisition.core.testsupport.InMemoryOwnerLeadRepository;
import vn.sunvibe.acquisition.core.testsupport.OwnerLeadFixture;
import vn.sunvibe.shared.generator.id.IdGenerator;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Read side of the staff lead queue (acquisition-owner-lead-queue spec 5.3, 6 shapes; Q9-3, Q9-4, Q9-11, Q9-17).
 */
class OwnerLeadQueryServiceTest {

    private static final String SALE_A = "01JSALEAAAAAAAAAAAAAAAAAAA";
    private static final String SALE_B = "01JSALEBBBBBBBBBBBBBBBBBBB";
    private static final String LEADER = "01JLEADERAAAAAAAAAAAAAAAAA";
    private static final LeadViewer OWN_A = new LeadViewer(SALE_A, LeadAccess.OWN);
    private static final LeadViewer ALL_LEADER = new LeadViewer(LEADER, LeadAccess.ALL);
    private static final String WARD_X = "20194";
    private static final String WARD_Y = "20195";
    private static final Instant T0 = Instant.parse("2026-10-10T08:00:00Z");

    private InMemoryOwnerLeadRepository leads;
    private InMemoryOwnerLeadActivityRepository activities;
    private InMemoryOwnerLeadAccessLogRepository accessLogs;
    private InMemoryDuplicateCandidateRepository candidates;
    private InMemoryConsentRecordRepository consents;
    private OwnerLeadQueryService service;
    private int minutes;

    @BeforeEach
    void setUp() {
        leads = new InMemoryOwnerLeadRepository();
        activities = new InMemoryOwnerLeadActivityRepository();
        accessLogs = new InMemoryOwnerLeadAccessLogRepository();
        candidates = new InMemoryDuplicateCandidateRepository(leads);
        consents = new InMemoryConsentRecordRepository();
        IdGenerator<Long> ids = new AtomicLong(5_000L)::incrementAndGet;
        service = new OwnerLeadQueryService(leads, activities, accessLogs, candidates, consents,
                new LeadVisibility(leads), ids);
    }

    // ==================== list ====================

    @Test
    void list_ownViewer_seesOnlyOwnLeadsEvenWhenScopeAll() {
        OwnerLead own = stored(OwnerLeadStatus.ASSIGNED, SALE_A, WARD_X);
        stored(OwnerLeadStatus.ASSIGNED, SALE_B, WARD_X);
        stored(OwnerLeadStatus.NEW, null, WARD_X);

        assertEquals(List.of(own.getId().getValue()), ids(service.list(OWN_A, query(LeadScope.ALL, Set.of(), null))));
        assertEquals(List.of(own.getId().getValue()), ids(service.list(OWN_A, query(LeadScope.MINE, Set.of(), null))));
        // Unassigned is status NEW; still limited to the SALE's own leads, so nothing (never the NEW lead of nobody)
        assertEquals(List.of(), ids(service.list(OWN_A, query(LeadScope.UNASSIGNED, Set.of(), null))));
    }

    @Test
    void list_leaderScopeAll_seesEveryLead() {
        stored(OwnerLeadStatus.ASSIGNED, SALE_A, WARD_X);
        stored(OwnerLeadStatus.ASSIGNED, SALE_B, WARD_X);
        stored(OwnerLeadStatus.NEW, null, WARD_X);

        PageResult<OwnerLeadSummary> page = service.list(ALL_LEADER, query(LeadScope.ALL, Set.of(), null));

        assertEquals(3, page.items().size());
        assertEquals(3, page.totalElements());
    }

    @Test
    void list_scopeUnassigned_returnsOnlyNewLeads() {
        stored(OwnerLeadStatus.ASSIGNED, SALE_A, WARD_X);
        stored(OwnerLeadStatus.REJECTED, null, WARD_X);
        OwnerLead fresh = stored(OwnerLeadStatus.NEW, null, WARD_X);

        PageResult<OwnerLeadSummary> page = service.list(ALL_LEADER, query(LeadScope.UNASSIGNED, Set.of(), null));

        assertEquals(List.of(fresh.getId().getValue()), ids(page));
    }

    @Test
    void list_scopeUnassignedWithAStatusFilterWithoutNew_isEmpty() {
        stored(OwnerLeadStatus.ASSIGNED, SALE_A, WARD_X);
        stored(OwnerLeadStatus.NEW, null, WARD_X);

        PageResult<OwnerLeadSummary> page = service.list(ALL_LEADER,
                query(LeadScope.UNASSIGNED, Set.of(OwnerLeadStatus.ASSIGNED), null));

        assertTrue(page.items().isEmpty());
        assertEquals(0, page.totalElements());
    }

    @Test
    void list_mineForLeader_returnsOnlyAssignedToTheLeader() {
        stored(OwnerLeadStatus.ASSIGNED, SALE_A, WARD_X);
        stored(OwnerLeadStatus.NEW, null, WARD_X);
        OwnerLead leaders = stored(OwnerLeadStatus.CONTACTED, LEADER, WARD_X);

        PageResult<OwnerLeadSummary> page = service.list(ALL_LEADER, query(LeadScope.MINE, Set.of(), null));

        assertEquals(List.of(leaders.getId().getValue()), ids(page));
    }

    @Test
    void list_statusAndWardFilters_combine() {
        OwnerLead match = stored(OwnerLeadStatus.CONTACTED, SALE_A, WARD_X);
        stored(OwnerLeadStatus.CONTACTED, SALE_A, WARD_Y);
        stored(OwnerLeadStatus.ON_HOLD, SALE_A, WARD_X);
        OwnerLead match2 = stored(OwnerLeadStatus.ASSIGNED, SALE_B, WARD_X);

        PageResult<OwnerLeadSummary> page = service.list(ALL_LEADER,
                query(LeadScope.ALL, Set.of(OwnerLeadStatus.ASSIGNED, OwnerLeadStatus.CONTACTED), WARD_X));

        assertEquals(List.of(match2.getId().getValue(), match.getId().getValue()), ids(page));
    }

    @Test
    void list_possibleDuplicate_countsOnlyCandidatesTheViewerCanSee() {
        OwnerLead mine = stored(OwnerLeadStatus.ASSIGNED, SALE_A, WARD_X);
        OwnerLead othersCandidate = stored(OwnerLeadStatus.ASSIGNED, SALE_B, WARD_X);
        OwnerLead mine2 = stored(OwnerLeadStatus.ASSIGNED, SALE_A, WARD_X);
        OwnerLead mine3 = stored(OwnerLeadStatus.CONTACTED, SALE_A, WARD_X);
        candidates.saveAll(othersCandidate.getId(), List.of(mine.getId()), T0);
        candidates.saveAll(mine3.getId(), List.of(mine2.getId()), T0);

        PageResult<OwnerLeadSummary> own = service.list(OWN_A, query(LeadScope.MINE, Set.of(), null));
        PageResult<OwnerLeadSummary> all = service.list(ALL_LEADER, query(LeadScope.ALL, Set.of(), null));

        assertFalse(summaryOf(own, mine).possibleDuplicate());
        assertTrue(summaryOf(own, mine2).possibleDuplicate());
        assertTrue(summaryOf(own, mine3).possibleDuplicate());
        assertTrue(summaryOf(all, mine).possibleDuplicate());
        assertTrue(summaryOf(all, othersCandidate).possibleDuplicate());
    }

    @Test
    void list_severalPages_pagesNewestFirst() {
        OwnerLead oldest = stored(OwnerLeadStatus.NEW, null, WARD_X);
        OwnerLead middle = stored(OwnerLeadStatus.NEW, null, WARD_X);
        OwnerLead newest = stored(OwnerLeadStatus.NEW, null, WARD_X);

        PageResult<OwnerLeadSummary> first = service.list(ALL_LEADER, new LeadListQuery(LeadScope.ALL, Set.of(),
                null, 0, 2));
        PageResult<OwnerLeadSummary> second = service.list(ALL_LEADER, new LeadListQuery(LeadScope.ALL, Set.of(),
                null, 1, 2));

        assertEquals(List.of(newest.getId().getValue(), middle.getId().getValue()), ids(first));
        assertEquals(List.of(oldest.getId().getValue()), ids(second));
        assertEquals(3, first.totalElements());
        assertEquals(0, first.page());
        assertEquals(2, first.size());
        assertEquals(1, second.page());
    }

    @Test
    void list_summaryCarriesTheListFieldsAndTheVersion() {
        OwnerLead lead = stored(OwnerLeadStatus.ASSIGNED, SALE_A, WARD_X);

        OwnerLeadSummary summary = service.list(OWN_A, query(LeadScope.MINE, Set.of(), null)).items().get(0);

        assertEquals(lead.getId().getValue(), summary.id());
        assertEquals(lead.getRequestCode().getValue(), summary.requestCode());
        assertEquals(OwnerLeadStatus.ASSIGNED, summary.status());
        assertEquals(lead.getIntent(), summary.intent());
        assertEquals("Nguyen Van A", summary.contactName());
        assertEquals("48", summary.provinceCode());
        assertEquals(WARD_X, summary.wardCode());
        assertEquals("Hai Chau 1", summary.wardName());
        assertEquals(SALE_A, summary.assignedUserId());
        assertEquals(lead.getCreatedAt(), summary.createdAt());
        assertEquals(0L, summary.version());
        assertFalse(summary.toString().contains("Nguyen"), "the summary holds the contact name: never print it");
    }

    @Test
    void list_anyScope_writesNoAccessLog() {
        stored(OwnerLeadStatus.ASSIGNED, SALE_A, WARD_X);

        service.list(ALL_LEADER, query(LeadScope.ALL, Set.of(), null));
        service.list(OWN_A, query(LeadScope.MINE, Set.of(), null));

        assertTrue(accessLogs.all().isEmpty());
    }

    // ==================== get ====================

    @Test
    void get_visibleLead_writesOneAccessLogRow() {
        OwnerLead lead = stored(OwnerLeadStatus.ASSIGNED, SALE_A, WARD_X);
        Instant before = Instant.now();

        OwnerLeadDetail detail = service.get(OWN_A, lead.getId().getValue());

        Instant after = Instant.now();
        assertEquals(lead.getId().getValue(), detail.id());
        List<OwnerLeadAccessLog> logs = accessLogs.all();
        assertEquals(1, logs.size());
        assertEquals(lead.getId(), logs.get(0).getLeadId());
        assertEquals(SALE_A, logs.get(0).getUserId());
        assertNotNull(logs.get(0).getId());
        assertFalse(logs.get(0).getAccessedAt().isBefore(before));
        assertFalse(logs.get(0).getAccessedAt().isAfter(after));
    }

    @Test
    void get_invisibleLead_throwsNotFoundAndWritesNoLog() {
        OwnerLead others = stored(OwnerLeadStatus.ASSIGNED, SALE_B, WARD_X);
        OwnerLead unassigned = stored(OwnerLeadStatus.NEW, null, WARD_X);

        assertThrows(OwnerLeadNotFoundException.class, () -> service.get(OWN_A, others.getId().getValue()));
        assertThrows(OwnerLeadNotFoundException.class, () -> service.get(OWN_A, unassigned.getId().getValue()));

        assertTrue(accessLogs.all().isEmpty());
    }

    @Test
    void get_unknownId_throwsTheSameNotFound() {
        OwnerLead others = stored(OwnerLeadStatus.ASSIGNED, SALE_B, WARD_X);

        OwnerLeadNotFoundException invisible = assertThrows(OwnerLeadNotFoundException.class,
                () -> service.get(OWN_A, others.getId().getValue()));
        OwnerLeadNotFoundException unknown = assertThrows(OwnerLeadNotFoundException.class,
                () -> service.get(ALL_LEADER, OwnerLeadId.generate().getValue()));
        OwnerLeadNotFoundException notAUlid = assertThrows(OwnerLeadNotFoundException.class,
                () -> service.get(ALL_LEADER, "not-a-ulid' OR 1=1"));
        OwnerLeadNotFoundException blank = assertThrows(OwnerLeadNotFoundException.class,
                () -> service.get(ALL_LEADER, " "));

        assertEquals(invisible.getMessage(), unknown.getMessage());
        assertEquals(invisible.getMessage(), notAUlid.getMessage());
        assertEquals(invisible.getMessage(), blank.getMessage());
        assertFalse(invisible.getMessage().contains(others.getId().getValue()));
        assertTrue(accessLogs.all().isEmpty());
    }

    @Test
    void get_duplicateOfInvisibleLead_returnsNullDuplicateOf() {
        OwnerLead original = stored(OwnerLeadStatus.ASSIGNED, SALE_B, WARD_X);
        OwnerLead repeat = stored(OwnerLeadStatus.ASSIGNED, SALE_A, WARD_X);
        repeat.changeStatus(OwnerLeadStatus.DUPLICATE, "Same owner", original.getId(), null, null);
        leads.save(repeat);

        OwnerLeadDetail forSale = service.get(OWN_A, repeat.getId().getValue());
        OwnerLeadDetail forLeader = service.get(ALL_LEADER, repeat.getId().getValue());

        assertNull(forSale.duplicateOfLeadId());
        assertEquals(original.getId().getValue(), forLeader.duplicateOfLeadId());
        assertEquals(OwnerLeadStatus.DUPLICATE, forSale.status());
        assertEquals("Same owner", forSale.closeReason());
    }

    @Test
    void get_duplicateCandidates_excludeInvisibleOnes() {
        OwnerLead lead = stored(OwnerLeadStatus.ASSIGNED, SALE_A, WARD_X);
        OwnerLead olderMine = stored(OwnerLeadStatus.CONTACTED, SALE_A, WARD_X);
        OwnerLead others = stored(OwnerLeadStatus.ASSIGNED, SALE_B, WARD_X);
        OwnerLead newerMine = stored(OwnerLeadStatus.ASSIGNED, SALE_A, WARD_X);
        candidates.saveAll(lead.getId(), List.of(olderMine.getId(), others.getId()), T0);
        candidates.saveAll(newerMine.getId(), List.of(lead.getId()), T0);

        OwnerLeadDetail forSale = service.get(OWN_A, lead.getId().getValue());
        OwnerLeadDetail forLeader = service.get(ALL_LEADER, lead.getId().getValue());

        assertEquals(List.of(olderMine.getId().getValue(), newerMine.getId().getValue()),
                forSale.duplicateCandidates().stream().map(DuplicateCandidateView::id).toList());
        assertTrue(forSale.possibleDuplicate());
        assertEquals(3, forLeader.duplicateCandidates().size());
        DuplicateCandidateView first = forSale.duplicateCandidates().get(0);
        assertEquals(olderMine.getRequestCode().getValue(), first.requestCode());
        assertEquals(OwnerLeadStatus.CONTACTED, first.status());
        assertEquals(olderMine.getCreatedAt(), first.createdAt());
    }

    @Test
    void get_onlyInvisibleCandidates_isNotAPossibleDuplicate() {
        OwnerLead lead = stored(OwnerLeadStatus.ASSIGNED, SALE_A, WARD_X);
        OwnerLead others = stored(OwnerLeadStatus.ASSIGNED, SALE_B, WARD_X);
        candidates.saveAll(lead.getId(), List.of(others.getId()), T0);

        OwnerLeadDetail detail = service.get(OWN_A, lead.getId().getValue());

        assertFalse(detail.possibleDuplicate());
        assertTrue(detail.duplicateCandidates().isEmpty());
    }

    @Test
    void get_activitiesAscendingAndNoHashesExposed() {
        OwnerLead lead = stored(OwnerLeadStatus.ASSIGNED, SALE_A, WARD_X);
        Instant later = T0.plusSeconds(600);
        activities.save(OwnerLeadActivity.from(2L, lead.getId(), SALE_A,
                new LeadChange(OwnerLeadActivityType.NOTE_ADDED, null, null, null, null, "Called the owner", later),
                later));
        activities.save(OwnerLeadActivity.load(1L, lead.getId(), OwnerLeadActivityType.AUTO_ASSIGNED, null,
                OwnerLeadStatus.NEW, OwnerLeadStatus.ASSIGNED, null, SALE_A, null, null, T0));

        OwnerLeadDetail detail = service.get(OWN_A, lead.getId().getValue());

        List<LeadActivityView> history = detail.activities();
        assertEquals(List.of(OwnerLeadActivityType.AUTO_ASSIGNED, OwnerLeadActivityType.NOTE_ADDED),
                history.stream().map(LeadActivityView::type).toList());
        assertEquals(SALE_A, history.get(0).toAssigneeId());
        assertEquals(OwnerLeadStatus.NEW, history.get(0).fromStatus());
        assertEquals(OwnerLeadStatus.ASSIGNED, history.get(0).toStatus());
        assertNull(history.get(0).actorUserId());
        assertEquals(SALE_A, history.get(1).actorUserId());
        assertEquals("Called the owner", history.get(1).note());
        assertEquals(later, history.get(1).nextActionAt());
        assertEquals(later, history.get(1).occurredAt());

        ConsentView consent = detail.consent();
        assertEquals(ConsentRecord.CURRENT_NOTICE_VERSION, consent.noticeVersion());
        assertEquals(T0, consent.acceptedAt());
        assertEquals(List.of("noticeVersion", "acceptedAt"), componentNames(ConsentView.class));
        for (String name : componentNames(OwnerLeadDetail.class)) {
            String lower = name.toLowerCase(Locale.ROOT);
            assertFalse(lower.contains("hash") || lower.contains("snapshot") || lower.equals("ip")
                    || lower.contains("addresskey") || lower.contains("consentrecordid"), name);
        }
    }

    @Test
    void get_detailCarriesTheContactDataButNeverPrintsIt() {
        OwnerLead lead = stored(OwnerLeadStatus.ASSIGNED, SALE_A, WARD_X);

        OwnerLeadDetail detail = service.get(OWN_A, lead.getId().getValue());

        assertEquals("+84909123456", detail.phone());
        assertEquals("owner@example.com", detail.email());
        assertEquals("12 Bach Dang", detail.addressDetail());
        assertEquals("Owner note", detail.notes());
        assertEquals(lead.getSource(), detail.source());
        assertEquals(0L, detail.version());
        assertNull(detail.latitude());
        assertNull(detail.askingPrice());
        String printed = detail.toString();
        assertFalse(printed.contains("0909") || printed.contains("owner@example.com") || printed.contains("Bach"),
                printed);
    }

    // ==================== helpers ====================

    /** A lead stored in the fake repository (version 0), one minute newer than the previous one. */
    private OwnerLead stored(OwnerLeadStatus status, String assignee, String wardCode) {
        OwnerLead lead = OwnerLeadFixture.newLead(status, assignee, wardCode, T0.plusSeconds(60L * minutes++));
        leads.save(lead);
        consents.save(ConsentRecord.load(lead.getConsentRecordId(), ConsentRecord.CURRENT_NOTICE_VERSION,
                ConsentPurpose.OWNER_INTAKE_CONTACT, ConsentChannel.WEB, "a".repeat(64), "b".repeat(64), T0));
        return lead;
    }

    private static LeadListQuery query(LeadScope scope, Set<OwnerLeadStatus> statuses, String wardCode) {
        return new LeadListQuery(scope, statuses, wardCode, 0, 20);
    }

    private static List<String> ids(PageResult<OwnerLeadSummary> page) {
        return page.items().stream().map(OwnerLeadSummary::id).toList();
    }

    private static OwnerLeadSummary summaryOf(PageResult<OwnerLeadSummary> page, OwnerLead lead) {
        return page.items().stream()
                .filter(item -> item.id().equals(lead.getId().getValue()))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> componentNames(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }
}
