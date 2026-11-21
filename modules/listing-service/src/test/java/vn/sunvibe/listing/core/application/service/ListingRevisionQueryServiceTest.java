package vn.sunvibe.listing.core.application.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.query.ListingRevisionView;
import vn.sunvibe.listing.core.application.query.TermsSourcesView;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementTermsFacts;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;
import vn.sunvibe.listing.core.domain.model.listing.DepositBasis;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.RevisionContent;
import vn.sunvibe.listing.core.domain.model.listing.RevisionFeatures;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;
import vn.sunvibe.listing.core.domain.model.listing.TermsSource;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;
import vn.sunvibe.listing.core.domain.policy.listing.RevisionSubmitRules;
import vn.sunvibe.listing.core.testsupport.InMemoryListingRepository;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;
import vn.sunvibe.listing.core.testsupport.StubAdvertisingRightPort;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.pending;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.withStatus;

/**
 * The revision read, the terms sources and the open revision number (listing-publish E06-S2 spec 3.7, E06S2-D2,
 * E06S2-D14, E06S2-D20, plan E06S2-P10, E06S2-P15, E06S2-P16; RD-15, RD-3 sources at service level), with fakes.
 */
class ListingRevisionQueryServiceTest {

    private static final String LISTING = "01JLISTING0000000000000001";
    private static final String AGREEMENT = "01AGREEMENT";
    private static final String UNIT = "01UNIT";
    private static final String SALE = "01SALE";
    private static final ListingViewer SALE_VIEWER = new ListingViewer(SALE, Set.of("ROLE_SALE"));
    private static final AgreementTermsFacts.Terms E05_IN_EFFECT = new AgreementTermsFacts.Terms(8_000_000L, "MONTHS",
            2, null, 6, 1, List.of(new AgreementTermsFacts.Fee("Internet", "INCLUDED", null, null)));
    private static final AgreementTermsFacts.Terms E05_PENDING = new AgreementTermsFacts.Terms(9_500_000L, "AMOUNT",
            null, 5_000_000L, 12, 3, List.of());
    private static final LocalDate START = LocalDate.parse("2026-12-01");

    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final StubAdvertisingRightPort rights = new StubAdvertisingRightPort();
    private final ListingQueryService service = new ListingQueryService(listings, rights);

    @Test
    void revision_draft_problemsOfItsSubmission() {
        RevisionContent shortTitle = new RevisionContent("a".repeat(14), null, RevisionFeatures.NONE_CHOSEN);
        stored(ListingRevision.load("R1", 1, RevisionStatus.DRAFT, SALE, RevisionFixtures.CREATED,
                RevisionFixtures.NOW, shortTitle, null, null, null, null));
        standing(false, SALE);

        ListingRevisionView view = service.revision(LISTING, 1, SALE_VIEWER);

        assertEquals(RevisionSubmitRules.violations(shortTitle, null), view.problems());
        assertEquals(3, view.problems().size());
        assertEquals(shortTitle, view.content());
        assertEquals(7L, view.dataVersion());
        assertEquals(RevisionStatus.DRAFT, view.status());
        assertEquals(RevisionFixtures.NOW, view.updatedAt());
    }

    @Test
    void revision_pendingOrCancelled_problemsEmpty() {
        stored(withStatus(1, RevisionStatus.CANCELLED), pending(2));
        standing(false, SALE);

        ListingRevisionView cancelled = service.revision(LISTING, 1, SALE_VIEWER);
        ListingRevisionView pendingView = service.revision(LISTING, 2, SALE_VIEWER);

        assertEquals(List.of(), cancelled.problems());
        assertEquals(RevisionContent.EMPTY, cancelled.content());
        assertEquals(List.of(), pendingView.problems());
        assertEquals(RevisionFixtures.unitSnapshot(), pendingView.unitSnapshotOrNull());
        assertEquals(RevisionFixtures.CREATED, pendingView.submittedAtOrNull());
        assertEquals(RevisionFixtures.inEffectTerms(), pendingView.termsOrNull());
    }

