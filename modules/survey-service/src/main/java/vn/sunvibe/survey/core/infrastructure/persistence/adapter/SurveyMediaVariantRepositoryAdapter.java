package vn.sunvibe.survey.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.survey.core.domain.model.media.MediaVariant;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaVariantDomainRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyMediaVariantEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.mapper.manual.SurveyMediaEntityMapper;
import vn.sunvibe.survey.core.infrastructure.persistence.repository.SurveyMediaVariantJpaRepository;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Adapter - insert-only photo variants (survey-media spec 5.2, I-3). */
@Service
@RequiredArgsConstructor
public class SurveyMediaVariantRepositoryAdapter implements SurveyMediaVariantDomainRepository {

    private final SurveyMediaVariantJpaRepository jpaRepository;
    private final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc;

    @Override
    public java.util.Set<String> findStoredKeys(Collection<String> keys) {
        if (keys.isEmpty()) {
            return java.util.Set.of();
        }
        return new java.util.HashSet<>(jdbc.queryForList(MediaSql.STORED_VARIANT_KEYS,
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("keys", keys), String.class));
    }

    @Override
    public void saveAll(SurveyMediaId mediaId, List<MediaVariant> variants) {
        jpaRepository.saveAllAndFlush(variants.stream()
                .map(variant -> SurveyMediaEntityMapper.toEntity(mediaId, variant)).toList());
    }

    @Override
    public Map<SurveyMediaId, List<MediaVariant>> findByMediaIds(Collection<SurveyMediaId> mediaIds) {
        if (mediaIds.isEmpty()) {
            return Map.of();
        }
        return jpaRepository.findByIdMediaIdIn(mediaIds.stream().map(SurveyMediaId::getValue).toList()).stream()
                .sorted(Comparator.comparing((SurveyMediaVariantEntity row) -> row.getId().getVariant()))
                .collect(Collectors.groupingBy(row -> SurveyMediaId.of(row.getId().getMediaId()), LinkedHashMap::new,
                        Collectors.mapping(SurveyMediaEntityMapper::toDomain, Collectors.toList())));
    }
}
