package vn.sunvibe.property.core.application.shared.external.survey;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The frozen source of an intake (property-create intake spec 5.1): a current unmarked PASSED with the G1 address, the
 * values of each space and the G8 terms as reference. It holds a private address and owner statements: never logged.
 *
 * @param assetTypeOrNull G1's asset type, as its code
 * @param sourceDate      the day of the visit, in Vietnam
 * @param reasonOrNull    why the revision cannot be used, as the survey names it; null when it can
 */
public record IntakeSource(String revisionId, Instant decidedAt, String performedBy, String recordedBy,
                           String contentSha256, String rentalScope, String assetTypeOrNull, String addressText,
                           String provinceCode, String provinceName, String wardCode, String wardName,
                           BigDecimal latitude, BigDecimal longitude, LocalDate sourceDate, List<Space> spaces,
                           List<Term> terms, boolean usable, String reasonOrNull,
                           List<MarkedRevision> markedRevisions) {

    /** One viewed space. */
    public record Space(String spaceId, String code, String location, String accessLimits, LocalDate sourceDate,
                        BigDecimal areaSqm, Integer bedrooms, Integer privateBathrooms, String condition,
                        List<String> amenities, List<FieldSource> sources) {

        @Override
        public String toString() {
            return "Space[" + spaceId + "]";
        }
    }

    /** Where one value of a space came from, and when. */
    public record FieldSource(String field, String source, LocalDate date) {
    }

    /** One G8 term, as reference only (property-create BR-10): money stays amount, currency and unit (ADR-006). */
    public record Term(String name, String spaceCodeOrNull, Long amount, String currency, String unit, String text,
                       String source, LocalDate date) {

        @Override
        public String toString() {
            return "Term[" + name + "]";
        }
    }

    /** A revision a manager withdrew from use, with the kind of its mark. */
    public record MarkedRevision(String revisionId, String kind) {
    }

    @Override
    public String toString() {
        return "IntakeSource[" + revisionId + "]";
    }
}
