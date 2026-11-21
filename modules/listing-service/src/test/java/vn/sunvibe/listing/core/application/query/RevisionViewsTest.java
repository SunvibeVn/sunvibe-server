package vn.sunvibe.listing.core.application.query;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.domain.model.listing.RevisionContent;
import vn.sunvibe.listing.core.domain.model.listing.RevisionFeatures;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The two revision reads carry unpublished text and E05's terms (E06-S2 spec E06S2-D21): their text names none. */
class RevisionViewsTest {

    @Test
    void toString_revisionAndSourcesViews_hidden() {
        ListingRevisionView revision = new ListingRevisionView("01JLISTING0000000000000001", 1, RevisionStatus.DRAFT,
                RevisionFixtures.CREATED, RevisionFixtures.NOW,
                new RevisionContent("secret title", "secret description", RevisionFeatures.NONE_CHOSEN),
                RevisionFixtures.inEffectTerms(), RevisionFixtures.unitSnapshot(), null, 7L, List.of());
        TermsSourcesView sources = new TermsSourcesView(false, RevisionFixtures.inEffectTerms(), null);

        assertEquals("ListingRevisionView[hidden]", revision.toString());
        assertEquals("TermsSourcesView[hidden]", sources.toString());
        for (String value : List.of("secret", "8000000", "01JLISTING")) {
            assertFalse((revision + " " + sources).contains(value), value);
        }
    }
}
