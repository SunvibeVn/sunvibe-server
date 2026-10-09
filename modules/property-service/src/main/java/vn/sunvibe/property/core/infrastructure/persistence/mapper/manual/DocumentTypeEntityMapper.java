package vn.sunvibe.property.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.domain.model.document.DocumentType;
import vn.sunvibe.property.core.infrastructure.persistence.entity.DocumentTypeEntity;

@Component
public class DocumentTypeEntityMapper {

    public DocumentType toDomain(DocumentTypeEntity entity) {
        if (entity == null) return null;
        return DocumentType.load(
                entity.getId(),
                entity.getTypeName(),
                entity.getDescription(),
                Boolean.TRUE.equals(entity.getRequiresRenewal()),
                entity.getRenewalFrequencyDays(),
                Boolean.TRUE.equals(entity.getIsArchived()),
                entity.getIcon()
        );
    }

    public DocumentTypeEntity toEntity(DocumentType domain) {
        if (domain == null) return null;
        return DocumentTypeEntity.builder()
                .id(domain.getId())
                .typeName(domain.getTypeName())
                .description(domain.getDescription())
                .requiresRenewal(domain.isRequiresRenewal())
                .renewalFrequencyDays(domain.getRenewalFrequencyDays())
                .isArchived(domain.isArchived())
                .icon(domain.getIcon())
                .build();
    }
}