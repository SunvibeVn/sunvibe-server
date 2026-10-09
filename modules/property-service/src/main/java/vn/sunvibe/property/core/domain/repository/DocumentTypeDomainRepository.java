package vn.sunvibe.property.core.domain.repository;

import vn.sunvibe.property.core.domain.model.document.DocumentType;

import java.util.List;
import java.util.Optional;

public interface DocumentTypeDomainRepository {

    Optional<DocumentType> findById(Integer id);

    List<DocumentType> findAll();
}