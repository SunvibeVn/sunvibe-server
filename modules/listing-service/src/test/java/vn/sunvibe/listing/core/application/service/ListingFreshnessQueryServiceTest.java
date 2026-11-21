package vn.sunvibe.listing.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.query.ListingFreshnessView;
import vn.sunvibe.listing.core.application.query.ReconfirmationListView;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingAnswerFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingRightPort;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementTermsFacts;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessChannel;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessOutcome;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessRecord;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessSummary;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;
import vn.sunvibe.listing.core.domain.policy.listing.FreshnessPolicy;
import vn.sunvibe.listing.core.domain.repository.ReconfirmationCandidate;
import vn.sunvibe.listing.core.testsupport.InMemoryListingRepository;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;
import vn.sunvibe.listing.core.testsupport.StubAdvertisingRightPort;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.draft;

/**
 * The freshness read and the listings to reconfirm (listing-publish US-6 AC-2, AC-3, BR-10, BR-17; E06-S3 spec 3.2, 3.3,
 * E06S3-D2, E06S3-D7, E06S3-D8, plan E06S3-P3), with fakes. Fixture dates are 8, 11, 13 and 16 days before the Vietnam
 * date of the JVM clock (E06S3-D14).
 */
class ListingFreshnessQueryServiceTest {

    private static final String LISTING = "01JLISTING0000000000000001";
    private static final String AGREEMENT = "01AGREEMENT";
    private static final String UNIT = "01UNIT";
    private static final String SALE = "01SALE";
    private static final String OTHER_SALE = "01OTHERSALE";
    private static final String NOTE = "Chủ xác nhận còn trống.\nGọi lại sau.";
    private static final ListingViewer SALE_VIEWER = new ListingViewer(SALE, Set.of("ROLE_SALE"));
    private static final ListingViewer MANAGER = new ListingViewer("01MANAGER", Set.of("ROLE_SALES_MANAGER"));
    private static final LocalDate TODAY = FreshnessPolicy.today(Instant.now());

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final StubAdvertisingRightPort stubRights = new StubAdvertisingRightPort();
    private final CountingRightPort rights = new CountingRightPort(stubRights);
    private final ListingQueryService service = new ListingQueryService(listings, rights);

    @Test
    void freshness_readersInScope_latestWithNoteAndDates() {
        stored();
        standing(AGREEMENT, false, SALE);
        LocalDate confirmedOn = TODAY.minusDays(13);
        Instant recordedAt = Instant.parse("2026-11-01T02:10:00Z");
        listings.putFreshness(LISTING, new FreshnessSummary(confirmedOn, true), new FreshnessRecord("01RECORD", LISTING,
                4L, confirmedOn, recordedAt, SALE, FreshnessChannel.CALL, FreshnessOutcome.AVAILABLE, true, true, NOTE));
        List<String> callsBefore = listings.calls();

        List<ListingFreshnessView> views = List.of(service.freshness(LISTING, SALE_VIEWER),
                service.freshness(LISTING, MANAGER),
                service.freshness(LISTING, new ListingViewer("01REVIEWER", Set.of("ROLE_LISTING_REVIEWER"))));

        ListingFreshnessView expected = new ListingFreshnessView(LISTING, confirmedOn, confirmedOn.plusDays(10),
                confirmedOn.plusDays(14), true, false, new ListingFreshnessView.LatestRecord(confirmedOn, recordedAt,
                FreshnessChannel.CALL, FreshnessOutcome.AVAILABLE, true, true, true, NOTE));
        assertEquals(List.of(expected, expected, expected), views);
        List<String> oneRead = List.of("findRow " + LISTING, "findFreshnessSummary " + LISTING,
                "findLatestFreshnessRecord " + LISTING);
        List<String> threeReads = new ArrayList<>(oneRead);
        threeReads.addAll(oneRead);
        threeReads.addAll(oneRead);
        assertEquals(threeReads, listings.calls().subList(callsBefore.size(), listings.calls().size()));
    }

    @Test
    void freshness_outOfScopeUnknownOrMalformed_notFound() {
        stored();
        standing(AGREEMENT, false, SALE);

        assertEquals(List.of(ListingNotFoundException.class, ListingNotFoundException.class,
                        ListingNotFoundException.class),
                Arrays.asList(thrown(() -> service.freshness(LISTING, new ListingViewer(OTHER_SALE, Set.of("ROLE_SALE")))),
                        thrown(() -> service.freshness("01JLISTING0000000000000009", SALE_VIEWER)),
                        thrown(() -> service.freshness("01", SALE_VIEWER))));
    }

    @Test
    void freshness_noRecord_nullsAndFalse() {
        stored();
        standing(AGREEMENT, false, SALE);

        assertEquals(new ListingFreshnessView(LISTING, null, null, null, false, false, null),
                service.freshness(LISTING, SALE_VIEWER));
    }

