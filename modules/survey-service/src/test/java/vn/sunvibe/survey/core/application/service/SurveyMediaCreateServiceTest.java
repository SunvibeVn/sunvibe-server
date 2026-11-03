package vn.sunvibe.survey.core.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;

import vn.sunvibe.shared.logging.RequestId;
import vn.sunvibe.survey.core.application.command.CreateMediaCommand;
import vn.sunvibe.survey.core.application.exception.ChecklistNotFoundException;
import vn.sunvibe.survey.core.application.exception.ChecklistReadOnlyException;
import vn.sunvibe.survey.core.application.exception.ChecklistTakeoverRequiredException;
import vn.sunvibe.survey.core.application.exception.MediaContentIdentityException;
import vn.sunvibe.survey.core.application.exception.MediaIntakePausedException;
import vn.sunvibe.survey.core.application.exception.MediaLimitReachedException;
import vn.sunvibe.survey.core.application.exception.MediaRateLimitedException;
import vn.sunvibe.survey.core.application.exception.MediaUploadDisabledException;
import vn.sunvibe.survey.core.application.exception.MediaUploadKeyReusedException;
import vn.sunvibe.survey.core.application.exception.SurveyorIneligibleException;
import vn.sunvibe.survey.core.application.query.MediaCreateResult;
import vn.sunvibe.survey.core.application.query.MediaItemView;
import vn.sunvibe.survey.core.domain.exception.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.model.media.MediaCounterKind;
import vn.sunvibe.survey.core.domain.model.media.MediaPlacement;
import vn.sunvibe.survey.core.domain.model.media.MediaStatus;
import vn.sunvibe.survey.core.domain.model.media.MediaUnavailableReason;
import vn.sunvibe.survey.core.domain.model.media.PauseReason;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.model.security.SecurityOutcome;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.model.security.SurveySecurityEvent;
import vn.sunvibe.survey.core.domain.policy.media.IdentityVerdict;
import vn.sunvibe.survey.core.domain.policy.media.ReadBudgetArithmetic;
import vn.sunvibe.survey.core.testsupport.ChecklistFixture;
import vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness;
import vn.sunvibe.survey.core.testsupport.MediaFixture;
import vn.sunvibe.survey.core.testsupport.MediaServiceHarness;

/**
 * Create a photo record (survey-media spec 4.4 step 1, the create order of 5.4, A-3, A-5, A-11, A-16, A-19, A-27; plan
 * Task A13): one test per step, each step answering before the next, and the lock order of 5.4. Hand-written fakes;
 * the 413 of step (0) is the body filter's ({@code MediaJsonBodyLimitFilterTest}).
 */
class SurveyMediaCreateServiceTest {

    private static final String LEAD = ChecklistServiceHarness.LEAD;
    private static final String SALE_A = MediaServiceHarness.SALE_A;
    private static final String SALE_B = MediaServiceHarness.SALE_B;

    private final MediaServiceHarness h = new MediaServiceHarness();
    private final String checklist = h.checklist(LEAD);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    // ==================== the created photo ====================

    @Test
    void create_newPhoto_awaitsContentWithNumberAndSetVersion() {
        MediaCreateResult first = create(MediaServiceHarness.exterior(checklist, MediaFixture.sha(1)));
        MediaCreateResult second = create(MediaServiceHarness.exterior(checklist, MediaFixture.sha(2)));

        assertTrue(first.created());
        MediaItemView item = first.media();
        assertEquals(1, item.mediaNo());
        assertEquals(1L, first.setVersion());
        assertEquals(MediaStatus.AWAITING_CONTENT, item.status());
        assertEquals(SALE_A, item.uploadedByUserId());
        assertEquals("Sale A", item.uploadedByNameOrNull());
        assertFalse(item.contentAvailable());
        assertEquals(MediaUnavailableReason.NOT_READY, item.unavailableReasonOrNull());
        assertNull(item.removedOrNull());
        assertNull(item.heldOrNull());
        assertEquals(2, second.media().mediaNo());
        assertEquals(2L, second.setVersion());
        assertEquals(2, h.media.all().size());
    }

