package vn.sunvibe.survey.core.testsupport;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.github.f4b6a3.ulid.UlidCreator;

import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.core.application.command.CreateMediaCommand;
import vn.sunvibe.survey.core.application.query.ChecklistStartResult;
import vn.sunvibe.survey.core.application.service.SurveyMediaCommandService;
import vn.sunvibe.survey.core.application.service.SurveyMediaContentService;
import vn.sunvibe.survey.core.application.service.SurveyMediaQueryService;
import vn.sunvibe.survey.core.application.shared.MediaAccessLogger;
import vn.sunvibe.survey.core.application.shared.MediaReadBudgetLimits;
import vn.sunvibe.survey.core.application.shared.MediaReadBudgets;
import vn.sunvibe.survey.core.application.shared.ChecklistContextLoader;
import vn.sunvibe.survey.core.application.shared.IntakeGate;
import vn.sunvibe.survey.core.application.shared.MediaBodySlots;
import vn.sunvibe.survey.core.application.shared.MediaClock;
import vn.sunvibe.survey.core.application.shared.MediaInstanceSettings;
import vn.sunvibe.survey.core.application.shared.MediaItemAssembler;
import vn.sunvibe.survey.core.application.shared.MediaLimitCounter;
import vn.sunvibe.survey.core.application.shared.SecurityEventRecorder;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupDocument;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.model.media.MediaPlacement;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;

/**
 * The survey-media command service wired with hand-written fakes over the checklist harness (survey-media plan Task
 * A13). Every fake writes to the one journal of {@link ChecklistServiceHarness}, so a test can assert the lock order of
 * spec 5.4.
 */
public final class MediaServiceHarness {

    public static final String SALE_A = ChecklistServiceHarness.SALE_A;
    public static final String SALE_B = ChecklistServiceHarness.SALE_B;
    public static final String MANAGER = ChecklistServiceHarness.MANAGER;

    /** This instance's id in body slots (plan PD-12). */
    public static final String INSTANCE = "01JINSTANCE000000000000001";

    private static final AtomicInteger KEYS = new AtomicInteger();

    public final ChecklistServiceHarness base = new ChecklistServiceHarness();
    public final List<String> journal = base.journal;
    public final InMemoryMediaRepositories.Media media = new InMemoryMediaRepositories.Media(journal);
    public final InMemoryMediaRepositories.Sets sets = new InMemoryMediaRepositories.Sets(journal);
    public final InMemoryMediaRepositories.Counters counters = new InMemoryMediaRepositories.Counters(journal, media);
    public final MediaClock clock = () -> Instant.now().truncatedTo(ChronoUnit.MICROS);
    public final InMemoryIntakeHealth health = new InMemoryIntakeHealth().open(clock.now());
    public final MutableMediaSwitches switches = new MutableMediaSwitches();
    public final TestTransactions transactions = new TestTransactions();
    public final InMemoryMediaRepositories.Jobs jobs = new InMemoryMediaRepositories.Jobs(journal);
    public final InMemoryMediaRepositories.Variants variants = new InMemoryMediaRepositories.Variants();
    public final InMemoryMediaRepositories.BodySlots slots = new InMemoryMediaRepositories.BodySlots(journal);
    public final InMemoryMediaStore store = new InMemoryMediaStore(journal);
    public final InMemoryBodySpool spool = new InMemoryBodySpool();
    public final RecordingProbeRequests probes = new RecordingProbeRequests();
    /** Photos a submission references (spec 4.7, BR-8, checklist submission spec I-5). */
    public final java.util.Set<String> referenced = new java.util.HashSet<>();
    public final InMemoryReviewRepositories.Selections selections;
    public final vn.sunvibe.survey.core.application.shared.RevisionPhotos revisionPhotos;
    public final vn.sunvibe.survey.core.application.shared.MediaSetLocks setLocks;
    public final vn.sunvibe.survey.core.application.shared.external.media.EvidenceReferencePort references =
            id -> referenced.contains(id.getValue());
    public final MediaBodySlots bodySlots;
    public final SecurityEventRecorder recorder;
    public final SurveyMediaCommandService commands;
    public final SurveyMediaContentService contents;
    public final SurveyMediaQueryService lists;