    @Test
    void revision_outOfScopeUnknownOrNull_notFound() {
        stored(withStatus(1, RevisionStatus.CANCELLED));
        standing(false, SALE);

        assertThrows(ListingNotFoundException.class,
                () -> service.revision(LISTING, 1, new ListingViewer("01OTHER", Set.of("ROLE_SALE"))));
        // out of scope: no revision is read
        assertFalse(listings.calls().stream().anyMatch(call -> call.startsWith("findRevision")), listings.calls()
                .toString());
        assertThrows(ListingNotFoundException.class, () -> service.revision(LISTING, 9, SALE_VIEWER));
        assertThrows(ListingNotFoundException.class, () -> service.revision(LISTING, null, SALE_VIEWER));
        assertThrows(ListingNotFoundException.class,
                () -> service.revision("01JLISTING0000000000000009", 1, SALE_VIEWER));
        assertThrows(ListingNotFoundException.class, () -> service.revision("bad", 1, SALE_VIEWER));
        assertFalse(listings.calls().stream().anyMatch(call -> call.contains("bad")));
    }

    @Test
    void revision_managersLeaderAndReviewer_read() {
        stored(pending(1));
        standing(false, SALE);

        for (String role : List.of("ROLE_SALE_LEADER", "ROLE_SALES_MANAGER", "ROLE_ADMIN", "ROLE_LISTING_REVIEWER")) {
            ListingRevisionView view = service.revision(LISTING, 1, new ListingViewer("01OTHER", Set.of(role)));
            assertEquals(RevisionStatus.PENDING_REVIEW, view.status(), role);
        }
        standing(true, SALE);
        assertEquals(1, service.revision(LISTING, 1, SALE_VIEWER).revisionNo());
    }

    @Test
    void termsSources_bothSources_mappedWithStartDate() {
        stored(RevisionFixtures.draft(1));
        standing(false, SALE);
        rights.terms(UNIT, AGREEMENT, new AgreementTermsFacts(false, E05_IN_EFFECT, START, E05_PENDING));

        TermsSourcesView sources = service.termsSources(LISTING, SALE_VIEWER);

        assertFalse(sources.ended());
        RevisionTerms inEffect = sources.inEffectOrNull();
        assertEquals(new RevisionTerms(TermsSource.IN_EFFECT, null, inEffect.capturedAt(), 8_000_000L,
                new RevisionTerms.Deposit(DepositBasis.MONTHS, 2, null), 6, 1, List.of(new RevisionTerms.Fee(
                "Internet", vn.sunvibe.listing.core.domain.model.listing.FeeBasis.INCLUDED, null, null))), inEffect);
        RevisionTerms pendingTerms = sources.pendingOrNull();
        assertEquals(new RevisionTerms(TermsSource.PENDING_START, START, pendingTerms.capturedAt(), 9_500_000L,
                new RevisionTerms.Deposit(DepositBasis.AMOUNT, null, 5_000_000L), 12, 3, List.of()), pendingTerms);
        assertEquals(List.of("findRow " + LISTING), listings.calls());
    }

    @Test
    void termsSources_endedStandingOrAnswer_endedNoSource() {
        stored(RevisionFixtures.draft(1));
        standing(true, SALE);
        rights.terms(UNIT, AGREEMENT, new AgreementTermsFacts(false, E05_IN_EFFECT, START, E05_PENDING));

        assertEquals(new TermsSourcesView(true, null, null), service.termsSources(LISTING, SALE_VIEWER));

        standing(false, SALE);
        rights.terms(UNIT, AGREEMENT, new AgreementTermsFacts(true, E05_IN_EFFECT, null, null));
        assertEquals(new TermsSourcesView(true, null, null), service.termsSources(LISTING, SALE_VIEWER));
    }

    @Test
    void termsSources_noAnswer_noSource() {
        stored(RevisionFixtures.draft(1));
        standing(false, SALE);

        TermsSourcesView sources = service.termsSources(LISTING, SALE_VIEWER);

        assertEquals(new TermsSourcesView(false, null, null), sources);
        assertTrue(rights.calls().contains("terms " + UNIT + " " + AGREEMENT));
    }