    @Test
    void create_spaceAndCaption_keepTheSpaceCodeAndCleanCaption() {
        MediaCreateResult result = create(new CreateMediaCommand(checklist, MediaServiceHarness.newKey(),
                ChecklistFixture.SPACE_1, EvidenceCategory.MAIN_SPACE, "  Phòng khách  ", 1000L,
                MediaFixture.sha(3)));

        assertEquals(ChecklistFixture.SPACE_1, result.media().spaceIdOrNull());
        assertEquals("A1", result.media().spaceCodeAtUploadOrNull());
        assertEquals("Phòng khách", result.media().captionOrNull());
        assertFalse(result.media().captionHidden());
    }

    @Test
    void create_lockOrder_leadChecklistG1AdvisorySetPhoto() {
        create(MediaServiceHarness.exterior(checklist, MediaFixture.sha(4)));

        List<String> j = h.journal;
        int lead = j.indexOf("lead:share");
        int checklistLock = j.indexOf("checklist:share");
        int g1 = j.indexOf("group:share:IDENTIFICATION");
        int user = j.indexOf("user:lock");
        int setInsert = j.indexOf("set:insert");
        int set = j.indexOf("set:lock");
        int insert = j.indexOf("media:insert");
        assertTrue(lead >= 0 && lead < checklistLock && checklistLock < g1 && g1 < user && user < setInsert
                && setInsert < set && set < insert, j.toString());
        assertEquals(-1, j.indexOf("checklist:read"), "the checklist row is first loaded by its share lock: " + j);
        assertTrue(j.indexOf("media:byKey") < g1 && j.lastIndexOf("media:byKey") > set,
                "the replay is checked before the locks and again after them: " + j);
        assertTrue(j.indexOf("set:update") > insert, j.toString());
        assertFalse(j.contains("lead:activity:CHECKLIST_MEDIA_ADDED"), "no History at create: " + j);
    }

    // ==================== (1) body ====================

    @Test
    void invalidBody_400BeforeNotFound() {
        CreateMediaCommand command = new CreateMediaCommand("01JNOSUCHCHECKLIST00000000", "not-a-ulid", "bad",
                null, "0912345678", 0L, "XYZ");

        ChecklistValidationException refused = assertThrows(ChecklistValidationException.class,
                () -> create(command));

        assertEquals(List.of("caption", "category", "contentSha256", "sizeBytes", "spaceId", "uploadKey"),
                refused.violations().stream().map(v -> v.path()).sorted().toList());
        assertEquals(List.of(), h.journal, "no lookup before the body is valid");
    }

    @Test
    void sizeBounds_20MiBAccepted_oneMoreRefused() {
        assertTrue(create(new CreateMediaCommand(checklist, MediaServiceHarness.newKey(), null,
                EvidenceCategory.EXTERIOR_ACCESS, null, 20_971_520L, MediaFixture.sha(5))).created());

        ChecklistValidationException refused = assertThrows(ChecklistValidationException.class,
                () -> create(new CreateMediaCommand(checklist, MediaServiceHarness.newKey(), null,
                        EvidenceCategory.EXTERIOR_ACCESS, null, 20_971_521L, MediaFixture.sha(6))));
        assertEquals("sizeBytes", refused.violations().get(0).path());
    }

    @Test
    void contentSha256_upperCaseHex_isStoredLowerCase() {
        String upper = MediaFixture.sha(0xABCDEF).toUpperCase();

        MediaCreateResult result = create(MediaServiceHarness.exterior(checklist, upper));

        assertEquals(upper.toLowerCase(), h.media.stored(
                vn.sunvibe.survey.core.domain.model.media.SurveyMediaId.of(result.media().id())).orElseThrow()
                .getSourceSha256());
    }

    // ==================== (2) 404, (3) 403 ====================

