package vn.sunvibe.property.core.domain.model.document;


import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * Domain Model - DocumentType (Master)
 *
 * Represents a document type shared across multiple properties.
 * Can hold metadata about renewal requirements, archival, etc.
 *
 * Example: LAND_CERTIFICATE, BUILDING_CERTIFICATE, INSURANCE, etc
 */
@Data
@NoArgsConstructor
public class DocumentType {

    private Integer id;  // Database ID (not UUID for master data)
    private String typeName;               // Type name: "Land Certificate", etc
    private String description;            // Description: e.g. "Certificate of land use rights"
    private boolean requiresRenewal;       // Needs periodic renewal?
    private Integer renewalFrequencyDays;  // If requiresRenewal = true, interval in days
    private boolean isArchived;            // Is this type still in use?
    private String icon;                   // Icon for UI

    // ==================== Constructor ====================

    public DocumentType(Integer id, String typeName, String description,
                        boolean requiresRenewal, Integer renewalFrequencyDays,
                        boolean isArchived, String icon) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("DocumentType ID must be > 0");
        }
        if (typeName == null || typeName.isBlank()) {
            throw new IllegalArgumentException("TypeName cannot be null");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description cannot be null");
        }
        if (requiresRenewal && (renewalFrequencyDays == null || renewalFrequencyDays <= 0)) {
            throw new IllegalArgumentException(
                    "If requiresRenewal = true, renewalFrequencyDays must be > 0"
            );
        }

        this.id = id;
        this.typeName = typeName;
        this.description = description;
        this.requiresRenewal = requiresRenewal;
        this.renewalFrequencyDays = renewalFrequencyDays;
        this.isArchived = isArchived;
        this.icon = icon;
    }

    // ==================== Factory Methods ====================

    /**
     * Create a document type that does not require renewal.
     */
    public static DocumentType createNonRenewable(Integer id, String typeName,
                                                  String description, String icon) {
        return new DocumentType(id, typeName, description, false, null, false, icon);
    }

    /**
     * Create a document type that requires periodic renewal.
     */
    public static DocumentType createRenewable(Integer id, String typeName,
                                               String description,
                                               Integer renewalFrequencyDays,
                                               String icon) {
        return new DocumentType(id, typeName, description, true, renewalFrequencyDays,
                false, icon);
    }

    /**
     * Load from database (reconstruct).
     */
    public static DocumentType load(Integer id, String typeName, String description,
                                    boolean requiresRenewal, Integer renewalFrequencyDays,
                                    boolean isArchived, String icon) {
        return new DocumentType(id, typeName, description, requiresRenewal,
                renewalFrequencyDays, isArchived, icon);
    }

    // ==================== Business Methods ====================

    /**
     * Calculate the expiry date based on issue date.
     */
    public java.time.LocalDate calculateExpiryDate(java.time.LocalDate issueDate) {
        if (!requiresRenewal || renewalFrequencyDays == null) {
            return null;
        }
        if (issueDate == null) {
            throw new IllegalArgumentException("IssueDate cannot be null");
        }
        return issueDate.plusDays(renewalFrequencyDays);
    }

    /**
     * Check whether this document type needs renewal tracking.
     */
    public boolean needsRenewalTracking() {
        return requiresRenewal && renewalFrequencyDays != null && renewalFrequencyDays > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DocumentType that = (DocumentType) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DocumentType{" +
                "id=" + id +
                ", typeName='" + typeName + '\'' +
                ", requiresRenewal=" + requiresRenewal +
                '}';
    }
}