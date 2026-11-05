package vn.sunvibe.survey.core.application.usecase;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The frozen source of an inventory intake (property-create intake spec 5.1): the current unmarked PASSED of a cycle with
 * the G1 address and catalog names, the values of each space, the G8 terms as reference, whether it can still be used, and
 * each marked revision of the lead. It holds a private address and owner statements: never logged.
 *
 * @param assetTypeOrNull  G1's asset type, null when the type was recorded as not applicable
 * @param sourceDate       the day the visit took place, in Vietnam
 * @param reasonOrNull     why the revision cannot be used; null when it can
 * @param markedRevisions  every marked revision of the lead, with its kind
 */
public record IntakeSourceView(String revisionId, Instant decidedAt, String performedBy, String recordedBy,
                               String contentSha256, String rentalScope, String assetTypeOrNull, String addressText,
                               String provinceCode, String provinceName, String wardCode, String wardName,
                               BigDecimal latitude, BigDecimal longitude, LocalDate sourceDate, List<Space> spaces,
                               List<Term> terms, boolean usable, String reasonOrNull,
                               List<MarkedRevision> markedRevisions) {

    /**
     * One viewed space: G1 identity, G2 access limits, G3 values, G4 occupancy as its condition and the G6 amenities seen
     * on it. A code or location comes from the survey, so it is private as the rest.
     */
    public record Space(String spaceId, String code, String location, String accessLimits, LocalDate sourceDate,
                        BigDecimal areaSqm, Integer bedrooms, Integer privateBathrooms, String condition,
                        List<String> amenities, List<FieldSource> sources) {

        @Override
        public String toString() {
            return "Space[" + spaceId + "]";
        }
    }

    /** Where one G3 value came from and when. */
    public record FieldSource(String field, String source, LocalDate date) {
    }

    /**
     * One G8 term, common or of a space: money stays amount, currency and unit (ADR-006); anything else is text. A term
     * still unknown has neither, and no source.
     */
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
        return "IntakeSourceView[" + revisionId + "]";
    }
}
