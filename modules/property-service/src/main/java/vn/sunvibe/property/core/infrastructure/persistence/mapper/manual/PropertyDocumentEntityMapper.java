package vn.sunvibe.property.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.domain.model.document.DocumentType;
import vn.sunvibe.property.core.domain.model.document.PropertyDocument;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;
import vn.sunvibe.property.core.infrastructure.persistence.entity.PropertyDocumentEntity;

@Component
public class PropertyDocumentEntityMapper {

    public PropertyDocument toDomain(PropertyDocumentEntity entity) {
        if (entity == null) return null;

        // Reconstitution path: use no-args + setters to bypass constructor validation.
        DocumentType documentType = new DocumentType();
        documentType.setId(entity.getDocumentTypeId());
        documentType.setTypeName(entity.getDocumentType());

        PropertyDocument domain = new PropertyDocument();
        domain.setId(entity.getId());
        domain.setPropertyId(PropertyId.of(entity.getPropertyId()));
        domain.setDocumentTypeId(entity.getDocumentTypeId());
        domain.setDocumentType(documentType);
        domain.setFilePath(entity.getFilePath());
        domain.setFileKey(entity.getFileKey());
        domain.setIssueDate(entity.getIssueDate());
        domain.setExpiryDate(entity.getExpiryDate());
        domain.setIssuer(entity.getIssuer());
        domain.setNotes(entity.getNotes());
        domain.setUploadedAt(entity.getUploadedAt());
        domain.setUploadedBy(entity.getUploadedBy());
        return domain;
    }

    public PropertyDocumentEntity toEntity(PropertyDocument domain) {
        if (domain == null) return null;

        DocumentType documentType = domain.getDocumentType();
        return PropertyDocumentEntity.builder()
                .id(domain.getId())
                .propertyId(domain.getPropertyId().getValue())
                .documentTypeId(domain.getDocumentTypeId())
                .documentName(documentType != null ? documentType.getTypeName() : null)
                .documentType(documentType != null ? documentType.getTypeName() : null)
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