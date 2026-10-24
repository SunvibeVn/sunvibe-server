package vn.sunvibe.survey.core.testsupport;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.core.application.command.BasisInput;
import vn.sunvibe.survey.core.application.command.IntakeInput;
import vn.sunvibe.survey.core.application.command.StartChecklistCommand;
import vn.sunvibe.survey.core.application.query.ChecklistStartResult;
import vn.sunvibe.survey.core.application.service.SurveyChecklistCommandService;
import vn.sunvibe.survey.core.application.service.SurveyChecklistQueryService;
import vn.sunvibe.survey.core.application.service.SurveyQueryService;
import vn.sunvibe.survey.core.application.shared.SurveyBlockAssembler;
import vn.sunvibe.survey.core.application.shared.ChecklistContextLoader;
import vn.sunvibe.survey.core.application.shared.ChecklistViewAssembler;
import vn.sunvibe.survey.core.application.shared.SecurityEventRecorder;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.lead.ViewerAccess;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentKind;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyIntent;
import vn.sunvibe.survey.core.domain.model.appointment.VisitRecordKind;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static vn.sunvibe.survey.core.testsupport.AppointmentFixture.atLead;
import static vn.sunvibe.survey.core.testsupport.AppointmentFixture.details;
import static vn.sunvibe.survey.core.testsupport.AppointmentFixture.leadPlace;

/**
 * The checklist services wired with hand-written fakes (survey-checklist plan Tasks A10, A11). Every fake writes to one
 * journal, so a test can assert the order of spec 5.5: the lead share lock first, the History entry last. Times are
 * relative to the real clock, as the services read it.
 */
public final class ChecklistServiceHarness {

    public static final String LEAD = "01JLEADAAAAAAAAAAAAAAAAAAA";
    public static final String OTHER_LEAD = "01JLEADBBBBBBBBBBBBBBBBBBB";
    public static final String SALE_A = AppointmentFixture.SALE_A;
    public static final String SALE_B = AppointmentFixture.SALE_B;
    public static final String LEADER = AppointmentFixture.LEADER;
    public static final String MANAGER = "01JMANAGERAAAAAAAAAAAAAAAA";

    public final List<String> journal = new ArrayList<>();
    public final InMemorySurveyLeadPort leads = new InMemorySurveyLeadPort(journal);
    public final InMemorySurveyAppointmentRepository appointments = new InMemorySurveyAppointmentRepository(journal);
    public final InMemoryChecklistRepositories.Checklists checklists = new InMemoryChecklistRepositories.Checklists(
            journal);
    public final InMemoryChecklistRepositories.Groups groups = new InMemoryChecklistRepositories.Groups(journal);
    public final InMemoryChecklistRepositories.Takeovers takeovers = new InMemoryChecklistRepositories.Takeovers(
            journal);
    public final InMemoryChecklistRepositories.Saves saves = new InMemoryChecklistRepositories.Saves(journal);
    public final InMemoryChecklistRepositories.SecurityEvents securityEvents =
            new InMemoryChecklistRepositories.SecurityEvents();
    public final InMemorySurveyHistoryRepositories.AccessLogs accessLogs =
            new InMemorySurveyHistoryRepositories.AccessLogs();
    public final InMemorySurveyorDirectory directory = new InMemorySurveyorDirectory()
            .withEligible(SALE_A, "Sale A").withEligible(SALE_B, "Sale B").withEligible(LEADER, "Lead Er")
            .withName(MANAGER, "Man Ager");
    public final SecurityEventRecorder recorder;
    public final ChecklistViewAssembler views;
    public final SurveyChecklistCommandService commands;
    public final SurveyChecklistQueryService queries;
    public final SurveyQueryService surveys;
    private final AtomicLong appointmentNo = new AtomicLong();

    public ChecklistServiceHarness() {
        IdGenerator<Long> snowflakes = new AtomicLong(5_000L)::incrementAndGet;
        IdGenerator<String> ulids = () -> com.github.f4b6a3.ulid.UlidCreator.getMonotonicUlid().toString();
        recorder = new SecurityEventRecorder(securityEvents, new NoTransactions(), snowflakes);
        ChecklistContextLoader loader = new ChecklistContextLoader(checklists, appointments, leads, directory,
                recorder);
        views = new ChecklistViewAssembler(checklists, groups, takeovers, saves, leads, directory);
        commands = new SurveyChecklistCommandService(checklists, groups, takeovers, saves, appointments, accessLogs,
                leads, directory, loader, views, ulids, snowflakes);
        queries = new SurveyChecklistQueryService(takeovers, accessLogs, directory, loader, views, snowflakes);
        surveys = new SurveyQueryService(appointments, accessLogs, leads, directory, new SurveyBlockAssembler(
                appointments, new InMemorySurveyHistoryRepositories.Resumptions(), directory, views), snowflakes,
                recorder);
    }

