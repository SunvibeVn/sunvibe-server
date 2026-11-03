package vn.sunvibe.survey.core.testsupport;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import vn.sunvibe.survey.core.domain.model.media.ListingRun;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaListingRunDomainRepository;

/** No full-listing run (survey-media spec 4.4 step 4): nothing blocks a resume unless a test says so. */
public final class NoListingRuns implements SurveyMediaListingRunDomainRepository {

    public boolean blocks;

    @Override
    public Optional<ListingRun> leaseOpen(String token, Instant leaseUntil, Instant now) {
        return Optional.empty();
    }

    @Override
    public Optional<ListingRun> startIfDue(String token, Instant leaseUntil, Instant dueBefore, Instant now) {
        return Optional.empty();
    }

    @Override
    public boolean progress(long id, String token, String startAfterKey, int keysSeen, int undeleted,
                            Instant leaseUntil) {
        return false;
    }

    @Override
    public boolean finish(long id, String token, Instant now) {
        return false;
    }

    @Override
    public List<Long> abandonStartedBefore(Instant cutoff, Instant now) {
        return List.of();
    }

    @Override
    public boolean blocksResume() {
        return blocks;
    }
}
