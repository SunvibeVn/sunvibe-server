package vn.sunvibe.survey.core.testsupport;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.core.application.command.common.BasisInput;
import vn.sunvibe.survey.core.application.command.checklist.IntakeInput;
import vn.sunvibe.survey.core.application.command.checklist.StartChecklistCommand;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistStartResult;
import vn.sunvibe.survey.core.application.service.checklist.SurveyChecklistCommandService;
import vn.sunvibe.survey.core.application.service.checklist.SurveyChecklistQueryService;
import vn.sunvibe.survey.core.application.service.checklist.SurveyChecklistRequalificationCommandService;
import vn.sunvibe.survey.core.application.service.appointment.SurveyQueryService;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistReviewReader;
import vn.sunvibe.survey.core.application.shared.appointment.SurveyBlockAssembler;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistMediaCascade;
import vn.sunvibe.survey.core.application.shared.common.ChecklistContextLoader;
import vn.sunvibe.survey.core.application.service.checklist.SurveyChecklistReviewCommandService;
import vn.sunvibe.survey.core.application.service.common.SurveyStateService;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistViewAssembler;
import vn.sunvibe.survey.core.application.shared.checklist.ReviewerExclusionLoader;
import vn.sunvibe.survey.core.application.shared.common.RevisionPhotos;
import vn.sunvibe.survey.core.application.shared.common.SurveyRecordReader;
import vn.sunvibe.survey.core.application.shared.common.SecurityEventRecorder;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.lead.ViewerAccess;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentKind;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyIntent;
import vn.sunvibe.survey.core.domain.model.appointment.VisitRecord;
import vn.sunvibe.survey.core.domain.model.appointment.VisitRecordKind;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistDecision;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSubmission;
import vn.sunvibe.survey.core.domain.model.checklist.DecisionOutcome;
import vn.sunvibe.survey.core.domain.model.checklist.FailedKind;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.NeedsChangesKind;
import vn.sunvibe.survey.core.domain.model.checklist.RentalScope;
import vn.sunvibe.survey.core.domain.model.checklist.SubmittedSpace;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupDocument;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.model.media.MediaPlacement;
import vn.sunvibe.survey.core.domain.model.media.MediaSet;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
    /** The photo fakes write to the same journal, so lock order across groups, set and photos shows. */
    public final List<String> mediaJournal = journal;
    public final InMemoryMediaRepositories.Jobs mediaJobs = new InMemoryMediaRepositories.Jobs(journal);
    public final InMemoryMediaRepositories.Media media = new InMemoryMediaRepositories.Media(mediaJournal);
    public final InMemoryMediaRepositories.Sets mediaSets = new InMemoryMediaRepositories.Sets(mediaJournal);
    public final InMemoryReviewRepositories.Submissions submissions = new InMemoryReviewRepositories.Submissions(
            journal);
    public final InMemoryReviewRepositories.Evidence evidence = new InMemoryReviewRepositories.Evidence(journal);
    public final InMemoryReviewRepositories.Decisions decisions = new InMemoryReviewRepositories.Decisions(journal);
    public final InMemoryReviewRepositories.Selections selections = new InMemoryReviewRepositories.Selections(journal);
    public final InMemoryReviewRepositories.Requalifications requalifications =
            new InMemoryReviewRepositories.Requalifications(journal);
    public final InMemoryIntakeHealth health = new InMemoryIntakeHealth().open(now());
    /** Uploads never on, as every profile's default (survey-media spec A-1): the W16 readiness. */
    public final MutableMediaSwitches switches = new MutableMediaSwitches();
    public final SecurityEventRecorder recorder;
    public final ChecklistViewAssembler views;
    /** The submission switch (checklist submission spec A-3): off, as configured, unless a test turns it on. */
    public boolean submissionOn;
    public final RevisionPhotos revisionPhotos;
    public final SurveyRecordReader records;
    public final SurveyStateService surveyStates;
    public final SurveyChecklistCommandService commands;
    public final ReviewerExclusionLoader exclusions;
    public final ChecklistReviewReader reviewReader;
    public final SurveyChecklistReviewCommandService reviews;
    public final SurveyChecklistRequalificationCommandService requalification;
    public final SurveyChecklistQueryService queries;
    public final SurveyQueryService surveys;
    private final AtomicLong appointmentNo = new AtomicLong();
    private final AtomicInteger photoHash = new AtomicInteger(7_000);

    public ChecklistServiceHarness() {
        IdGenerator<Long> snowflakes = new AtomicLong(5_000L)::incrementAndGet;
        IdGenerator<String> ulids = () -> com.github.f4b6a3.ulid.UlidCreator.getMonotonicUlid().toString();
        recorder = new SecurityEventRecorder(securityEvents, noCounters(journal), new NoTransactions(), snowflakes,
                ChecklistServiceHarness::now);
        ChecklistContextLoader loader = new ChecklistContextLoader(checklists, appointments, leads, directory,
                recorder);
        switches.uploads = false;
        records = new SurveyRecordReader(checklists, decisions, evidence, media);
        surveyStates = new SurveyStateService(records, appointments);
        revisionPhotos = new RevisionPhotos(media, selections);
        exclusions = new ReviewerExclusionLoader(checklists, takeovers, saves, media, selections);
        reviewReader = new ChecklistReviewReader(submissions, evidence, decisions, requalifications, media, exclusions,
                records, directory, switches);
        views = new ChecklistViewAssembler(checklists, groups, takeovers, saves, leads, directory, mediaSets,
                revisionPhotos, switches, () -> submissionOn, health, reviewReader);
        commands = new SurveyChecklistCommandService(checklists, groups, takeovers, saves, appointments, accessLogs,
                leads, directory, loader, views, new ChecklistMediaCascade(mediaSets, media, mediaJobs, selections), ulids,
                snowflakes);
        reviews = new SurveyChecklistReviewCommandService(checklists, groups, takeovers, submissions, evidence,
                decisions, media, mediaSets, selections, accessLogs, leads, directory, loader, exclusions, records,
                recorder, views, switches, () -> submissionOn,
                (contents, frozen) -> contents.keySet() + "|" + frozen, ulids, snowflakes, new NoTransactions());
        requalification = new SurveyChecklistRequalificationCommandService(checklists, requalifications, accessLogs,
                leads, loader, records, views, snowflakes, new NoTransactions());
        queries = new SurveyChecklistQueryService(checklists, appointments, takeovers, accessLogs, leads, directory,
                loader, views, reviewReader, exclusions, snowflakes);
        surveys = new SurveyQueryService(appointments, accessLogs, leads, directory, new SurveyBlockAssembler(
                appointments, new InMemorySurveyHistoryRepositories.Resumptions(), directory, views, records,
                reviewReader), snowflakes, recorder);
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

    /**
     * A revision that can be submitted: the performer's checklist on a WHOLE record with every needed field filled and
     * a usable photo on every required pair, with uploads and submission on.
     */
    public SurveyChecklist ready(String leadId, String performer) {
        switches.uploads = true;
        submissionOn = true;
        SurveyChecklist checklist = started(leadId, performer).view().checklist();
        String space = ChecklistFixture.SPACE_1;
        IdentificationContent whole = ChecklistFixture.identificationWhole();
        // the fixture's address is not the lead's, so the record says how it differs
        IdentificationContent g1 = new IdentificationContent(whole.observedAddress(), "Số nhà ghi khác trên đơn",
                whole.propertyType(), whole.fit(), whole.rentalScope(), whole.spaces(), whole.otherPartsNote(),
                whole.generalNote());
        for (GroupContent content : List.of(g1, ChecklistFixture.parts(space),
                ChecklistFixture.dimensions(space), ChecklistFixture.condition(space), ChecklistFixture.safety(),
                ChecklistFixture.amenities(space), ChecklistFixture.terms(space))) {
            GroupState stored = groups.get(checklist.getId(), content.group());
            groups.put(checklist.getId(), content.group(), new GroupState(new GroupDocument(content.group(), content,
                    Map.of(), Map.of()), stored.version() + 1, now(), performer));
        }
        readyPhoto(checklist.getId(), EvidenceCategory.EXTERIOR_ACCESS, MediaPlacement.asset());
        for (EvidenceCategory category : List.of(EvidenceCategory.MAIN_SPACE, EvidenceCategory.KITCHEN,
                EvidenceCategory.BATHROOM)) {
            readyPhoto(checklist.getId(), category, MediaPlacement.space(space, "A1"));
        }
        return checklist;
    }

    /** The revision as a submit leaves it, without the gates. */
    public void submitted(SurveyChecklistId checklistId, String submitter) {
        SurveyChecklist checklist = checklists.findById(checklistId).orElseThrow();
        checklist.submit(submitter, now());
        checklists.update(checklist);
        VisitRecord visit = appointments.findById(checklist.getVisitAppointmentId()).orElseThrow().getVisit();
        submissions.save(new ChecklistSubmission(checklistId, submitter, now(), checklist.getTemplateCode(),
                checklist.getTemplateVersion(), checklist.getKind(), "LONG_TERM_RENT", checklist.getIntentCycle(),
                checklist.getVisitAppointmentId(), visit.performedBy(), visit.arrivedAt(), visit.recordedBy(),
                visit.recordedAt(), RentalScope.WHOLE, null, List.of(new SubmittedSpace(ChecklistFixture.SPACE_1, "A1")),
                Map.of(), ChecklistSubmission.MEDIA_V1_NO_PERMISSION, "a".repeat(64)));
    }

    /** The revision as a decision leaves it, with its decision row, without the rules. */
    public void decided(SurveyChecklistId checklistId, DecisionOutcome outcome, NeedsChangesKind needsChangesKind,
                        FailedKind failedKind, String reviewer) {
        SurveyChecklist checklist = checklists.findById(checklistId).orElseThrow();
        checklist.decide(outcome, now());
        checklists.update(checklist);
        decisions.save(new ChecklistDecision(checklistId, outcome, needsChangesKind, failedKind,
                outcome == DecisionOutcome.PASSED ? null : "Lý do của người duyệt", null, null, reviewer, now(),
                "SURVEY_SCHEDULED", "SURVEY_SCHEDULED", List.of()));
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

    /** G1 of the checklist set to WHOLE with one observed space SPACE_1 (code A1), as a save would store it. */
    public void wholeWithSpaceOne(SurveyChecklistId checklistId) {
        GroupState g1 = groups.get(checklistId, ChecklistGroup.IDENTIFICATION);
        groups.put(checklistId, ChecklistGroup.IDENTIFICATION, new GroupState(new GroupDocument(
                ChecklistGroup.IDENTIFICATION, ChecklistFixture.identificationWhole(), Map.of(), Map.of()),
                g1.version() + 1, now(), SALE_A));
    }

    /** A usable photo stored on the checklist with its media-set row bumped, as a commit and the worker leave it. */
    public SurveyMedia readyPhoto(SurveyChecklistId checklistId, EvidenceCategory category,
                                  MediaPlacement placement) {
        return photo(checklistId, category, placement, SALE_A, MediaFixture::ready);
    }

    /** A photo of the uploader in the state the function leaves a new one in, stored with its set row bumped. */
    public SurveyMedia photo(SurveyChecklistId checklistId, EvidenceCategory category, MediaPlacement placement,
                             String uploader, java.util.function.UnaryOperator<SurveyMedia> state) {
        SurveyMedia photo = state.apply(MediaFixture.awaitingOn(checklistId.getValue(), category, placement,
                uploader, MediaFixture.sha(photoHash.incrementAndGet())));
        media.put(photo);
        mediaSets.insertIgnoringExisting(checklistId.getValue(), now());
        MediaSet set = mediaSets.findForRead(checklistId.getValue()).orElseThrow();
        set.bump(now());
        mediaSets.update(set);
        return photo;
    }

    /** A view assembler over empty checklist repositories, for the W15 tests of the survey block. */
    public static ChecklistViewAssembler noChecklists(InMemorySurveyLeadPort leads, InMemorySurveyorDirectory directory) {
        List<String> journal = new ArrayList<>();
        MutableMediaSwitches off = new MutableMediaSwitches();
        off.uploads = false;
        return new ChecklistViewAssembler(new InMemoryChecklistRepositories.Checklists(journal),
                new InMemoryChecklistRepositories.Groups(journal), new InMemoryChecklistRepositories.Takeovers(journal),
                new InMemoryChecklistRepositories.Saves(journal), leads, directory,
                new InMemoryMediaRepositories.Sets(journal), new RevisionPhotos(
                        new InMemoryMediaRepositories.Media(journal), new InMemoryReviewRepositories.Selections(journal)),
                off, () -> false, new InMemoryIntakeHealth().open(now()), noReviews(directory));
    }

    /** The review reads over empty repositories, for the W15 tests of the survey block. */
    public static ChecklistReviewReader noReviews(InMemorySurveyorDirectory directory) {
        List<String> journal = new ArrayList<>();
        InMemoryChecklistRepositories.Checklists checklists = new InMemoryChecklistRepositories.Checklists(journal);
        InMemoryMediaRepositories.Media media = new InMemoryMediaRepositories.Media(journal);
        return new ChecklistReviewReader(new InMemoryReviewRepositories.Submissions(journal),
                new InMemoryReviewRepositories.Evidence(journal), new InMemoryReviewRepositories.Decisions(journal),
                new InMemoryReviewRepositories.Requalifications(journal), media,
                new ReviewerExclusionLoader(checklists, new InMemoryChecklistRepositories.Takeovers(journal),
                        new InMemoryChecklistRepositories.Saves(journal), media,
                        new InMemoryReviewRepositories.Selections(journal)), noRecords(), directory,
                new MutableMediaSwitches());
    }

    /** The survey record over empty checklist repositories, for the W15 tests of the appointment commands. */
    public static SurveyRecordReader noRecords() {
        List<String> journal = new ArrayList<>();
        return new SurveyRecordReader(new InMemoryChecklistRepositories.Checklists(journal),
                new InMemoryReviewRepositories.Decisions(journal), new InMemoryReviewRepositories.Evidence(journal),
                new InMemoryMediaRepositories.Media(journal));
    }

    /** A recorder over an in-memory event repository, for the W15 tests of the survey block. */
    public static SecurityEventRecorder noRecorder() {
        return new SecurityEventRecorder(new InMemoryChecklistRepositories.SecurityEvents(),
                noCounters(new ArrayList<>()), new NoTransactions(), new AtomicLong(9_000L)::incrementAndGet,
                ChecklistServiceHarness::now);
    }

    /** The survey-media counters of the recorder's throttle (spec A-19), over no photos. */
    private static InMemoryMediaRepositories.Counters noCounters(List<String> journal) {
        return new InMemoryMediaRepositories.Counters(journal, new InMemoryMediaRepositories.Media(journal));
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
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
