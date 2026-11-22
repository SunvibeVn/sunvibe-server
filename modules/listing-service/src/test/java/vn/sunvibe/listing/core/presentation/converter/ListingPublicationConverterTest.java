package vn.sunvibe.listing.core.presentation.converter;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.application.command.PutLiveCommand;
import vn.sunvibe.listing.core.application.query.ListingPublicationView;
import vn.sunvibe.listing.core.application.query.LiveDifferenceView;
import vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup;
import vn.sunvibe.listing.core.domain.model.listing.ComparedPart;
import vn.sunvibe.listing.core.domain.model.listing.DisplayReason;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;
import vn.sunvibe.listing.core.domain.model.listing.TermsSource;
import vn.sunvibe.listing.core.presentation.dto.request.ListingRevisionCommandRequest;
import vn.sunvibe.listing.core.presentation.dto.response.ListingDisplayAnswerResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingFeaturesResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingLiveDifferenceResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingLiveRevisionResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingPublicationApprovedResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingPublicationLiveResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingPublicationResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingTermsSnapshotResponse;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The publication's request and response converters (listing-publish E06-S6 spec sections 4.1 to 4.3): the path and the
 * body as the put live command; the views' values as codes, the existing schemas reused for the features and the terms
 * snapshot, every nullable part null where the view has none; the difference prints no content.
 */
class ListingPublicationConverterTest {

    private static final String LISTING = "01JFQ9P0000000000000000001";
    private static final Instant WENT_LIVE = Instant.parse("2026-11-21T03:00:00.000001Z");

    private final ListingRequestConverter requests = new ListingRequestConverter();
    private final ListingResponseConverter responses = new ListingResponseConverter();

    @Test
    void toResponse_publication_everyField() {
        ListingPublicationView view = new ListingPublicationView(LISTING, ListingState.PUBLISHED, 9L,
                new ListingPublicationView.Live(1, WENT_LIVE, new ListingPublicationView.DisplayAnswer(true, List.of(),
                        List.of())),
                new ListingPublicationView.Approved(2, TermsSource.PENDING_START,
                        new ListingPublicationView.DisplayAnswer(false, List.of(DisplayReason.ADVERTISING_RIGHT,
                                DisplayReason.FRESHNESS), List.of(AgreementReasonGroup.SUSPENDED,
                                AgreementReasonGroup.OWNER))),
                true, false);
        ListingPublicationView archived = new ListingPublicationView(LISTING, ListingState.ARCHIVED, 9L, null, null,
                false, false);

        assertAll(
                () -> assertEquals(new ListingPublicationResponse(LISTING, "PUBLISHED", 9L,
                        new ListingPublicationLiveResponse(1, WENT_LIVE,
                                new ListingDisplayAnswerResponse(true, List.of(), List.of())),
                        new ListingPublicationApprovedResponse(2, "PENDING_START",
                                new ListingDisplayAnswerResponse(false, List.of("ADVERTISING_RIGHT", "FRESHNESS"),
                                        List.of("SUSPENDED", "OWNER"))),
                        true, false), responses.toPublication(view)),
                () -> assertEquals(new ListingPublicationResponse(LISTING, "ARCHIVED", 9L, null, null, false, false),
                        responses.toPublication(archived)));
    }

    @Test
    void toResponse_liveDifference_everyField() {
        RevisionTerms terms = RevisionFixtures.inEffectTerms();
        LiveDifferenceView compared = new LiveDifferenceView(3, LiveDifferenceView.Comparison.COMPARED,
                new LiveDifferenceView.LiveRevision(1, WENT_LIVE, RevisionFixtures.completeContent(), terms),
                List.of(ComparedPart.TITLE, ComparedPart.TERMS));
        LiveDifferenceView noLive = new LiveDifferenceView(3, LiveDifferenceView.Comparison.NO_LIVE_REVISION, null,
                List.of());

        ListingLiveDifferenceResponse response = responses.toLiveDifference(compared);

        assertAll(
                () -> assertEquals(new ListingLiveDifferenceResponse(3, "COMPARED",
                        new ListingLiveRevisionResponse(1, WENT_LIVE, RevisionFixtures.TITLE,
                                RevisionFixtures.DESCRIPTION,
                                new ListingFeaturesResponse("FULL", true, true, true, "PRIVATE", "SHARED", true, true,
                                        true, true, true, "NOT_ALLOWED", true),
                                new ListingTermsSnapshotResponse("IN_EFFECT", null, terms.capturedAt(),
                                        responses.toTerms(terms))),
                        List.of("TITLE", "TERMS")), response),
                () -> assertEquals(new ListingLiveDifferenceResponse(3, "NO_LIVE_REVISION", null, List.of()),
                        responses.toLiveDifference(noLive)),
                // unpublished text and E05's terms: the text names neither
                () -> assertFalse(response.toString().contains(RevisionFixtures.TITLE), response.toString()),
                () -> assertFalse(response.live().toString().contains("8000000"), response.live().toString()));
    }

    @Test
    void toPutLiveCommand_pathAndBody() {
        assertAll(
                () -> assertEquals(new PutLiveCommand(LISTING, 2, 5L),
                        requests.toPutLiveCommand(LISTING, 2, new ListingRevisionCommandRequest(5L))),
                () -> assertEquals(new PutLiveCommand(LISTING, null, 5L),
                        requests.toPutLiveCommand(LISTING, null, new ListingRevisionCommandRequest(5L))));
    }
}
