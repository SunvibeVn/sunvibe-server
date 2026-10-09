package vn.sunvibe.property.core.infrastructure.persistence.entity;


import jakarta.persistence.*;
import lombok.*;
import vn.sunvibe.common.time.AppClock;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA Entity - PropertyDocumentEntity
 * Table: property_documents
 */
@Entity
@Table(name = "property_document", indexes = {
        @Index(name = "idx_property_id_03", columnList = "property_id"),
        @Index(name = "idx_document_type", columnList = "document_type"),
        @Index(name = "idx_issue_date", columnList = "issue_date"),
        @Index(name = "idx_expiry_date", columnList = "expiry_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyDocumentEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "property_id", nullable = false, length = 36)
    private String propertyId;

    @Column(name = "document_type_id")
    private Integer documentTypeId;

    @Column(name = "document_name", nullable = false, length = 255)
    private String documentName;

    @Column(name = "document_type", nullable = false, length = 100)
    private String documentType;  // LAND_CERTIFICATE, BUILDING_CERTIFICATE, etc

    @Column(name = "file_path", nullable = false, columnDefinition = "TEXT")
    private String filePath;  // URL or path to file

    @Column(name = "file_key", length = 500)
    private String fileKey;  // S3 key or identifier

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;  // NULL if no expiration

    @Column(name = "issuer", length = 255)
    private String issuer;  // Issuing authority

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "uploaded_by", nullable = false, length = 36)
    private String uploadedBy;

    // ==================== Relationships ====================
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false, insertable = false, updatable = false)
    private PropertyEntity property;

    // ==================== Lifecycle Callbacks ====================
    @PrePersist
    public void onPrePersist() {
        if (this.uploadedAt == null) {
            this.uploadedAt = AppClock.currentLocalTime();
        }
    }

    @Override
    public String toString() {
        return "PropertyDocumentEntity{" +
                "id='" + id + '\'' +
                ", documentName='" + documentName + '\'' +
                ", documentType='" + documentType + '\'' +
                '}';
    }
}