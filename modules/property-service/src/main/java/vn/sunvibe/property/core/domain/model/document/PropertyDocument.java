package vn.sunvibe.property.core.domain.model.document;

import lombok.Data;
import lombok.NoArgsConstructor;
import vn.sunvibe.common.time.AppClock;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain Model - PropertyDocument
 * <p>
 * Represents a document/certificate for a specific property.
 * Links to DocumentType (master) to know about requirements, renewal, etc.
 * <p>
 * Relationship: PropertyDocument (N) ──1── DocumentType (Master)
 */
@Data
@NoArgsConstructor
public class PropertyDocument {

    private String id;
    private PropertyId propertyId;
    private Integer documentTypeId;        // FK to document type (master)

    private DocumentType documentType;     // Reference to master (loaded separately)

    private String filePath;               // URL or path to file
    private String fileKey;                // S3 key or identifier
    private LocalDate issueDate;           // Issue date
    private LocalDate expiryDate;          // Expiry date (calculated from master)
    private String issuer;                 // Issuing authority
    private String notes;                  // Notes
    private LocalDateTime uploadedAt;
    private String uploadedBy;

    // ==================== Constructor ====================

    public PropertyDocument(PropertyId propertyId, Integer documentTypeId,
                            DocumentType documentType, String filePath, String fileKey,
                            LocalDate issueDate, String issuer, String notes,
                            String uploadedBy) {
        if (propertyId == null) {
            throw new IllegalArgumentException("PropertyId cannot be null");
        }
        if (documentTypeId == null || documentTypeId <= 0) {
            throw new IllegalArgumentException("DocumentTypeId must be > 0");
        }
        if (documentType == null) {
            throw new IllegalArgumentException("DocumentType cannot be null");
        }
        if (!documentTypeId.equals(documentType.getId())) {
            throw new IllegalArgumentException("DocumentTypeId does not match DocumentType.id");
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
        this.documentTypeId = documentTypeId;
        this.documentType = documentType;
        this.filePath = filePath;
        this.fileKey = fileKey;
        this.issueDate = issueDate;
        // Calculate expiry date if documentType requires renewal
        this.expiryDate = documentType.calculateExpiryDate(issueDate);
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
        return new PropertyDocument(propertyId, documentType.getId(), documentType,
                filePath, fileKey, issueDate, issuer, null, uploadedBy);
    }

    /**
     * Load from database (reconstruct).
     */
    public static PropertyDocument load(String id, PropertyId propertyId,
                                        Integer documentTypeId,
                                        DocumentType documentType,
                                        String filePath, String fileKey,
                                        LocalDate issueDate, LocalDate expiryDate,
                                        String issuer, String notes,
                                        LocalDateTime uploadedAt, String uploadedBy) {
        PropertyDocument doc = new PropertyDocument(propertyId, documentTypeId, documentType,
                filePath, fileKey, issueDate, issuer,
                notes, uploadedBy);
        doc.id = id;
        // If expiryDate is provided from DB, use it (do not recalculate)
        if (expiryDate != null) {
            doc.expiryDate = expiryDate;
        }
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
     * Check whether this document needs renewal.
     */
    public boolean needsRenewal() {
        return documentType.needsRenewalTracking() && isExpired();
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
                ", documentTypeId=" + documentTypeId +
                ", documentType='" + documentType.getTypeName() + '\'' +
                ", issueDate=" + issueDate +
                ", expiryDate=" + expiryDate +
                ", isValid=" + isValid() +
                '}';
    }
}