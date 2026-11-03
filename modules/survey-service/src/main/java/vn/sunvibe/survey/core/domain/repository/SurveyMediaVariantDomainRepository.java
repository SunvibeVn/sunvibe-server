package vn.sunvibe.survey.core.domain.repository;

import vn.sunvibe.survey.core.domain.model.media.MediaVariant;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** The variants of READY photos: insert-only (survey-media spec 5.2, I-3). */
public interface SurveyMediaVariantDomainRepository {

    /** Insert the variants of one photo's outcome. */
    void saveAll(SurveyMediaId mediaId, List<MediaVariant> variants);

    /** spec 4.4 step 4: the keys among these that a stored variant row references (the rest are orphans). */
    java.util.Set<String> findStoredKeys(Collection<String> keys);

    /** The variants of the photos, in kind order, by photo. */
    Map<SurveyMediaId, List<MediaVariant>> findByMediaIds(Collection<SurveyMediaId> mediaIds);
}
