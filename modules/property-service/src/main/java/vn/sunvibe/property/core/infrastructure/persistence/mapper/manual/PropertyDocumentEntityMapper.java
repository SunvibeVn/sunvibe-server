package vn.sunvibe.property.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.domain.model.document.PropertyDocument;
import vn.sunvibe.property.core.domain.model.vo.DocumentTypeRef;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;
import vn.sunvibe.property.core.infrastructure.persistence.entity.PropertyDocumentEntity;

@Component
public class PropertyDocumentEntityMapper {

    public PropertyDocument toDomain(PropertyDocumentEntity entity) {
        if (entity == null) return null;

        // The document row keeps the type's name; it is rebuilt as a reference, not as master data
        return PropertyDocument.load(
                entity.getId(),
                PropertyId.of(entity.getPropertyId()),
                DocumentTypeRef.of(entity.getDocumentTypeId(), entity.getDocumentType()),
                entity.getFilePath(),
                entity.getFileKey(),
                entity.getIssueDate(),
                entity.getExpiryDate(),
                entity.getIssuer(),
                entity.getNotes(),
                entity.getUploadedAt(),
                entity.getUploadedBy()
        );
    }

    public PropertyDocumentEntity toEntity(PropertyDocument domain) {
        if (domain == null) return null;

        DocumentTypeRef documentType = domain.getDocumentType();
        return PropertyDocumentEntity.builder()
                .id(domain.getId())
                .propertyId(domain.getPropertyId().getValue())
                .documentTypeId(domain.getDocumentTypeId())
                .documentName(documentType.getName())
                .documentType(documentType.getName())
                .filePath(domain.getFilePath())
                .fileKey(domain.getFileKey())
                .issueDate(domain.getIssueDate())
                .expiryDate(domain.getExpiryDate())
                .issuer(domain.getIssuer())
                .notes(domain.getNotes())
                .uploadedAt(domain.getUploadedAt())
                .uploadedBy(domain.getUploadedBy())
                .build();
    }
}