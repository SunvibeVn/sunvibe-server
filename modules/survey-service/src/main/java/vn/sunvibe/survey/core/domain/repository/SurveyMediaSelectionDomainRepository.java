package vn.sunvibe.survey.core.domain.repository;

import vn.sunvibe.survey.core.domain.model.media.MediaSelection;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Carried photos of later revisions (survey-checklist submission spec 4.6, 5.3, A-12). A revision's own photos have no
 * row here. Selections of a revision change only while it is a DRAFT (I-2).
 */
public interface SurveyMediaSelectionDomainRepository {

    void insertAll(List<MediaSelection> selections);

    /** Mark a deselection. */
    void update(MediaSelection selection);

    /** Every selection of the revision, deselected ones included. */
    List<MediaSelection> findByChecklistId(String checklistId);

    Optional<MediaSelection> findOne(String checklistId, SurveyMediaId mediaId);

    /**
     * The revision the photo was carried from into this one, by a scalar query (submission spec 5.5): it loads no row,
     * so {@link #findOne} after the locks still reads what they protect.
     */
    Optional<String> findCarriedFrom(String checklistId, SurveyMediaId mediaId);

    /** The revisions that select the photo now (not its origin), by id, for the set locks of spec 5.4. */
    List<String> findSelectingChecklistIds(SurveyMediaId mediaId);

    /** Every selection of these revisions, deselected ones included. */
    List<MediaSelection> findByChecklistIds(Collection<String> checklistIds);
}
