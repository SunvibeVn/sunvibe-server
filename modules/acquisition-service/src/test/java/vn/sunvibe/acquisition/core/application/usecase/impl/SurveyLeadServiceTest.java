package vn.sunvibe.acquisition.core.application.usecase.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import vn.sunvibe.acquisition.core.application.shared.IntakeLocationRules;
import vn.sunvibe.acquisition.core.application.shared.LeadAccessPolicy;
import vn.sunvibe.acquisition.core.application.shared.LeadVisibility;
import vn.sunvibe.acquisition.core.application.shared.external.area.AdministrativeWard;
import vn.sunvibe.acquisition.core.application.usecase.survey.MeetingPlaceCheck;
import vn.sunvibe.acquisition.core.application.usecase.survey.MeetingPlaceViolation;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadAccess;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadCard;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadEvent;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadFacts;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadMove;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadMoveRefusedException;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadMoveResult;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadTarget;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadVersionConflictException;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadViewer;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivity;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.acquisition.core.presentation.converter.LeadViewerConverter;
import vn.sunvibe.acquisition.core.testsupport.InMemoryAdministrativeAreaPort;
import vn.sunvibe.acquisition.core.testsupport.InMemoryLeadAssigneeDirectory;
import vn.sunvibe.acquisition.core.testsupport.InMemoryOwnerLeadActivityRepository;
import vn.sunvibe.acquisition.core.testsupport.InMemoryOwnerLeadRepository;
import vn.sunvibe.acquisition.core.testsupport.OwnerLeadFixture;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.security.AuthenticatedUser;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The use case acquisition publishes for survey-service (survey-schedule spec 5.4): the viewer from the one role
 * mapping, the facts of a visible lead, the version-guarded lead move with its history entry, the cards of the survey
 * list and the meeting-place check by the intake location rules. It never calls survey-service back.
 */
class SurveyLeadServiceTest {

    private static final String SALE_A = "01JSALEAAAAAAAAAAAAAAAAAAA";
    private static final String SALE_B = "01JSALEBBBBBBBBBBBBBBBBBBB";
    private static final String LEADER = "01JLEADERAAAAAAAAAAAAAAAAA";
    private static final String APPOINTMENT = "01JAPPOINTMENTAAAAAAAAAAAA";
    private static final SurveyLeadViewer OWN_A = new SurveyLeadViewer(SALE_A, SurveyLeadAccess.OWN);
    private static final SurveyLeadViewer ALL_LEADER = new SurveyLeadViewer(LEADER, SurveyLeadAccess.ALL);
    private static final Instant T0 = Instant.parse("2026-10-10T08:00:00Z");
    private static final AdministrativeWard HAI_CHAU = new AdministrativeWard("20194", "Hải Châu", "WARD", "48",
            "Đà Nẵng");
    private static final AdministrativeWard HOANG_SA = new AdministrativeWard("20333", "Hoàng Sa", "SPECIAL_ZONE",
            "48", "Đà Nẵng");
    private static final RoleHierarchy REAL_HIERARCHY = RoleHierarchyImpl.withDefaultRolePrefix()
            .role("SUPER_ADMIN").implies("ADMIN")
            .build();

    private FailingSaveRepository leads;
    private InMemoryOwnerLeadActivityRepository activities;
    private InMemoryLeadAssigneeDirectory directory;
    private SurveyLeadService service;

    @BeforeEach
    void setUp() {
        leads = new FailingSaveRepository();
        activities = new InMemoryOwnerLeadActivityRepository();
        directory = new InMemoryLeadAssigneeDirectory().withName(SALE_A, "Sale A").withName(LEADER, "Lead Er");
        IdGenerator<Long> ids = new AtomicLong(9_000L)::incrementAndGet;
        service = new SurveyLeadService(leads, activities, new LeadVisibility(leads), new LeadAccessPolicy(),
                new IntakeLocationRules(new InMemoryAdministrativeAreaPort().with(HAI_CHAU).with(HOANG_SA)),
                directory, ids);
    }

    // ==================== viewer ====================

