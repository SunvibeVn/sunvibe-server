package vn.sunvibe.property.core.application.usecase.intake;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The preview of a completion (property-create intake spec 7.1, exact keys, S-32). On a CREATE {@code property.code} and the
 * canonical {@code unitCode} are null, because the code is drawn at completion. It holds an address and owner statements, so it
 * stays out of every log.
 */
public record IntakePreviewView(String previewHash, Owner owner, PropertyPart property, List<UnitPart> units, SourcePart source,
                                List<TermPart> termsReference,
                                @ArraySchema(schema = @Schema(allowableValues = {"PHOTOS", "NOTES", "CONTACT", "TERMS"}))
                                List<String> notCarried,
                                @ArraySchema(schema = @Schema(allowableValues = {"TERMS_UNCONFIRMED", "UNIT_UNCLASSIFIED",
                                        "AVAILABILITY_DIFFERS", "LEGACY_PROPERTY", "SAME_PHONE_LEAD"})) List<String> warnings) {

    @Schema(name = "IntakePreviewOwner")
    public record Owner(@Schema(allowableValues = {"CONFIRMED"}) String state, @Schema(allowableValues = {"DONE"}) String check,
                        String accountAddressMasked) {
    }

    @Schema(name = "IntakePreviewProperty")
    public record PropertyPart(@Schema(nullable = true, description = "Null on a CREATE") String code, String title,
                               @Schema(nullable = true) String assetType, @Schema(nullable = true) String layout, String address,
                               String ward) {

        @Override
        public String toString() {
            return "PropertyPart[" + code + "]";
        }
    }

    @Schema(name = "IntakePreviewUnit")
    public record UnitPart(String spaceCode,
                           @Schema(nullable = true, description = "Null for the canonical unit of a CREATE") String unitCode,
                           @Schema(nullable = true) String location, @Schema(nullable = true) BigDecimal areaSqm,
                           @Schema(nullable = true) Integer bedrooms, @Schema(nullable = true) Integer bathrooms,
                           @Schema(nullable = true) String condition, List<String> amenities,
                           @Schema(allowableValues = {"AVAILABLE", "OCCUPIED", "MAINTENANCE", "BLOCKED"}) String availability,
                           List<FieldSource> sources) {

        @Override
        public String toString() {
            return "UnitPart[" + spaceCode + "]";
        }
    }

    @Schema(name = "IntakePreviewFieldSource")
    public record FieldSource(String field, @Schema(nullable = true) String source, @Schema(nullable = true) LocalDate date) {
    }

    @Schema(name = "IntakePreviewSource")
    public record SourcePart(String revisionId, Instant decidedAt, String address) {

        @Override
        public String toString() {
            return "SourcePart[" + revisionId + "]";
        }
    }

    /** A G8 term as an unconfirmed reference. {@code unit} names the space it belongs to; money stays in {@code value}. */
    @Schema(name = "IntakePreviewTerm")
    public record TermPart(String name, @Schema(nullable = true) IntakeBlockView.TermValue value,
                           @Schema(nullable = true, description = "The space's code; null for a common term") String unit,
                           @Schema(nullable = true) String source, @Schema(nullable = true) LocalDate date) {

        @Override
        public String toString() {
            return "TermPart[" + name + "]";
        }
    }

    @Override
    public String toString() {
        return "IntakePreviewView[hidden]";
    }
}