    @Test
    void hiddenLead_404WithThrottledEvent() {
        MDC.put(RequestId.MDC_KEY, "01JREQUEST0000000000000003");

        assertThrows(ChecklistNotFoundException.class, () -> h.commands.create(ChecklistServiceHarness.own(SALE_B),
                false, MediaServiceHarness.exterior(checklist, MediaFixture.sha(7))));
        assertThrows(ChecklistNotFoundException.class, () -> h.commands.create(ChecklistServiceHarness.own(SALE_B),
                false, MediaServiceHarness.exterior(checklist, MediaFixture.sha(7))));

        List<SurveySecurityEvent> events = h.base.securityEvents.all();
        assertEquals(1, events.size(), "throttled to one a minute");
        SurveySecurityEvent event = events.get(0);
        assertEquals(SecurityResourceType.MEDIA_SET, event.resourceType());
        assertEquals(checklist, event.resourceId());
        assertEquals(SecurityAction.UPLOAD, event.action());
        assertEquals(SecurityOutcome.NOT_VISIBLE, event.outcome());
        assertEquals("01JREQUEST0000000000000003", event.requestIdOrNull());
    }

    @Test
    void unknownChecklist_404WithoutEvent() {
        assertThrows(ChecklistNotFoundException.class, () -> create(MediaServiceHarness.exterior(
                "01JNOSUCHCHECKLIST00000000", MediaFixture.sha(8))));

        assertEquals(List.of(), h.base.securityEvents.all());
    }

    @Test
    void notAssignee_403WithEvent() {
        assertThrows(AccessDeniedException.class, () -> h.commands.create(
                ChecklistServiceHarness.all(MediaServiceHarness.MANAGER), false,
                MediaServiceHarness.exterior(checklist, MediaFixture.sha(9))));

        SurveySecurityEvent event = h.base.securityEvents.all().get(0);
        assertEquals(SecurityOutcome.NOT_ASSIGNEE, event.outcome());
        assertEquals(SecurityResourceType.MEDIA_SET, event.resourceType());
        assertTrue(h.media.all().isEmpty());
    }

    // ==================== (4) 409 read-only, (5) 422, (6) 409 takeover ====================

    @Test
    void readOnly_409BeforeIneligible() {
        h.base.lead(LEAD, "ON_HOLD", SALE_A);
        h.base.directory.makeIneligible(SALE_A);

        assertThrows(ChecklistReadOnlyException.class, () -> create(MediaServiceHarness.exterior(checklist,
                MediaFixture.sha(10))));
    }

    @Test
    void ineligible_422BeforeTakeover() {
        h.base.lead(LEAD, "SURVEY_SCHEDULED", SALE_B);
        h.base.directory.makeIneligible(SALE_B);

        assertThrows(SurveyorIneligibleException.class, () -> h.commands.create(ChecklistServiceHarness.own(SALE_B),
                false, MediaServiceHarness.exterior(checklist, MediaFixture.sha(11))));
    }

    @Test
    void takeoverRequired_409BeforeReplay() {
        h.base.lead(LEAD, "SURVEY_SCHEDULED", SALE_B);

        assertThrows(ChecklistTakeoverRequiredException.class, () -> h.commands.create(
                ChecklistServiceHarness.own(SALE_B), false, MediaServiceHarness.exterior(checklist,
                        MediaFixture.sha(12))));
        assertFalse(h.journal.contains("media:byKey"), h.journal.toString());
    }

    // ==================== (7) replay ====================

    @Test
    void replay_beforeDisabledAndPaused() {
        CreateMediaCommand command = MediaServiceHarness.exterior(checklist, MediaFixture.sha(13));
        MediaCreateResult first = create(command);
        h.switches.uploads = false;
        h.health.pause(PauseReason.STORE, h.clock.now());

        MediaCreateResult replay = create(command);

        assertFalse(replay.created());
        assertEquals(first.media().id(), replay.media().id());
        assertEquals(1L, replay.setVersion());
        assertEquals(1, h.media.all().size());
    }

    @Test
    void sameKeyOtherBody_409() {
        CreateMediaCommand command = MediaServiceHarness.exterior(checklist, MediaFixture.sha(14));
        create(command);

        MediaUploadKeyReusedException refused = assertThrows(MediaUploadKeyReusedException.class,
                () -> create(new CreateMediaCommand(checklist, command.uploadKey(), null,
                        EvidenceCategory.EXTERIOR_ACCESS, "Mặt tiền", 1000L, MediaFixture.sha(14))));
        assertEquals("uploadKey", refused.getField());
    }

