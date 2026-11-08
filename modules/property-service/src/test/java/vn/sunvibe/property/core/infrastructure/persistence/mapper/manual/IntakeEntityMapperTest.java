package vn.sunvibe.property.core.infrastructure.persistence.mapper.manual;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import vn.sunvibe.property.core.domain.model.intake.AvailabilityChoice;
import vn.sunvibe.property.core.domain.model.intake.AvailabilityItem;
import vn.sunvibe.property.core.domain.model.intake.CaseOutcome;
import vn.sunvibe.property.core.domain.model.intake.CaseSignal;
import vn.sunvibe.property.core.domain.model.intake.CaseStatus;
import vn.sunvibe.property.core.domain.model.intake.DuplicateCase;
import vn.sunvibe.property.core.domain.model.intake.IntakeSourceRef;
import vn.sunvibe.property.core.domain.model.intake.IntakeStatus;
import vn.sunvibe.property.core.domain.model.intake.IntakeTarget;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.model.intake.OwnerCheckState;
import vn.sunvibe.property.core.domain.model.intake.OwnerProof;
import vn.sunvibe.property.core.domain.model.intake.ProofBinding;
import vn.sunvibe.property.core.domain.model.intake.SelectedSpace;
import vn.sunvibe.property.core.domain.model.intake.SpaceUnitMapping;
import vn.sunvibe.property.core.domain.model.intake.TargetMode;
import vn.sunvibe.property.core.domain.model.property.enums.AssetType;
import vn.sunvibe.property.core.domain.model.property.enums.RentalLayout;
import vn.sunvibe.property.core.infrastructure.persistence.json.IntakeJsonCodec;

/**
 * Stored intake rows keep every value in its own field through the row and back (cleanup spec A-5, SV-169): each
 * parameter of a {@code load} gets a value no other parameter has, so two arguments swapped in the mapper or in
 * {@code load} fail here.
 */
class IntakeEntityMapperTest {

    private final IntakeEntityMapper mapper = new IntakeEntityMapper(new IntakeJsonCodec());

    @Nested
    class InventoryIntakeRow {

        @Test
        void load_everyFieldDistinct_survivesTheMapperRoundTrip() {
            IntakeSourceRef source = new IntakeSourceRef("checklist-1", "sha-1", RentalLayout.PARTS, AssetType.VILLA,
                    "20194", "addr-1", 16.05, 108.21);
            List<SelectedSpace> selection = List.of(new SelectedSpace("space-1", "A1", "loc-1"));
            IntakeTarget target = new IntakeTarget(TargetMode.LINK, "property-1", "canonical-1",
                    List.of(new SpaceUnitMapping("space-1", "unit-1")));
            List<AvailabilityItem> availability = List.of(new AvailabilityItem("space-1", AvailabilityChoice.OCCUPIED,
                    LocalDate.of(2026, 11, 2), "note-1", LocalDate.of(2026, 12, 3), "receiver-1"));
            InventoryIntake stored = InventoryIntake.load("intake-1", 3, "lead-1", 4, source,
                    IntakeStatus.OWNER_CONFIRMED, 5L, selection, target, availability, Map.of("survey", 6L),
                    "property-2", "proof-1", "preview-1", "completer-1", at(1), at(2), at(3));

            InventoryIntake back = mapper.toDomain(mapper.toEntity(stored));

            assertEquals("intake-1", back.getId());
            assertEquals(3, back.getIntakeNo());
            assertEquals("lead-1", back.getLeadId());
            assertEquals(4, back.getIntentCycle());
            assertEquals(new IntakeSourceRef("checklist-1", "sha-1", RentalLayout.PARTS, AssetType.VILLA, "20194",
                    "addr-1", 16.05, 108.21), back.getSource());
            assertEquals(IntakeStatus.OWNER_CONFIRMED, back.getStatus());
            assertEquals(5L, back.getVersion());
            assertEquals(List.of(new SelectedSpace("space-1", "A1", "loc-1")), back.getSelection());
            assertEquals(new IntakeTarget(TargetMode.LINK, "property-1", "canonical-1",
                    List.of(new SpaceUnitMapping("space-1", "unit-1"))), back.getTarget());
            assertEquals(List.of(new AvailabilityItem("space-1", AvailabilityChoice.OCCUPIED,
                    LocalDate.of(2026, 11, 2), "note-1", LocalDate.of(2026, 12, 3), "receiver-1")),
                    back.getAvailability());
            assertEquals(Map.of("survey", 6L), back.getUsedVersions());
            assertEquals("property-2", back.getResultPropertyId());
            assertEquals("proof-1", back.getProofId());
            assertEquals("preview-1", back.getPreviewHash());
            assertEquals("completer-1", back.getCompletedByUserId());
            assertEquals(at(1), back.getCompletedAt());
            assertEquals(at(2), back.getCreatedAt());
            assertEquals(at(3), back.getUpdatedAt());
        }
    }

