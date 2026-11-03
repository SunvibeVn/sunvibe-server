package vn.sunvibe.survey.core.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import vn.sunvibe.survey.core.application.exception.ChecklistNotFoundException;
import vn.sunvibe.survey.core.application.exception.MediaRateLimitedException;
import vn.sunvibe.survey.core.application.query.MediaItemView;
import vn.sunvibe.survey.core.application.query.MediaListView;
import vn.sunvibe.survey.core.domain.exception.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.model.log.AccessResourceType;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.model.media.HoldReason;
import vn.sunvibe.survey.core.domain.model.media.MediaCounterKind;
import vn.sunvibe.survey.core.domain.model.media.MediaPlacement;
import vn.sunvibe.survey.core.domain.model.media.MediaUnavailableReason;
import vn.sunvibe.survey.core.domain.model.media.PauseReason;
import vn.sunvibe.survey.core.domain.model.media.RemovalKind;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.security.SecurityOutcome;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.policy.media.CoverageState;
import vn.sunvibe.survey.core.domain.policy.media.ReadBudgetArithmetic;
import vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness;
import vn.sunvibe.survey.core.testsupport.MediaFixture;
import vn.sunvibe.survey.core.testsupport.MediaServiceHarness;

/**
 * The media list (survey-media spec 4.3, 5.4 list order, 7.2, A-6, A-19, A-26, A-30; plan Task A16): (1) a malformed
 * known version 400 before any lookup, (2) 404 with a throttled event, (3) the list budget, then 204 or 200 and one
 * access row per user and checklist per 10 minutes. Hand-written fakes.
 */
class SurveyMediaListServiceTest {

    private static final String SALE_A = MediaServiceHarness.SALE_A;
    private static final String LEAD = ChecklistServiceHarness.LEAD;

    private final MediaServiceHarness h = new MediaServiceHarness();
    private final String checklist = h.checklist(LEAD);

    @Test
    void malformedKnownVersion_400BeforeAnyLookup() {
        ChecklistValidationException refused = assertThrows(ChecklistValidationException.class,
                () -> list(SALE_A, "1.2.3"));

        assertEquals("knownVersion", refused.violations().get(0).path());
        assertEquals(List.of(), h.journal);
    }

    @Test
    void hiddenLead_404WithThrottledEvent_noBudgetUsed() {
        assertThrows(ChecklistNotFoundException.class, () -> h.lists.list(ChecklistServiceHarness.own(
                MediaServiceHarness.SALE_B), false, checklist, null));

        assertEquals(SecurityResourceType.MEDIA_SET, h.base.securityEvents.all().get(0).resourceType());
        assertEquals(SecurityOutcome.NOT_VISIBLE, h.base.securityEvents.all().get(0).outcome());
        assertEquals(0, h.counters.counterValue(MediaServiceHarness.SALE_B, ReadBudgetArithmetic.hourStart(
                h.clock.now()), MediaCounterKind.LIST));
    }

    @Test
    void budgetSpent_429NoAccessRow() {
        h.counters.add(SALE_A, ReadBudgetArithmetic.hourStart(h.clock.now()), MediaCounterKind.LIST, 1_200);

        MediaRateLimitedException refused = assertThrows(MediaRateLimitedException.class,
                () -> list(SALE_A, null));

        assertEquals("budget", refused.getField());
        assertEquals(List.of(), listRows());
        assertEquals(SecurityOutcome.BUDGET_EXCEEDED, h.base.securityEvents.all().get(0).outcome());
    }

    @Test
    void list_partitionsItemsNotAcceptedRemoved_withTotalsLimitsAndCoverage() {
        SurveyMedia ready = photo(1);
        MediaFixture.ready(ready);
        h.media.put(ready);
        SurveyMedia awaiting = photo(2);
        h.media.put(awaiting);
        SurveyMedia expired = photo(3);
        expired.expire(h.clock.now());
        h.media.put(expired);
        SurveyMedia removed = photo(4);
        removed.remove(RemovalKind.HOLDER, SALE_A, null, null, h.clock.now());
        h.media.put(removed);

        MediaListView view = list(SALE_A, null).orElseThrow();

        assertEquals(List.of(1, 2), view.items().stream().map(MediaItemView::mediaNo).toList());
        assertEquals(List.of(3), view.notAcceptedItems().stream().map(MediaItemView::mediaNo).toList());
        assertEquals(List.of(4), view.removedItems().stream().map(MediaItemView::mediaNo).toList());
        assertEquals(1, view.totalNotAccepted());
        assertEquals(1, view.totalRemoved());
        assertEquals(2, view.limits().selectedCount());
        assertEquals(1, view.limits().pendingCount());
        assertEquals(100, view.limits().maxSelected());
        assertEquals(20, view.limits().maxPending());
        assertEquals(20_971_520L, view.limits().maxFileBytes());
        assertEquals(CoverageState.COVERED, view.coverage().stream()
                .filter(pair -> pair.category() == EvidenceCategory.EXTERIOR_ACCESS).findFirst().orElseThrow()
                .state());
        assertTrue(view.uploadEnabled());
    }

