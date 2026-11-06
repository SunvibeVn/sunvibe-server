package vn.sunvibe.survey.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import vn.sunvibe.survey.core.domain.exception.MediaDuplicateContentException;
import vn.sunvibe.survey.core.domain.exception.MediaUploadKeyTakenException;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistPerson;
import vn.sunvibe.survey.core.domain.model.media.MediaStatus;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.model.media.PlacedMedia;
import vn.sunvibe.survey.core.domain.model.media.MediaRef;
import vn.sunvibe.survey.core.domain.model.media.MediaVariant;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaVariantDomainRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyMediaEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.mapper.manual.SurveyMediaEntityMapper;
import vn.sunvibe.survey.core.infrastructure.persistence.repository.SurveyMediaJpaRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.support.MediaSql;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Adapter - survey photo rows on JPA, the sweep finders on plain SQL (survey-media spec 5.2, 5.4). The A-16 (a)
 * backstop becomes {@link MediaDuplicateContentException} and the upload-key unique key
 * {@link MediaUploadKeyTakenException}; conditional updates bump the version and clear the persistence context.
 */
@Service
@RequiredArgsConstructor
public class SurveyMediaRepositoryAdapter implements SurveyMediaDomainRepository {

    static final Set<String> DUPLICATE_CONTENT = Set.of("uq_survey_media_selected_sha256");
    static final Set<String> UPLOAD_KEY = Set.of("uq_survey_media_checklist_uploader_key");

    private final SurveyMediaJpaRepository jpaRepository;
    private final SurveyMediaVariantDomainRepository variants;
    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public void insert(SurveyMedia media) {
        if (media.getVersion() != null) {
            throw new IllegalArgumentException("Only a new photo is inserted.");
        }
        try {
            media.markPersisted(jpaRepository.saveAndFlush(SurveyMediaEntityMapper.toEntity(media)).getVersion());
        } catch (DataIntegrityViolationException e) {
            if (ConstraintViolations.violatesAny(e, DUPLICATE_CONTENT)) {
                throw new MediaDuplicateContentException(e);
            }
            if (ConstraintViolations.violatesAny(e, UPLOAD_KEY)) {
                throw new MediaUploadKeyTakenException(e);
            }
            throw e;
        }
    }

    @Override
    public void update(SurveyMedia media) {
        if (media.getVersion() == null) {
            throw new IllegalArgumentException("A new photo is inserted, not updated.");
        }
        try {
            media.markPersisted(jpaRepository.saveAndFlush(SurveyMediaEntityMapper.toEntity(media)).getVersion());
        } catch (DataIntegrityViolationException e) {
            if (ConstraintViolations.violatesAny(e, DUPLICATE_CONTENT)) {
                throw new MediaDuplicateContentException(e);
            }
            throw e;
        }
    }

    @Override
    public List<SurveyMedia> lockForUpdate(Collection<SurveyMediaId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return withVariants(jpaRepository.findForUpdateByIdIn(ids.stream().map(SurveyMediaId::getValue).sorted()
                .toList()));
    }

    @Override
    public List<SurveyMedia> findForChecklist(String checklistId) {
        return withVariants(jpaRepository.findByChecklistIdOrderByMediaNoAsc(checklistId));
    }

    @Override
    public List<ChecklistPerson> findUploaders(Collection<String> checklistIds) {
        return checklistIds.isEmpty() ? List.of() : jpaRepository.findUploaders(checklistIds);
    }

    @Override
    public List<ChecklistPerson> findRemovers(Collection<String> checklistIds) {
        return checklistIds.isEmpty() ? List.of() : jpaRepository.findRemovers(checklistIds);
    }

    @Override
    public List<SurveyMedia> findByIds(Collection<SurveyMediaId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return withVariants(jpaRepository.findByIdInOrderByIdAsc(ids.stream().map(SurveyMediaId::getValue).toList()));
    }

    @Override
    public Optional<SurveyMedia> findById(SurveyMediaId id) {
        return jpaRepository.findById(id.getValue()).map(entity -> withVariants(List.of(entity)).get(0));
    }

    @Override
    public int deleteExpiredRecords(String checklistId, Instant finishedAtOrBefore, int limit) {
        return jdbc.update(MediaSql.DELETE_EXPIRED_RECORDS, new org.springframework.jdbc.core.namedparam
                .MapSqlParameterSource("checklistId", checklistId).addValue("cutoff",
                finishedAtOrBefore.atOffset(java.time.ZoneOffset.UTC)).addValue("limit", limit));
    }

    @Override
    public List<MediaRef> findReadyWithoutContentMissing(String afterIdOrNull, int limit) {
        return jdbc.query("SELECT id, checklist_id FROM survey_media WHERE status = 'READY' AND content_missing_at IS"
                + " NULL AND content_deleted_at IS NULL AND id > :after ORDER BY id LIMIT :limit",
                new MapSqlParameterSource("after", afterIdOrNull == null ? "" : afterIdOrNull).addValue("limit", limit),
                (row, n) -> new MediaRef(SurveyMediaId.of(row.getString("id")), row.getString("checklist_id")));
    }