    /** survey-schedule spec 5.4: survey and the lead queue give the same access for every set of roles. */
    @Test
    void viewerFor_everyRoleSet_givesTheSameAccessAsTheLeadQueueConverter() {
        List<String> roles = List.of("ROLE_SALE", "ROLE_SALE_LEADER", "ROLE_SALES_MANAGER", "ROLE_ADMIN",
                "ROLE_SUPER_ADMIN", "ROLE_CUSTOMER");
        LeadViewerConverter converter = new LeadViewerConverter();
        for (int mask = 0; mask < 1 << roles.size(); mask++) {
            List<String> granted = new ArrayList<>();
            for (int i = 0; i < roles.size(); i++) {
                if ((mask & 1 << i) != 0) {
                    granted.add(roles.get(i));
                }
            }
            Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                    (AuthenticatedUser) () -> SALE_A, null,
                    granted.stream().map(SimpleGrantedAuthority::new).toList());
            Set<String> expanded = REAL_HIERARCHY.getReachableGrantedAuthorities(authentication.getAuthorities())
                    .stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());

            Optional<String> queue = access(() -> converter.toViewer(authentication, REAL_HIERARCHY).access().name());
            Optional<String> survey = access(() -> service.viewerFor(SALE_A, expanded).access().name());

            assertEquals(queue, survey, granted.toString());
        }
    }

    private static Optional<String> access(java.util.function.Supplier<String> call) {
        try {
            return Optional.of(call.get());
        } catch (IllegalStateException e) {
            return Optional.empty();
        }
    }

    // ==================== findVisible ====================

    @Test
    void findVisible_ownLead_returnsTheFacts() {
        OwnerLead lead = stored(OwnerLeadStatus.CONTACTED, SALE_A);
        activities.save(OwnerLeadActivity.load(1L, lead.getId(), OwnerLeadActivityType.NOTE_ADDED, SALE_A, null, null,
                null, null, "note", null, T0.plusSeconds(60), null, null));

        SurveyLeadFacts facts = service.findVisible(id(lead), OWN_A).orElseThrow();

        assertEquals(id(lead), facts.leadId());
        assertEquals("SV-7K3M-Q9TX", facts.requestCode());
        assertEquals("CONTACTED", facts.status());
        assertEquals("LONG_TERM_RENT", facts.intent());
        assertEquals(1, facts.intentCycle());
        assertEquals(SALE_A, facts.assignedUserId());
        assertEquals(0L, facts.version());
        assertEquals(T0.plusSeconds(60), facts.lastActivityAt());
        assertEquals("Nguyen Van A", facts.contactName());
        assertEquals("12 Bach Dang", facts.place().addressDetail());
        assertEquals("48", facts.place().provinceCode());
        assertEquals("20194", facts.place().wardCode());
        assertEquals("Hai Chau 1", facts.place().wardName());
        assertNull(facts.place().latitude());
        assertEquals(OwnerLeadFixture.LONG_AGO, facts.createdAt());
        assertNull(facts.onHoldSince());
    }

    @Test
    void findVisible_heldLead_carriesTheTimeOfTheLastHold() {
        OwnerLead lead = stored(OwnerLeadStatus.ON_HOLD, SALE_A);
        activities.save(statusMove(1L, lead, OwnerLeadStatus.CONTACTED, OwnerLeadStatus.ON_HOLD, T0));
        activities.save(statusMove(2L, lead, OwnerLeadStatus.ON_HOLD, OwnerLeadStatus.CONTACTED, T0.plusSeconds(10)));
        activities.save(statusMove(3L, lead, OwnerLeadStatus.CONTACTED, OwnerLeadStatus.ON_HOLD, T0.plusSeconds(20)));

        assertEquals(T0.plusSeconds(20), service.findVisible(id(lead), OWN_A).orElseThrow().onHoldSince());
    }

    @Test
    void findVisible_leadOfAnotherSaleUnknownOrNotAUlid_isEmpty() {
        OwnerLead other = stored(OwnerLeadStatus.CONTACTED, SALE_B);

        assertEquals(Optional.empty(), service.findVisible(id(other), OWN_A));
        assertEquals(Optional.empty(), service.findVisible(OwnerLeadId.generate().getValue(), OWN_A));
        assertEquals(Optional.empty(), service.findVisible("not-a-ulid", ALL_LEADER));
        assertTrue(service.findVisible(id(other), ALL_LEADER).isPresent());
    }

    // ==================== applySurveyMove ====================

    @Test
    void applySurveyMove_bookingFromContacted_movesBumpsVersionAndWritesOneTextlessEntry() {
        OwnerLead lead = stored(OwnerLeadStatus.CONTACTED, SALE_A);
        Instant at = Instant.parse("2026-10-23T03:00:00Z");

        SurveyLeadMoveResult result = service.applySurveyMove(id(lead), 0L, new SurveyLeadMove(
                SurveyLeadEvent.SURVEY_BOOKED, SurveyLeadTarget.SURVEY_SCHEDULED, SALE_A, APPOINTMENT, 1, at));

        assertEquals("SURVEY_SCHEDULED", result.status());
        assertEquals(1L, result.version());
        assertEquals(at, result.lastActivityAt());
        assertEquals(OwnerLeadStatus.SURVEY_SCHEDULED, leads.findById(lead.getId()).orElseThrow().getStatus());
        List<OwnerLeadActivity> history = activities.all();
        assertEquals(1, history.size());
        OwnerLeadActivity entry = history.get(0);
        assertEquals(OwnerLeadActivityType.SURVEY_BOOKED, entry.getType());
        assertEquals(SALE_A, entry.getActorUserId());
        assertEquals(OwnerLeadStatus.CONTACTED, entry.getFromStatus());
        assertEquals(OwnerLeadStatus.SURVEY_SCHEDULED, entry.getToStatus());
        assertEquals(APPOINTMENT, entry.getReferenceId());
        assertEquals(1, entry.getReferenceNo());
        assertNull(entry.getNote());
        assertEquals(at, entry.getOccurredAt());

        assertEquals("SURVEY_BOOKED", result.entry().type());
        assertEquals(SALE_A, result.entry().actorUserId());
        assertEquals("Sale A", result.entry().actorName());
        assertEquals("CONTACTED", result.entry().fromStatus());
        assertEquals("SURVEY_SCHEDULED", result.entry().toStatus());
        assertEquals(APPOINTMENT, result.entry().referenceId());
        assertEquals(1, result.entry().referenceNo());
        assertEquals(at, result.entry().occurredAt());
    }

    @Test
    void applySurveyMove_keep_hasNoStatusesAndStillBumpsTheVersion() {
        OwnerLead lead = stored(OwnerLeadStatus.SURVEY_SCHEDULED, SALE_A);

        SurveyLeadMoveResult result = service.applySurveyMove(id(lead), 0L, new SurveyLeadMove(
                SurveyLeadEvent.SURVEY_CONFIRMED, SurveyLeadTarget.KEEP, LEADER, APPOINTMENT, 2, T0));

        assertEquals("SURVEY_SCHEDULED", result.status());
        assertEquals(1L, result.version());
        assertNull(result.entry().fromStatus());
        assertNull(result.entry().toStatus());
    }

    @Test
    void applySurveyMove_staleVersion_isAConflictAndChangesNothing() {
        OwnerLead lead = stored(OwnerLeadStatus.CONTACTED, SALE_A);

        assertThrows(SurveyLeadVersionConflictException.class, () -> service.applySurveyMove(id(lead), 5L,
                booking()));

        assertEquals(OwnerLeadStatus.CONTACTED, leads.findById(lead.getId()).orElseThrow().getStatus());
        assertTrue(activities.all().isEmpty());
    }

    @Test
    void applySurveyMove_lostRaceOnSave_isAConflict() {
        OwnerLead lead = stored(OwnerLeadStatus.CONTACTED, SALE_A);
        leads.failNextSave = true;

        assertThrows(SurveyLeadVersionConflictException.class, () -> service.applySurveyMove(id(lead), 0L,
                booking()));

        assertTrue(activities.all().isEmpty());
    }

    @Test
    void applySurveyMove_moveNotInTheTable_isRefusedWithTheStatus() {
        OwnerLead lead = stored(OwnerLeadStatus.ON_HOLD, SALE_A);

        SurveyLeadMoveRefusedException ex = assertThrows(SurveyLeadMoveRefusedException.class,
                () -> service.applySurveyMove(id(lead), 0L, booking()));

        assertEquals("ON_HOLD", ex.getStatus());
        assertTrue(activities.all().isEmpty());
    }

    // ==================== findCards ====================

    @Test
    void findCards_dropsLeadsTheViewerCannotSee() {
        OwnerLead own = stored(OwnerLeadStatus.SURVEY_SCHEDULED, SALE_A);
        OwnerLead other = stored(OwnerLeadStatus.SURVEY_SCHEDULED, SALE_B);

        Map<String, SurveyLeadCard> forSale = service.findCards(OWN_A, List.of(id(own), id(other), "not-a-ulid"));
        Map<String, SurveyLeadCard> forLeader = service.findCards(ALL_LEADER, List.of(id(own), id(other)));

        assertEquals(Set.of(id(own)), forSale.keySet());
        SurveyLeadCard card = forSale.get(id(own));
        assertEquals(id(own), card.leadId());
        assertEquals("SV-7K3M-Q9TX", card.requestCode());
        assertEquals("Nguyen Van A", card.contactName());
        assertEquals("Hai Chau 1", card.wardName());
        assertEquals(Set.of(id(own), id(other)), forLeader.keySet());
        assertEquals(Map.of(), service.findCards(ALL_LEADER, List.of()));
    }

    // ==================== checkMeetingPlace ====================

    @Test
    void checkMeetingPlace_validPlace_returnsNamesAndRoundedPin() {
        MeetingPlaceCheck check = service.checkMeetingPlace("48", "20194", new BigDecimal("16.0544071"),
                new BigDecimal("108.2021669"));

        assertEquals(List.of(), check.violations());
        assertEquals("Đà Nẵng", check.provinceName());
        assertEquals("Hải Châu", check.wardName());
        assertEquals(new BigDecimal("16.054407"), check.latitude());
        assertEquals(new BigDecimal("108.202167"), check.longitude());
    }

    @Test
    void checkMeetingPlace_brokenPlace_returnsTheViolationInsteadOfThrowing() {
        assertEquals(List.of("provinceCode"), fields(service.checkMeetingPlace("99", "20194", null, null)));
        assertEquals(List.of("wardCode"), fields(service.checkMeetingPlace("48", "00000", null, null)));
        assertEquals(List.of("latitude"), fields(service.checkMeetingPlace("48", "20194", new BigDecimal("15.0"),
                new BigDecimal("108.2"))));
        assertEquals(List.of("latitude"), fields(service.checkMeetingPlace("48", "20333", new BigDecimal("16.0"),
                new BigDecimal("108.2"))));
    }

    // ==================== helpers ====================

    private static List<String> fields(MeetingPlaceCheck check) {
        return check.violations().stream().map(MeetingPlaceViolation::field).toList();
    }

    private static SurveyLeadMove booking() {
        return new SurveyLeadMove(SurveyLeadEvent.SURVEY_BOOKED, SurveyLeadTarget.SURVEY_SCHEDULED, SALE_A,
                APPOINTMENT, 1, T0);
    }

    private OwnerLead stored(OwnerLeadStatus status, String assignee) {
        OwnerLead lead = OwnerLeadFixture.lead(status, assignee, (Instant) null, null);
        leads.save(lead);
        return lead;
    }

    private static OwnerLeadActivity statusMove(long id, OwnerLead lead, OwnerLeadStatus from, OwnerLeadStatus to,
                                                Instant at) {
        return OwnerLeadActivity.load(id, lead.getId(), OwnerLeadActivityType.STATUS_CHANGED, SALE_A, from, to, null,
                null, null, null, at, null, null);
    }

    private static String id(OwnerLead lead) {
        return lead.getId().getValue();
    }

    /** The in-memory repository, able to lose the next save to a concurrent writer like the database would. */
    private static final class FailingSaveRepository extends InMemoryOwnerLeadRepository {

        boolean failNextSave;

        @Override
        public void save(OwnerLead lead) {
            if (failNextSave) {
                failNextSave = false;
                throw new ObjectOptimisticLockingFailureException(OwnerLead.class, lead.getId().getValue());
            }
            super.save(lead);
        }
    }
}
