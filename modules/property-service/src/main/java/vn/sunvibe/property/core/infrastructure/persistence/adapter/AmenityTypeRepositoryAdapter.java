package vn.sunvibe.property.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.property.core.domain.model.amenity.AmenityType;
import vn.sunvibe.property.core.domain.repository.AmenityTypeDomainRepository;
import vn.sunvibe.property.core.infrastructure.persistence.repository.AmenityTypeJpaRepository;
import vn.sunvibe.property.core.infrastructure.persistence.mapper.manual.AmenityTypeEntityMapper;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AmenityTypeRepositoryAdapter implements AmenityTypeDomainRepository {

    private final AmenityTypeJpaRepository jpaRepository;
    private final AmenityTypeEntityMapper mapper;

    @Override
    public Optional<AmenityType> findById(Integer id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<AmenityType> findAll() {
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .toList();
    }
}