    @Test
    void sameKeyOnAnotherChecklist_isIndependent() {
        String other = h.checklist(ChecklistServiceHarness.OTHER_LEAD);
        CreateMediaCommand command = MediaServiceHarness.exterior(checklist, MediaFixture.sha(15));
        create(command);

        MediaCreateResult elsewhere = create(new CreateMediaCommand(other, command.uploadKey(), null,
                EvidenceCategory.EXTERIOR_ACCESS, null, 1000L, MediaFixture.sha(15)));

        assertTrue(elsewhere.created());
    }

    @Test
    void concurrentTwinCommittedBeforeInsert_keyTaken_answersTheReplay() {
        CreateMediaCommand command = MediaServiceHarness.exterior(checklist, MediaFixture.sha(16));
        h.media.beforeInsert = photo -> {
            h.media.beforeInsert = p -> { };
            h.media.put(SurveyMedia.create(MediaFixture.newId(), checklist, command.uploadKey(), 1,
                    MediaPlacement.asset(), EvidenceCategory.EXTERIOR_ACCESS, null, SALE_A, 1000L,
                    MediaFixture.sha(16), h.clock.now()));
        };

        MediaCreateResult result = create(command);

        assertFalse(result.created(), "a unique violation on the upload key is the replay, never a 500");
        assertEquals(List.of("begin:REQUIRED", "rollback", "begin:REQUIRED", "commit"), h.transactions.calls);
    }

    @Test
    void concurrentTwinOtherBodyBeforeInsert_keyTaken_409KeyReused() {
        CreateMediaCommand command = MediaServiceHarness.exterior(checklist, MediaFixture.sha(17));
        h.media.beforeInsert = photo -> {
            h.media.beforeInsert = p -> { };
            h.media.put(SurveyMedia.create(MediaFixture.newId(), checklist, command.uploadKey(), 1,
                    MediaPlacement.asset(), EvidenceCategory.DETAIL, null, SALE_A, 1000L, MediaFixture.sha(99),
                    h.clock.now()));
        };

        assertThrows(MediaUploadKeyReusedException.class, () -> create(command));
    }

    // ==================== (8) disabled, (9) paused ====================

    @Test
    void uploadsNeverOn_409WritesNoPhoto() {
        h.switches.uploads = false;

        assertThrows(MediaUploadDisabledException.class, () -> create(MediaServiceHarness.exterior(checklist,
                MediaFixture.sha(18))));

        assertTrue(h.media.all().isEmpty());
        assertTrue(h.sets.stored(checklist).isEmpty());
        assertFalse(h.journal.contains("set:insert"), h.journal.toString());
    }

    @Test
    void paused_503() {
        h.health.pause(PauseReason.STORE, h.clock.now());

        MediaIntakePausedException paused = assertThrows(MediaIntakePausedException.class,
                () -> create(MediaServiceHarness.exterior(checklist, MediaFixture.sha(19))));

        assertFalse(paused.sweepStale());
        assertEquals(List.of("read"), h.health.calls);
        assertTrue(h.media.all().isEmpty());
    }

    @Test
    void staleSweep_503_recordedAsPauseAfterTheTransaction() {
        h.health.sweptAt(h.clock.now().minus(Duration.ofMinutes(11)));

        assertThrows(MediaIntakePausedException.class, () -> create(MediaServiceHarness.exterior(checklist,
                MediaFixture.sha(20))));

        assertEquals(List.of("read", "failure:SWEEP_STALE:pause"), h.health.calls);
        assertEquals(List.of("begin:REQUIRED", "rollback"), h.transactions.calls);
    }

    // ==================== (10) stored data ====================

    @Test
    void spaceNotInG1_400() {
        ChecklistValidationException refused = assertThrows(ChecklistValidationException.class,
                () -> create(new CreateMediaCommand(checklist, MediaServiceHarness.newKey(), ChecklistFixture.SPACE_2,
                        EvidenceCategory.MAIN_SPACE, null, 1000L, MediaFixture.sha(21))));

        assertEquals("spaceId", refused.violations().get(0).path());
        assertTrue(h.journal.contains("group:share:IDENTIFICATION"), h.journal.toString());
        assertFalse(h.journal.contains("user:lock"), h.journal.toString());
    }