    @Nested
    class OwnerProofRow {

        @Test
        void load_everyFieldDistinct_survivesTheMapperRoundTrip() {
            OwnerProof stored = OwnerProof.load("proof-1", "intake-1", "request-1",
                    new ProofBinding("lead-1", 4, "checklist-1", "scope-1"), "user-1", "statement-1", at(1), at(2),
                    at(3), OwnerCheckState.DONE, 2, "phrase-1", at(4), 3, "checker-1", "basis-1", at(5));

            OwnerProof back = mapper.toDomain(mapper.toEntity(stored));

            assertEquals("proof-1", back.getId());
            assertEquals("intake-1", back.getIntakeId());
            assertEquals("request-1", back.getRequestId());
            assertEquals(new ProofBinding("lead-1", 4, "checklist-1", "scope-1"), back.getBinding());
            assertEquals("user-1", back.getUserId());
            assertEquals("statement-1", back.getStatementVersion());
            assertEquals(at(1), back.getConfirmedAt());
            assertEquals(at(2), back.getWithdrawnAt());
            assertEquals(at(3), back.getVoidedAt());
            assertEquals(OwnerCheckState.DONE, back.getOwnerCheck());
            assertEquals(2, back.getSignInLosses());
            assertEquals("phrase-1", back.getPhraseHash());
            assertEquals(at(4), back.getPhraseExpiresAt());
            assertEquals(3, back.getPhraseFailedAttempts());
            assertEquals("checker-1", back.getOwnerCheckByUserId());
            assertEquals("basis-1", back.getOwnerCheckBasis());
            assertEquals(at(5), back.getOwnerCheckAt());
        }
    }

    @Nested
    class DuplicateCaseRow {

        @Test
        void load_everyFieldDistinct_survivesTheMapperRoundTrip() {
            DuplicateCase stored = DuplicateCase.load("case-1", "intake-1", "property-1", "intake-2", "pair-1",
                    "scope-1", "scope-2", CaseSignal.PIN, CaseStatus.DECIDED, CaseOutcome.SAME_ASSET, "intake-3",
                    "basis-1", "decider-1", at(1), 7L, at(2));

            DuplicateCase back = mapper.toDomain(mapper.toEntity(stored));

            assertEquals("case-1", back.getId());
            assertEquals("intake-1", back.getIntakeId());
            assertEquals("property-1", back.getCandidatePropertyId());
            assertEquals("intake-2", back.getCandidateIntakeId());
            assertEquals("pair-1", back.getPairKey());
            assertEquals("scope-1", back.getScopeHash());
            assertEquals("scope-2", back.getCandidateScopeHash());
            assertEquals(CaseSignal.PIN, back.getSignal());
            assertEquals(CaseStatus.DECIDED, back.getStatus());
            assertEquals(CaseOutcome.SAME_ASSET, back.getOutcome());
            assertEquals("intake-3", back.getProceedingIntakeId());
            assertEquals("basis-1", back.getBasis());
            assertEquals("decider-1", back.getDecidedByUserId());
            assertEquals(at(1), back.getDecidedAt());
            assertEquals(7L, back.getVersion());
            assertEquals(at(2), back.getCreatedAt());
        }
    }

    private static Instant at(int minute) {
        return Instant.parse("2026-11-01T01:00:00Z").plusSeconds(60L * minute);
    }
}
