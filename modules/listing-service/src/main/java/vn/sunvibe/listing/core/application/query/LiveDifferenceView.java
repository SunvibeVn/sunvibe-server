package vn.sunvibe.listing.core.application.query;

import vn.sunvibe.listing.core.domain.model.listing.ComparedPart;
import vn.sunvibe.listing.core.domain.model.listing.RevisionContent;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;

import java.time.Instant;
import java.util.List;

/**
 * The difference of a revision from the live revision (listing-publish US-4 AC-2; E06-S6 spec E06S6-D12): which of the
 * three cases holds, the live revision's content and terms when another revision is live, and the parts of the asked
 * revision that differ from it by value. Its text never holds the content or the terms.
 *
 * @param liveOrNull   the live revision, only when it is another revision ({@code COMPARED})
 * @param changedParts the parts that differ, in the order of {@link ComparedPart}; empty unless {@code COMPARED}
 */
public record LiveDifferenceView(int revisionNo, Comparison comparison, LiveRevision liveOrNull,
                                 List<ComparedPart> changedParts) {

    public LiveDifferenceView {
        changedParts = List.copyOf(changedParts);
    }

    /** Which case the answer is. */
    public enum Comparison {
        /** Another revision is live: compared with it. */
        COMPARED,
        /** No revision is live. */
        NO_LIVE_REVISION,
        /** The asked revision is the live one. */
        IS_LIVE_REVISION
    }

    /** The live revision: it was submitted, approved and put live, so it holds a title, a description and terms. */
    public record LiveRevision(int revisionNo, Instant wentLiveAt, RevisionContent content, RevisionTerms terms) {

        @Override
        public String toString() {
            return "LiveRevision[" + revisionNo + "]";
        }
    }

    @Override
    public String toString() {
        return "LiveDifferenceView[" + revisionNo + ", " + comparison + ", " + changedParts + "]";
    }
}
