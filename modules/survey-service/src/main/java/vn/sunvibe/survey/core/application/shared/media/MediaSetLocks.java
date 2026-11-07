package vn.sunvibe.survey.core.application.shared.media;

import org.springframework.stereotype.Component;
import vn.sunvibe.survey.core.domain.model.media.MediaSet;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaSelectionDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaSetDomainRepository;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The set rows a photo writer takes before the photo (survey-checklist submission spec 4.6, 5.4): those of the photo's
 * origin revision and of every revision that selects it, in id order, because a change of the photo shows in all of
 * them. A revision started while this waits has the highest id, so taking it last keeps the order.
 */
@Component
public class MediaSetLocks {

    private final SurveyMediaSetDomainRepository sets;
    private final SurveyMediaSelectionDomainRepository selections;

    public MediaSetLocks(SurveyMediaSetDomainRepository sets, SurveyMediaSelectionDomainRepository selections) {
        this.sets = sets;
        this.selections = selections;
    }

    /** The locked set rows of one photo: its origin's and those of the revisions that select it. */
    public record LockedSets(MediaSet origin, List<MediaSet> all) {
    }

    /** Empty when the origin revision has no set row. */
    public Optional<LockedSets> lockFor(String originChecklistId, SurveyMediaId mediaId) {
        Map<String, MediaSet> locked = new TreeMap<>();
        Set<String> tried = new HashSet<>();
        Set<String> wanted = new TreeSet<>(selections.findSelectingChecklistIds(mediaId));
        wanted.add(originChecklistId);
        while (!wanted.isEmpty()) {
            for (String checklistId : wanted) {
                tried.add(checklistId);
                sets.lockForUpdate(checklistId).ifPresent(set -> locked.put(checklistId, set));
            }
            wanted = new TreeSet<>(selections.findSelectingChecklistIds(mediaId));
            wanted.removeAll(tried);
        }
        MediaSet origin = locked.get(originChecklistId);
        return origin == null ? Optional.empty() : Optional.of(new LockedSets(origin, List.copyOf(locked.values())));
    }

    /** spec 4.6: a change of the photo bumps every set that shows it. */
    public void bumpAll(LockedSets locked, Instant now) {
        for (MediaSet set : locked.all()) {
            set.bump(now);
            sets.update(set);
        }
    }
}