    @Test
    void termsSources_notDrafter_notFound() {
        stored(RevisionFixtures.draft(1));
        standing(false, SALE);
        rights.terms(UNIT, AGREEMENT, new AgreementTermsFacts(false, E05_IN_EFFECT, null, null));

        for (ListingViewer viewer : List.of(new ListingViewer("01MANAGER", Set.of("ROLE_SALES_MANAGER")),
                new ListingViewer("01REVIEWER", Set.of("ROLE_LISTING_REVIEWER")),
                new ListingViewer("01OTHER", Set.of("ROLE_SALE")), new ListingViewer(SALE, Set.of("ROLE_ADMIN")))) {
            assertThrows(ListingNotFoundException.class, () -> service.termsSources(LISTING, viewer));
        }
        assertThrows(ListingNotFoundException.class, () -> service.termsSources("bad", SALE_VIEWER));
        assertFalse(rights.calls().stream().anyMatch(call -> call.startsWith("terms")));
    }

    @Test
    void termsSources_brokenContract_illegalState() {
        stored(RevisionFixtures.draft(1));
        standing(false, SALE);
        rights.terms(UNIT, AGREEMENT, new AgreementTermsFacts(false, new AgreementTermsFacts.Terms(0L, "MONTHS", 2, null,
                6, 1, List.of()), null, null));

        assertThrows(IllegalStateException.class, () -> service.termsSources(LISTING, SALE_VIEWER));

        rights.terms(UNIT, AGREEMENT, new AgreementTermsFacts(false, null, null, E05_PENDING));
        assertThrows(IllegalStateException.class, () -> service.termsSources(LISTING, SALE_VIEWER));
    }

    @Test
    void listing_openRevision_numberElseNull() {
        stored(withStatus(1, RevisionStatus.CANCELLED), pending(2));
        standing(false, SALE);

        assertEquals(2, service.listing(LISTING, SALE_VIEWER).openRevisionNoOrNull());

        listings.put(Listing.load("01JLISTING0000000000000002", RevisionFixtures.CODE, UNIT,
                TransactionType.LONG_TERM_RENT, AGREEMENT, ListingState.UNPUBLISHED, SALE, RevisionFixtures.CREATED,
                RevisionFixtures.CREATED, 0L, List.of(withStatus(1, RevisionStatus.CANCELLED))));
        assertNull(service.listing("01JLISTING0000000000000002", SALE_VIEWER).openRevisionNoOrNull());
        assertThrows(ListingNotFoundException.class,
                () -> service.listing(LISTING, new ListingViewer("01OTHER", Set.of("ROLE_SALE"))));
        assertEquals(1, listings.calls().stream().filter(call -> call.equals("findOpenRevisionNo " + LISTING)).count());
    }

    @Test
    void revisionAndListing_transactions_repeatableReadReadOnly() throws NoSuchMethodException {
        for (String name : List.of("revision", "listing")) {
            Method method = method(name);
            Transactional transaction = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);
            assertEquals(Isolation.REPEATABLE_READ, transaction.isolation(), name);
            assertTrue(transaction.readOnly(), name);
        }
        for (String name : List.of("termsSources", "ofAgreement")) {
            assertNull(AnnotatedElementUtils.findMergedAnnotation(method(name), Transactional.class), name);
        }
        assertTrue(AnnotatedElementUtils.findMergedAnnotation(ListingQueryService.class, Transactional.class).readOnly());
    }

    private static Method method(String name) {
        for (Method method : ListingQueryService.class.getDeclaredMethods()) {
            if (method.getName().equals(name) && !method.isSynthetic()) {
                return method;
            }
        }
        throw new AssertionError("no method " + name);
    }

    private void stored(ListingRevision... revisions) {
        listings.put(Listing.load(LISTING, RevisionFixtures.CODE, UNIT, TransactionType.LONG_TERM_RENT, AGREEMENT,
                ListingState.UNPUBLISHED, SALE, RevisionFixtures.CREATED, RevisionFixtures.CREATED, 7L,
                List.of(revisions)));
    }

    private void standing(boolean ended, String assignee) {
        rights.standing(new AgreementStandingFacts(AGREEMENT, UNIT, "LONG_TERM_RENT", ended, assignee));
    }
}