    @Test
    void freshness_invalidLatestAfterValid_dateKeptNotHeld() {
        stored();
        standing(AGREEMENT, false, SALE);
        LocalDate confirmedOn = TODAY.minusDays(8);
        listings.putFreshness(LISTING, new FreshnessSummary(confirmedOn, false), new FreshnessRecord("01RECORD", LISTING,
                6L, TODAY, Instant.now(), SALE, FreshnessChannel.MESSAGE, FreshnessOutcome.NOT_AVAILABLE, null, null,
                null));

        ListingFreshnessView view = service.freshness(LISTING, SALE_VIEWER);

        assertEquals(Arrays.asList(confirmedOn, false), Arrays.asList(view.confirmedOnOrNull(),
                view.latestOrNull() == null ? null : view.latestOrNull().valid()));
    }

    @Test
    void reconfirmations_sale_onlyOwnBasisLeads() {
        candidate("01JLISTINGOWN0000000000001", "01AGROWN", TODAY.minusDays(16), true, SALE, false);
        candidate("01JLISTINGOTHER00000000001", "01AGROTHER", TODAY.minusDays(13), true, OTHER_SALE, false);

        List<String> listed = ids(service.reconfirmations(SALE_VIEWER));

        assertTrue(listed.contains("01JLISTINGOWN0000000000001"), listed.toString());
        assertFalse(listed.contains("01JLISTINGOTHER00000000001"), listed.toString());
    }

    @Test
    void reconfirmations_managerLeaderAdmin_everyDue() {
        candidate("01JLISTINGA000000000000001", "01AGRA", TODAY.minusDays(16), true, SALE, false);
        candidate("01JLISTINGB000000000000001", "01AGRB", TODAY.minusDays(13), false, OTHER_SALE, false);
        List<String> every = List.of("01JLISTINGA000000000000001", "01JLISTINGB000000000000001");

        assertEquals(List.of(every, every, every), List.of(ids(service.reconfirmations(MANAGER)),
                ids(service.reconfirmations(new ListingViewer("01LEADER", Set.of("ROLE_SALE_LEADER", "ROLE_SALE")))),
                ids(service.reconfirmations(new ListingViewer("01ADMIN", Set.of("ROLE_ADMIN"))))));
    }

    @Test
    void reconfirmations_endedOrMissingStanding_leftOut() {
        candidate("01JLISTINGINEFFECT00000001", "01AGRINEFFECT", TODAY.minusDays(16), true, SALE, false);
        candidate("01JLISTINGENDED0000000001", "01AGRENDED", TODAY.minusDays(13), true, SALE, true);
        listings.putCandidate(new ReconfirmationCandidate("01JLISTINGNOSTANDING00001", "TD-7K3M-Q9TX", "01AGRGONE",
                ListingState.UNPUBLISHED, new FreshnessSummary(TODAY.minusDays(11), true)));

        List<String> listed = ids(service.reconfirmations(MANAGER));

        assertTrue(listed.contains("01JLISTINGINEFFECT00000001"), listed.toString());
        assertFalse(listed.contains("01JLISTINGENDED0000000001"), listed.toString());
        assertFalse(listed.contains("01JLISTINGNOSTANDING00001"), listed.toString());
    }

    @Test
    void reconfirmations_fiftyOneKept_fiftyAndHasMore() {
        for (int n = 0; n < 51; n++) {
            candidate(String.format("01JLISTING%016d", n), String.format("01AGR%05d", n), TODAY.minusDays(16), true,
                    SALE, false);
        }

        ReconfirmationListView list = service.reconfirmations(SALE_VIEWER);

        assertEquals(50, list.items().size());
        assertTrue(list.hasMore());
    }

    @Test
    void reconfirmations_thousandOneCandidates_fourChunksAndHasMore() {
        for (int n = 0; n < 1001; n++) {
            candidate(String.format("01JLISTING%016d", n), String.format("01AGR%05d", n), TODAY.minusDays(16), true,
                    OTHER_SALE, false);
        }

        ReconfirmationListView list = service.reconfirmations(SALE_VIEWER);

        assertTrue(list.hasMore());
        assertTrue(listings.calls().stream().filter(call -> call.startsWith("findReconfirmationCandidates")).count() <= 4,
                listings.calls().toString());
        assertTrue(rights.standingsCalls() <= 4, String.valueOf(rights.standingsCalls()));
        assertEquals(List.of(250, 250, 250, 250), rights.standingsSizes());
    }