    @Test
    void sharedCategoryOutsideParts_400() {
        ChecklistValidationException refused = assertThrows(ChecklistValidationException.class,
                () -> create(new CreateMediaCommand(checklist, MediaServiceHarness.newKey(), null,
                        EvidenceCategory.SHARED_KITCHEN, null, 1000L, MediaFixture.sha(22))));

        assertEquals("category", refused.violations().get(0).path());
    }

    // ==================== (11) 429 ====================

    @Test
    void hourlyRecords_429() {
        Instant now = h.clock.now();
        for (int i = 0; i < 300; i++) {
            h.stored("01JOTHERCHECKLIST000000001", SALE_A, MediaFixture.sha(1000 + i), 1000L,
                    now.minus(Duration.ofMinutes(50)));
        }

        MediaRateLimitedException refused = assertThrows(MediaRateLimitedException.class,
                () -> create(MediaServiceHarness.exterior(checklist, MediaFixture.sha(23))));

        assertEquals("records", refused.getField());
        assertTrue(refused.getRetryAfterSeconds() >= 590 && refused.getRetryAfterSeconds() <= 601,
                "until the oldest leaves the hour: " + refused.getRetryAfterSeconds());
    }

    @Test
    void hourlyBytes_429() {
        Instant now = h.clock.now();
        for (int i = 0; i < 153; i++) {
            h.stored("01JOTHERCHECKLIST000000001", SALE_A, MediaFixture.sha(2000 + i), 20_971_520L,
                    now.minus(Duration.ofMinutes(5)));
        }

        MediaRateLimitedException refused = assertThrows(MediaRateLimitedException.class,
                () -> create(new CreateMediaCommand(checklist, MediaServiceHarness.newKey(), null,
                        EvidenceCategory.EXTERIOR_ACCESS, null, 20_971_520L, MediaFixture.sha(24))));

        assertEquals("bytes", refused.getField());
    }

    @Test
    void expensiveAtCap_429() {
        Instant now = h.clock.now();
        h.counters.add(SALE_A, ReadBudgetArithmetic.hourStart(now), MediaCounterKind.EXPENSIVE, 20);

        MediaRateLimitedException refused = assertThrows(MediaRateLimitedException.class,
                () -> create(MediaServiceHarness.exterior(checklist, MediaFixture.sha(25))));

        assertEquals("expensive", refused.getField());
        long untilNextHour = Duration.between(now, ReadBudgetArithmetic.hourStart(now).plus(Duration.ofHours(1)))
                .toSeconds();
        assertTrue(Math.abs(refused.getRetryAfterSeconds() - untilNextHour) <= 1, refused.getRetryAfterSeconds()
                + " vs " + untilNextHour);
    }

    @Test
    void expensiveBelowCap_passes() {
        h.counters.add(SALE_A, ReadBudgetArithmetic.hourStart(h.clock.now()), MediaCounterKind.EXPENSIVE, 19);

        assertTrue(create(MediaServiceHarness.exterior(checklist, MediaFixture.sha(26))).created());
    }

    // ==================== (12) identity, (13) limits ====================

    @Test
    void duplicate_beforeOtherVisit() {
        String sha = MediaFixture.sha(27);
        SurveyMedia same = h.stored(checklist, SALE_A, sha, 1000L, h.clock.now().minus(Duration.ofHours(2)));
        committedOnOtherVisit(sha);

        MediaContentIdentityException refused = assertThrows(MediaContentIdentityException.class,
                () -> create(MediaServiceHarness.exterior(checklist, sha)));

        assertEquals(IdentityVerdict.Kind.DUPLICATE_CONTENT, refused.getKind());
        assertEquals(same.getId().getValue(), refused.getMatchingMediaIdOrNull());
    }

