package vn.sunvibe.property.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.property.core.domain.model.rule.RuleCategory;
import vn.sunvibe.property.core.domain.repository.RuleCategoryDomainRepository;
import vn.sunvibe.property.core.infrastructure.persistence.mapper.manual.RuleCategoryEntityMapper;
import vn.sunvibe.property.core.infrastructure.persistence.repository.RuleCategoryJpaRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RuleCategoryDomainRepositoryAdapter implements RuleCategoryDomainRepository {

    private final RuleCategoryJpaRepository jpaRepository;
    private final RuleCategoryEntityMapper mapper;

    @Override
    public Optional<RuleCategory> findById(Integer id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<RuleCategory> findAll() {
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .toList();
    }
}