    @Test
    void reconfirmations_exactlyThousand_noMore() {
        for (int n = 0; n < 1000; n++) {
            candidate(String.format("01JLISTING%016d", n), String.format("01AGR%05d", n), TODAY.minusDays(16), true,
                    n % 25 == 0 ? SALE : OTHER_SALE, false);
        }

        ReconfirmationListView list = service.reconfirmations(SALE_VIEWER);

        assertEquals(40, list.items().size());
        assertFalse(list.hasMore());
    }

    @Test
    void reconfirmations_order_oldestFirstThenId() {
        candidate("01JLISTINGZ000000000000001", "01AGRZ", TODAY.minusDays(16), true, SALE, false);
        candidate("01JLISTINGC000000000000001", "01AGRC", TODAY.minusDays(11), true, SALE, false);
        candidate("01JLISTINGB000000000000001", "01AGRB", TODAY.minusDays(13), false, SALE, false);
        candidate("01JLISTINGA000000000000001", "01AGRA", TODAY.minusDays(13), true, SALE, false);

        ReconfirmationListView list = service.reconfirmations(SALE_VIEWER);

        LocalDate sixteen = TODAY.minusDays(16);
        LocalDate thirteen = TODAY.minusDays(13);
        LocalDate eleven = TODAY.minusDays(11);
        assertEquals(List.of(
                new ReconfirmationListView.Item("01JLISTINGZ000000000000001", "TD-7K3M-Q9TX", ListingState.UNPUBLISHED,
                        sixteen, sixteen.plusDays(10), sixteen.plusDays(14), true, true),
                new ReconfirmationListView.Item("01JLISTINGA000000000000001", "TD-7K3M-Q9TX", ListingState.UNPUBLISHED,
                        thirteen, thirteen.plusDays(10), thirteen.plusDays(14), false, true),
                new ReconfirmationListView.Item("01JLISTINGB000000000000001", "TD-7K3M-Q9TX", ListingState.UNPUBLISHED,
                        thirteen, thirteen.plusDays(10), thirteen.plusDays(14), false, false),
                new ReconfirmationListView.Item("01JLISTINGC000000000000001", "TD-7K3M-Q9TX", ListingState.UNPUBLISHED,
                        eleven, eleven.plusDays(10), eleven.plusDays(14), false, true)), list.items());
    }

    private static List<String> ids(ReconfirmationListView list) {
        return list.items().stream().map(ReconfirmationListView.Item::listingId).toList();
    }

    /** A candidate row, and its agreement's standing naming the assignee. */
    private void candidate(String listingId, String agreementId, LocalDate confirmedOn, boolean latestValid,
                           String assignee, boolean ended) {
        listings.putCandidate(new ReconfirmationCandidate(listingId, "TD-7K3M-Q9TX", agreementId,
                ListingState.UNPUBLISHED, new FreshnessSummary(confirmedOn, latestValid)));
        standing(agreementId, ended, assignee);
    }

    private void stored() {
        listings.put(Listing.load(LISTING, RevisionFixtures.CODE, UNIT, TransactionType.LONG_TERM_RENT, AGREEMENT,
                ListingState.UNPUBLISHED, SALE, RevisionFixtures.CREATED, RevisionFixtures.CREATED, 3L,
                List.of(draft(1))));
    }

    private void standing(String agreementId, boolean ended, String assignee) {
        stubRights.standing(new AgreementStandingFacts(agreementId, UNIT, "LONG_TERM_RENT", ended, assignee));
    }

    /** The class of the exception a call throws, or null when it throws none. */
    private static Class<?> thrown(Runnable call) {
        try {
            call.run();
            return null;
        } catch (RuntimeException e) {
            return e.getClass();
        }
    }

    /** E05's port around the stub, counting the standings calls and the number of agreement ids each carried. */
    private static final class CountingRightPort implements AdvertisingRightPort {

        private final AdvertisingRightPort delegate;
        private final List<Integer> standingsSizes = new ArrayList<>();

        private CountingRightPort(AdvertisingRightPort delegate) {
            this.delegate = delegate;
        }

        int standingsCalls() {
            return standingsSizes.size();
        }

        List<Integer> standingsSizes() {
            return List.copyOf(standingsSizes);
        }

        @Override
        public List<AgreementStandingFacts> standings(Collection<String> agreementIds) {
            standingsSizes.add(agreementIds.size());
            return delegate.standings(agreementIds);
        }

        @Override
        public Optional<AdvertisingAnswerFacts> answer(String unitId, String agreementId) {
            return delegate.answer(unitId, agreementId);
        }

        @Override
        public Optional<AdvertisingAnswerFacts> answerUnderShareLock(String unitId, String agreementId) {
            return delegate.answerUnderShareLock(unitId, agreementId);
        }

        @Override
        public Optional<AgreementTermsFacts> terms(String unitId, String agreementId) {
            return delegate.terms(unitId, agreementId);
        }
    }
}