    public static SurveyViewer own(String userId) {
        return new SurveyViewer(userId, ViewerAccess.OWN);
    }

    public static SurveyViewer all(String userId) {
        return new SurveyViewer(userId, ViewerAccess.ALL);
    }

    /** A long-term lead in the status, assigned to the user, in intent cycle 1. */
    public LeadFacts lead(String leadId, String status, String assignee) {
        return leads.withLead(leadId, status, "LONG_TERM_RENT", 1, assignee, 4L);
    }

    /** A VISITED appointment of the lead, performed and recorded by the performer one day ago. */
    public SurveyAppointment visited(String leadId, SurveyIntent intent, int cycle, String performer) {
        Instant start = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(Duration.ofDays(1));
        SurveyAppointment appointment = SurveyAppointment.book(SurveyAppointmentId.generate(), leadId,
                (int) appointmentNo.incrementAndGet(), AppointmentKind.FIRST, null, null, intent, cycle, leadPlace(),
                details(start, 60, atLead()), null, performer, false, null, performer,
                start.minus(Duration.ofDays(1)));
        appointment.recordVisit(start, "Đã đến xem nhà", performer, performer, VisitRecordKind.OWN, null, null,
                start.plus(Duration.ofHours(2)));
        appointments.save(appointment);
        return appointment;
    }

    /** A SCHEDULED appointment of the lead in the future. */
    public SurveyAppointment scheduled(String leadId, String surveyor) {
        Instant start = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(Duration.ofDays(2));
        SurveyAppointment appointment = SurveyAppointment.book(SurveyAppointmentId.generate(), leadId,
                (int) appointmentNo.incrementAndGet(), AppointmentKind.FIRST, null, null, SurveyIntent.LONG_TERM_RENT,
                1, leadPlace(), details(start, 60, atLead()), null, surveyor, false, null, surveyor, Instant.now());
        appointments.save(appointment);
        return appointment;
    }

    /** The Vietnam date of the visit's arrival. */
    public static LocalDate arrivalDate(SurveyAppointment visit) {
        return SurveyTimeRules.vietnamDate(visit.getVisit().arrivedAt());
    }

    public static LocalDate today() {
        return SurveyTimeRules.vietnamDate(Instant.now());
    }

    public static IntakeInput intake(LocalDate on) {
        return new IntakeInput("Nhận bàn giao từ người đi khảo sát", new BasisInput(on, "Ghi chú bàn giao của Sale A"));
    }

    /** The performer, as assignee of a SURVEY_SCHEDULED lead, starts a checklist on its visit. */
    public ChecklistStartResult started(String leadId, String performer) {
        lead(leadId, "SURVEY_SCHEDULED", performer);
        SurveyAppointment visit = visited(leadId, SurveyIntent.LONG_TERM_RENT, 1, performer);
        return commands.start(own(performer), new StartChecklistCommand(visit.getId().getValue(), null));
    }

    /** A view assembler over empty checklist repositories, for the W15 tests of the survey block. */
    public static ChecklistViewAssembler noChecklists(InMemorySurveyLeadPort leads, InMemorySurveyorDirectory directory) {
        List<String> journal = new ArrayList<>();
        return new ChecklistViewAssembler(new InMemoryChecklistRepositories.Checklists(journal),
                new InMemoryChecklistRepositories.Groups(journal), new InMemoryChecklistRepositories.Takeovers(journal),
                new InMemoryChecklistRepositories.Saves(journal), leads, directory);
    }

    /** A recorder over an in-memory event repository, for the W15 tests of the survey block. */
    public static SecurityEventRecorder noRecorder() {
        return new SecurityEventRecorder(new InMemoryChecklistRepositories.SecurityEvents(), new NoTransactions(),
                new AtomicLong(9_000L)::incrementAndGet);
    }

    /** A transaction manager that never has a transaction: the recorder writes at once. */
    private static final class NoTransactions extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
