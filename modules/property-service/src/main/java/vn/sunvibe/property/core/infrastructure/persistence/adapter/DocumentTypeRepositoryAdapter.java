package vn.sunvibe.property.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.property.core.domain.model.document.DocumentType;
import vn.sunvibe.property.core.domain.repository.DocumentTypeDomainRepository;
import vn.sunvibe.property.core.infrastructure.persistence.repository.DocumentTypeJpaRepository;
import vn.sunvibe.property.core.infrastructure.persistence.mapper.manual.DocumentTypeEntityMapper;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DocumentTypeRepositoryAdapter implements DocumentTypeDomainRepository {

    private final DocumentTypeJpaRepository jpaRepository;
    private final DocumentTypeEntityMapper mapper;

    @Override
    public Optional<DocumentType> findById(Integer id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<DocumentType> findAll() {
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .toList();
    }
}