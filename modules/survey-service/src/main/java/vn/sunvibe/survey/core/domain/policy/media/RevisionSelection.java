package vn.sunvibe.survey.core.domain.policy.media;

import vn.sunvibe.survey.core.domain.model.media.MediaSelection;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a revision holds (survey-checklist submission spec 4.6, A-12): its own photo rows and the carried photos it still
 * selects. Caps, coverage, the duplicate check and the gates all count this list.
 */
public final class RevisionSelection {

    private RevisionSelection() {
    }

    /** The carried photos the revision selects now. */
    public static List<SurveyMediaId> selectedIds(Collection<MediaSelection> selections) {
        return selections.stream().filter(MediaSelection::isSelected).map(MediaSelection::mediaId).toList();
    }

    /** The revision's own rows, then the carried photos it selects. */
    public static List<SurveyMedia> photos(Collection<SurveyMedia> own, Collection<SurveyMedia> selectedCarried) {
        List<SurveyMedia> photos = new ArrayList<>(own);
        photos.addAll(selectedCarried);
        return List.copyOf(photos);
    }

    /**
     * The number of the revision each selected photo came from, by photo id.
     *
     * @param checklistNumbers the numbers of the lead's revisions, by revision id
     */
    public static Map<String, Integer> carriedFromNumbers(Collection<MediaSelection> selections,
                                                          Map<String, Integer> checklistNumbers) {
        Map<String, Integer> numbers = new HashMap<>();
        for (MediaSelection selection : selections) {
            if (selection.isSelected()) {
                numbers.put(selection.mediaId().getValue(), checklistNumbers.get(selection.carriedFromChecklistId()));
            }
        }
        return numbers;
    }
}