    public MediaServiceHarness() {
        counters.eventSource = base.securityEvents::all;
        IdGenerator<Long> snowflakes = new AtomicLong(70_000L)::incrementAndGet;
        recorder = new SecurityEventRecorder(base.securityEvents, counters, transactions, snowflakes, clock);
        ChecklistContextLoader loader = new ChecklistContextLoader(base.checklists, base.appointments, base.leads,
                base.directory, recorder);
        IntakeGate gate = new IntakeGate(health, clock);
        selections = new InMemoryReviewRepositories.Selections(journal);
        revisionPhotos = new vn.sunvibe.survey.core.application.shared.RevisionPhotos(media, selections);
        setLocks = new vn.sunvibe.survey.core.application.shared.MediaSetLocks(sets, selections);
        commands = new SurveyMediaCommandService(loader, base.groups, media, sets, jobs, selections, revisionPhotos,
                setLocks, base.leads, references, recorder,
                new MediaLimitCounter(counters), gate, switches, new MediaItemAssembler(base.directory), clock,
                () -> UlidCreator.getMonotonicUlid().toString(), transactions);
        bodySlots = new MediaBodySlots(slots, counters, clock, transactions,
                new MediaInstanceSettings(INSTANCE, 3, 2));
        contents = new SurveyMediaContentService(commands, bodySlots, store, spool, probes, clock);
        counters.accessLogSource = base.accessLogs::all;
        lists = new SurveyMediaQueryService(loader, base.checklists, base.appointments, base.groups, revisionPhotos, sets,
                health, base.directory, switches, new MediaItemAssembler(base.directory),
                new MediaReadBudgets(counters, recorder, clock, transactions, MediaReadBudgetLimits.SPEC),
                new MediaAccessLogger(base.accessLogs, counters, snowflakes, clock, transactions), recorder,
                transactions);
    }

    /**
     * SALE_A, the assignee of a SURVEY_SCHEDULED lead, starts a checklist on its visit; G1 is the whole property with
     * {@code SPACE_1} "A1". The journal is cleared.
     */
    public String checklist(String leadId) {
        ChecklistStartResult started = base.started(leadId, SALE_A);
        String id = started.view().checklist().getId().getValue();
        g1(id, ChecklistFixture.identificationWhole());
        media.checklistLeadAndVisit.put(id, new String[] {leadId,
                started.view().checklist().getVisitAppointmentId().getValue()});
        journal.clear();
        return id;
    }

    public void g1(String checklistId, IdentificationContent g1) {
        base.groups.put(SurveyChecklistId.of(checklistId), ChecklistGroup.IDENTIFICATION, new GroupState(
                new GroupDocument(ChecklistGroup.IDENTIFICATION, g1, Map.of(), Map.of()), 2L, clock.now(), SALE_A));
    }

    /** A create of the asset's exterior with a fresh upload key. */
    public static CreateMediaCommand exterior(String checklistId, String sha256) {
        return new CreateMediaCommand(checklistId, newKey(), null, EvidenceCategory.EXTERIOR_ACCESS, null, 1000L,
                sha256);
    }

    public static String newKey() {
        return UlidCreator.getMonotonicUlid().toString();
    }

    /** A stored photo of the checklist awaiting content, as another transaction would have written it. */
    public SurveyMedia stored(String checklistId, String uploader, String sha256, long sizeBytes, Instant createdAt) {
        int n = KEYS.incrementAndGet();
        SurveyMedia photo = SurveyMedia.create(MediaFixture.newId(), checklistId, newKey(), n,
                MediaPlacement.asset(), EvidenceCategory.DETAIL, null, uploader, sizeBytes, sha256, createdAt);
        media.put(photo);
        return photo;
    }
}