    @Test
    void fromOtherVisit_409WithTheMatchingId() {
        String sha = MediaFixture.sha(28);
        SurveyMedia earlier = committedOnOtherVisit(sha);

        MediaContentIdentityException refused = assertThrows(MediaContentIdentityException.class,
                () -> create(MediaServiceHarness.exterior(checklist, sha)));

        assertEquals(IdentityVerdict.Kind.FROM_OTHER_VISIT, refused.getKind());
        assertEquals(earlier.getId().getValue(), refused.getMatchingMediaIdOrNull());
    }

    @Test
    void duplicateBackstopAtInsert_409WithTheMatchingId() {
        String sha = MediaFixture.sha(29);
        SurveyMedia twin = SurveyMedia.create(MediaFixture.newId(), checklist, MediaServiceHarness.newKey(), 1,
                MediaPlacement.asset(), EvidenceCategory.EXTERIOR_ACCESS, null, SALE_A, 1000L, sha, h.clock.now());
        h.media.beforeInsert = photo -> {
            h.media.beforeInsert = p -> { };
            h.media.put(twin);
        };

        MediaContentIdentityException refused = assertThrows(MediaContentIdentityException.class,
                () -> create(MediaServiceHarness.exterior(checklist, sha)));

        assertEquals(IdentityVerdict.Kind.DUPLICATE_CONTENT, refused.getKind());
        assertEquals(twin.getId().getValue(), refused.getMatchingMediaIdOrNull());
    }

    @Test
    void limit_409Selected() {
        for (int i = 0; i < 100; i++) {
            SurveyMedia photo = h.stored(checklist, SALE_A, MediaFixture.sha(3000 + i), 1000L,
                    h.clock.now().minus(Duration.ofHours(2)));
            if (i >= 10) {
                MediaFixture.ready(photo);
                h.media.put(photo);
            }
        }

        MediaLimitReachedException refused = assertThrows(MediaLimitReachedException.class,
                () -> create(MediaServiceHarness.exterior(checklist, MediaFixture.sha(30))));

        assertEquals("selected", refused.getField());
    }

    @Test
    void limit_409Pending() {
        for (int i = 0; i < 20; i++) {
            h.stored(checklist, SALE_A, MediaFixture.sha(4000 + i), 1000L, h.clock.now().minus(Duration.ofHours(2)));
        }

        MediaLimitReachedException refused = assertThrows(MediaLimitReachedException.class,
                () -> create(MediaServiceHarness.exterior(checklist, MediaFixture.sha(31))));

        assertEquals("pending", refused.getField());
    }

    @Test
    void notSelectedPhotos_freeTheirPlace() {
        for (int i = 0; i < 20; i++) {
            SurveyMedia photo = h.stored(checklist, SALE_A, MediaFixture.sha(5000 + i), 1000L,
                    h.clock.now().minus(Duration.ofHours(2)));
            photo.expire(h.clock.now().minus(Duration.ofMinutes(30)));
            h.media.put(photo);
        }

        assertTrue(create(MediaServiceHarness.exterior(checklist, MediaFixture.sha(5000))).created(),
                "an expired photo frees its place and its bytes constrain nothing");
    }

    private MediaCreateResult create(CreateMediaCommand command) {
        return h.commands.create(ChecklistServiceHarness.own(SALE_A), false, command);
    }

    /** A committed photo with this hash on a checklist of another visit of the same lead. */
    private SurveyMedia committedOnOtherVisit(String sha) {
        String otherChecklist = "01JOTHERV1S1TCHECK11ST0001";
        h.media.checklistLeadAndVisit.put(otherChecklist, new String[] {LEAD, "01JOTHERV1S1T0000000000001"});
        Instant at = h.clock.now().minus(Duration.ofDays(3));
        SurveyMedia photo = SurveyMedia.create(MediaFixture.newId(), otherChecklist, MediaServiceHarness.newKey(), 1,
                MediaPlacement.asset(), EvidenceCategory.EXTERIOR_ACCESS, null, SALE_A, 1000L, sha, at);
        photo.claim(MediaFixture.TOKEN, "quarantine/" + photo.getId() + "/1", at.plusSeconds(10));
        photo.commit("quarantine/" + photo.getId() + "/1", 1000L, at.plusSeconds(20));
        h.media.put(photo);
        return photo;
    }
}