    @Test
    void knownVersionUnchanged_empty_andStillOneAccessRowPer10Minutes() {
        MediaListView first = list(SALE_A, null).orElseThrow();

        Optional<MediaListView> again = list(SALE_A, first.basis().format());

        assertTrue(again.isEmpty(), "204");
        assertEquals(1, listRows().size(), "one MEDIA_LIST row per 10 minutes, the 204 included");
        assertEquals(checklist, listRows().get(0).resourceIdOrNull());
        assertEquals(2, h.counters.counterValue(SALE_A, ReadBudgetArithmetic.hourStart(h.clock.now()),
                MediaCounterKind.LIST), "every list read counts, the 204 included");
    }

    @Test
    void anyInputChanged_200() {
        MediaListView first = list(SALE_A, null).orElseThrow();
        h.health.pause(PauseReason.STORE, h.clock.now());

        Optional<MediaListView> paused = list(SALE_A, first.basis().format());

        assertTrue(paused.isPresent(), "intake paused changes the view digest and the state epoch");
        assertTrue(paused.get().intakePaused());
        assertFalse(paused.get().uploadEnabled());
        h.base.lead(LEAD, "ON_HOLD", SALE_A);
        assertTrue(list(SALE_A, paused.get().basis().format()).isPresent(), "a lead put ON_HOLD gives a 200 at once");
    }

    @Test
    void managerViewer_uploadEnabledFalse_canHoldOnlyForAdmin() {
        MediaListView manager = h.lists.list(ChecklistServiceHarness.all(MediaServiceHarness.MANAGER), false,
                checklist, null).orElseThrow();
        MediaListView admin = h.lists.list(ChecklistServiceHarness.all(MediaServiceHarness.MANAGER), true,
                checklist, null).orElseThrow();

        assertFalse(manager.uploadEnabled());
        assertFalse(manager.canHold());
        assertTrue(admin.canHold());
    }

    @Test
    void heldPhoto_saleSeesOnlyHeldAt_adminSeesTheHold() {
        SurveyMedia held = photo(1);
        MediaFixture.ready(held);
        held.hold(MediaFixture.ADMIN, HoldReason.SECURITY_INCIDENT, h.clock.now());
        h.media.put(held);

        MediaItemView forSale = list(SALE_A, null).orElseThrow().items().get(0);
        MediaItemView forAdmin = h.lists.list(ChecklistServiceHarness.all(MediaServiceHarness.MANAGER), true,
                checklist, null).orElseThrow().items().get(0);

        assertNull(forSale.heldOrNull().byUserIdOrNull());
        assertNull(forSale.captionOrNull());
        assertTrue(forSale.captionHidden());
        assertEquals(MediaUnavailableReason.HELD, forSale.unavailableReasonOrNull());
        assertEquals(MediaFixture.ADMIN, forAdmin.heldOrNull().byUserIdOrNull());
        assertTrue(forAdmin.contentAvailable());
    }

    @Test
    void budgetUpsert_runsInItsOwnReadCommittedTransaction() {
        list(SALE_A, null);

        assertTrue(h.transactions.calls.contains("begin:REQUIRES_NEW:READ_COMMITTED"), h.transactions.calls
                .toString());
        assertTrue(h.transactions.calls.stream().anyMatch(call -> call.contains(":" + 4)),
                "the snapshot is REPEATABLE READ: " + h.transactions.calls);
    }

    private List<vn.sunvibe.survey.core.domain.model.log.SurveyAccessLog> listRows() {
        return h.base.accessLogs.all().stream().filter(row -> row.resourceTypeOrNull() == AccessResourceType.MEDIA_LIST)
                .toList();
    }

    private Optional<MediaListView> list(String userId, String knownVersion) {
        return h.lists.list(ChecklistServiceHarness.own(userId), false, checklist, knownVersion);
    }

    private SurveyMedia photo(int no) {
        h.sets.insertIgnoringExisting(checklist, h.clock.now());
        return SurveyMedia.create(MediaFixture.newId(), checklist, MediaServiceHarness.newKey(), no,
                MediaPlacement.asset(), EvidenceCategory.EXTERIOR_ACCESS, "Cổng", SALE_A, 1000L, MediaFixture.sha(no),
                h.clock.now().minus(Duration.ofMinutes(5)));
    }
}
