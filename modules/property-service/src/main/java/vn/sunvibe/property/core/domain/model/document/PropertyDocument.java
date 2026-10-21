package vn.sunvibe.property.core.domain.model.document;

import vn.sunvibe.shared.time.AppClock;
import vn.sunvibe.property.core.domain.model.vo.DocumentTypeRef;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain Model - PropertyDocument
 * <p>
 * Represents a document/certificate for a specific property.
 * Links to DocumentType (master): its renewal rule sets the expiry date when the document is created; afterwards the
 * document keeps only a reference to the type and its stored expiry date.
 * <p>
 * Relationship: PropertyDocument (N) ──1── DocumentType (Master)
 */
public class PropertyDocument {

    private String id;
    private final PropertyId propertyId;
    private final DocumentTypeRef documentType;   // The document type (master), with the name this row keeps

    private final String filePath;         // URL or path to file
    private final String fileKey;          // S3 key or identifier
    private final LocalDate issueDate;     // Issue date
    private LocalDate expiryDate;          // Expiry date (calculated from master)
    private final String issuer;           // Issuing authority
    private String notes;                  // Notes
    private LocalDateTime uploadedAt;
    private final String uploadedBy;

    // ==================== Constructor ====================

    private PropertyDocument(PropertyId propertyId, DocumentTypeRef documentType, String filePath, String fileKey,
                             LocalDate issueDate, LocalDate expiryDate, String issuer, String notes,
                             String uploadedBy) {
        if (propertyId == null) {
            throw new IllegalArgumentException("PropertyId cannot be null");
        }
        if (documentType == null) {
            throw new IllegalArgumentException("DocumentType cannot be null");
        }
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("FilePath cannot be null");
        }
        if (issueDate == null) {
            throw new IllegalArgumentException("IssueDate cannot be null");
        }
        if (uploadedBy == null || uploadedBy.isBlank()) {
            throw new IllegalArgumentException("UploadedBy cannot be null");
        }

        this.id = UUID.randomUUID().toString();
        this.propertyId = propertyId;
        this.documentType = documentType;
        this.filePath = filePath;
        this.fileKey = fileKey;
        this.issueDate = issueDate;
        this.expiryDate = expiryDate;
        this.issuer = issuer;
        this.notes = notes;
        this.uploadedAt = AppClock.currentLocalTime();
        this.uploadedBy = uploadedBy;
    }

    // ==================== Factory Methods ====================

    /**
     * Create a document (expiry date is calculated from DocumentType).
     */
    public static PropertyDocument create(PropertyId propertyId,
                                          DocumentType documentType,
                                          String filePath, String fileKey,
                                          LocalDate issueDate, String issuer,
                                          String uploadedBy) {
        if (documentType == null) {
            throw new IllegalArgumentException("DocumentType cannot be null");
        }
        if (issueDate == null) {
            throw new IllegalArgumentException("IssueDate cannot be null");
        }
        return new PropertyDocument(propertyId, DocumentTypeRef.of(documentType.getId(), documentType.getTypeName()),
                filePath, fileKey, issueDate, documentType.calculateExpiryDate(issueDate), issuer, null, uploadedBy);
    }

    /**
     * Load from database (reconstruct): the stored expiry date is kept as it is.
     */
    public static PropertyDocument load(String id, PropertyId propertyId, DocumentTypeRef documentType,
                                        String filePath, String fileKey,
                                        LocalDate issueDate, LocalDate expiryDate,
                                        String issuer, String notes,
                                        LocalDateTime uploadedAt, String uploadedBy) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Id cannot be blank");
        PropertyDocument doc = new PropertyDocument(propertyId, documentType, filePath, fileKey, issueDate,
                expiryDate, issuer, notes, uploadedBy);
        doc.id = id;
        doc.uploadedAt = uploadedAt;
        return doc;
    }

    // ==================== Business Methods ====================

    /**
     * Check whether the document is still valid.
     */
    public boolean isValid() {
        if (expiryDate == null) return true;  // No expiry date = no expiration
        return LocalDate.now().isBefore(expiryDate) || LocalDate.now().equals(expiryDate);
    }

    /**
     * Check whether the document has expired.
     */
    public boolean isExpired() {
        if (expiryDate == null) return false;
        return LocalDate.now().isAfter(expiryDate);
    }

    /**
     * Check whether the document is expiring soon (within 30 days).
     */
    public boolean isExpiringSoon() {
        if (expiryDate == null) return false;
        LocalDate threshold = LocalDate.now().plusDays(30);
        return LocalDate.now().isBefore(expiryDate) && expiryDate.isBefore(threshold);
    }

    /**
     * Check whether this document needs renewal: it has an expiry date (set from a type that requires renewal) and
     * that date has passed.
     */
    public boolean needsRenewal() {
        return isExpired();
    }

    /**
     * Calculate the number of days remaining before expiry.
     */
    public long daysUntilExpiry() {
        if (expiryDate == null) return Long.MAX_VALUE;
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }

    /**
     * Append a note.
     */
    public void addNote(String note) {
        if (note != null && !note.isBlank()) {
            if (this.notes == null) {
                this.notes = note;
            } else {
                this.notes = this.notes + "\n" + note;
            }
        }
    }

    // ==================== Getters ====================

    public String getId() {
        return id;
    }

    public PropertyId getPropertyId() {
        return propertyId;
    }

    public Integer getDocumentTypeId() {
        return documentType.getId();
    }

    public DocumentTypeRef getDocumentType() {
        return documentType;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getFileKey() {
        return fileKey;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getNotes() {
        return notes;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PropertyDocument that = (PropertyDocument) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "PropertyDocument{" +
                "id='" + id + '\'' +
                ", documentTypeId=" + documentType.getId() +
                ", documentType='" + documentType.getName() + '\'' +
                ", issueDate=" + issueDate +
                ", expiryDate=" + expiryDate +
                ", isValid=" + isValid() +
                '}';
    }
}