    @Override
    public java.util.Set<String> findQuarantineKeysInUse() {
        return new java.util.HashSet<>(jdbc.queryForList(MediaSql.QUARANTINE_KEYS_IN_USE,
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource(), String.class));
    }

    @Override
    public List<PlacedMedia> findSelectedPlacements(String checklistId) {
        return jpaRepository.findPlacementsOfChecklist(checklistId, List.of(MediaStatus.AWAITING_CONTENT,
                MediaStatus.RECEIVING, MediaStatus.PROCESSING, MediaStatus.READY)).stream()
                .map(row -> new PlacedMedia(SurveyMediaId.of((String) row[0]), (String) row[1],
                        (EvidenceCategory) row[2])).toList();
    }

    @Override
    public List<SurveyMediaId> findOpenUploadIds(String checklistId, String uploaderUserId) {
        return jpaRepository.findIdsOfUploader(checklistId, uploaderUserId, List.of(MediaStatus.AWAITING_CONTENT,
                MediaStatus.RECEIVING)).stream().map(SurveyMediaId::of).toList();
    }

    @Override
    public Optional<String> findChecklistIdById(SurveyMediaId id) {
        return jpaRepository.findChecklistIdById(id.getValue());
    }

    @Override
    public Optional<SurveyMedia> findByUploadKey(String checklistId, String uploadedByUserId, String uploadKey) {
        return jpaRepository.findByChecklistIdAndUploadedByUserIdAndUploadKey(checklistId, uploadedByUserId,
                uploadKey).map(entity -> withVariants(List.of(entity)).get(0));
    }

    @Override
    public List<SurveyMedia> findCommittedHashOnOtherVisits(String leadId, String checklistId, String sha256) {
        return withVariants(jpaRepository.findCommittedHashOnOtherVisits(leadId, checklistId, sha256));
    }

    @Override
    public boolean claimIfAwaiting(SurveyMediaId id, String token, String attemptKey, Instant now,
                                   Instant createdAfter) {
        return jpaRepository.claimIfAwaiting(id.getValue(), token, attemptKey, now, createdAfter) == 1;
    }

    @Override
    public boolean releaseClaimIfToken(SurveyMediaId id, String token) {
        return jpaRepository.releaseClaimIfToken(id.getValue(), token) == 1;
    }

    @Override
    public int expireStale(String checklistId, Instant createdAtOrBefore, Instant now) {
        return jpaRepository.expireStale(checklistId, createdAtOrBefore, now);
    }

    @Override
    public List<MediaRef> findAwaitingCreatedBefore(Instant cutoff, int limit) {
        return refs(MediaSql.FIND_AWAITING_CREATED_BEFORE, cutoff, limit);
    }

    @Override
    public List<MediaRef> findReceivingStartedBefore(Instant cutoff, int limit) {
        return refs(MediaSql.FIND_RECEIVING_STARTED_BEFORE, cutoff, limit);
    }

    @Override
    public List<MediaRef> findProcessingReceivedBefore(Instant cutoff, int limit) {
        return refs(MediaSql.FIND_PROCESSING_RECEIVED_BEFORE, cutoff, limit);
    }

    @Override
    public List<MediaRef> findSourcesNotDeletedBefore(Instant cutoff, int limit) {
        return refs(MediaSql.FIND_SOURCES_NOT_DELETED_BEFORE, cutoff, limit);
    }

    @Override
    public List<MediaRef> findDeletionsRequestedBefore(Instant cutoff, int limit) {
        return refs(MediaSql.FIND_DELETIONS_REQUESTED_BEFORE, cutoff, limit);
    }

    @Override
    public List<MediaRef> findExpiredFinishedBefore(Instant cutoff, int limit) {
        return refs(MediaSql.FIND_EXPIRED_FINISHED_BEFORE, cutoff, limit);
    }

    private List<MediaRef> refs(String sql, Instant cutoff, int limit) {
        return jdbc.query(sql, new MapSqlParameterSource("cutoff", OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC))
                .addValue("limit", limit), (row, n) -> new MediaRef(SurveyMediaId.of(row.getString("id")),
                row.getString("checklist_id")));
    }

    private List<SurveyMedia> withVariants(List<SurveyMediaEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        Map<SurveyMediaId, List<MediaVariant>> byMedia = variants.findByMediaIds(entities.stream()
                .map(entity -> SurveyMediaId.of(entity.getId())).toList());
        return entities.stream().map(entity -> SurveyMediaEntityMapper.toDomain(entity,
                byMedia.getOrDefault(SurveyMediaId.of(entity.getId()), List.of()))).toList();
    }
}
