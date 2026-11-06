package vn.sunvibe.property.core.infrastructure.persistence.mapper.manual;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.domain.model.intake.DuplicateCase;
import vn.sunvibe.property.core.domain.model.intake.IntakeSourceRef;
import vn.sunvibe.property.core.domain.model.intake.IntakeSuspension;
import vn.sunvibe.property.core.domain.model.intake.IntakeTarget;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.model.intake.OwnerProof;
import vn.sunvibe.property.core.domain.model.intake.OwnerRequest;
import vn.sunvibe.property.core.domain.model.intake.ProofBinding;
import vn.sunvibe.property.core.domain.model.intake.TargetMode;
import vn.sunvibe.property.core.domain.model.intake.UnitMapping;
import vn.sunvibe.property.core.infrastructure.persistence.entity.intake.DuplicateCaseEntity;
import vn.sunvibe.property.core.infrastructure.persistence.entity.intake.IntakeSuspensionEntity;
import vn.sunvibe.property.core.infrastructure.persistence.entity.intake.IntakeUnitMappingEntity;
import vn.sunvibe.property.core.infrastructure.persistence.entity.intake.InventoryIntakeEntity;
import vn.sunvibe.property.core.infrastructure.persistence.entity.intake.OwnerProofEntity;
import vn.sunvibe.property.core.infrastructure.persistence.entity.intake.OwnerRequestEntity;
import vn.sunvibe.property.core.infrastructure.persistence.json.IntakeJsonCodec;

/** Maps the intake rows both ways, every column of each. Rebuilds domain objects with their load factories. */
@Component
@RequiredArgsConstructor
public class IntakeEntityMapper {

    private final IntakeJsonCodec json;

    public InventoryIntake toDomain(InventoryIntakeEntity entity) {
        IntakeTarget target = entity.getTargetMode() == null ? null : new IntakeTarget(entity.getTargetMode(),
                entity.getTargetPropertyId(), entity.getCanonicalSpaceId(), json.mappings(entity.getTargetMappings()));
        return InventoryIntake.load(entity.getId(), entity.getIntakeNo(), entity.getLeadId(), entity.getIntentCycle(),
                new IntakeSourceRef(entity.getSourceChecklistId(), entity.getSourceSha256(), entity.getRentalScope(),
                        entity.getAssetType(), entity.getWardCode(), entity.getAddressNorm(), entity.getLatitude(),
                        entity.getLongitude()),
                entity.getStatus(), entity.getVersion(), json.selection(entity.getSelection()), target,
                json.availability(entity.getAvailability()), json.versions(entity.getUsedVersions()),
                entity.getResultPropertyId(), entity.getProofId(), entity.getPreviewHash(), entity.getCompletedByUserId(),
                entity.getCompletedAt(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public InventoryIntakeEntity toEntity(InventoryIntake domain) {
        IntakeSourceRef source = domain.getSource();
        IntakeTarget target = domain.getTarget();
        boolean link = target != null && target.mode() == TargetMode.LINK;
        return InventoryIntakeEntity.builder()
                .id(domain.getId())
                .intakeNo(domain.getIntakeNo())
                .leadId(domain.getLeadId())
                .intentCycle(domain.getIntentCycle())
                .status(domain.getStatus())
                .version(domain.getVersion())
                .sourceChecklistId(source.checklistId())
                .sourceSha256(source.sha256())
                .rentalScope(source.rentalScope())
                .assetType(source.assetType())
                .wardCode(source.wardCode())
                .addressNorm(source.addressNorm())
                .latitude(source.latitude())
                .longitude(source.longitude())
                .selection(json.selection(domain.getSelection()))
                .canonicalSpaceId(target == null ? null : target.canonicalSpaceIdOrNull())
                .targetMode(target == null ? null : target.mode())
                .targetPropertyId(target == null ? null : target.propertyIdOrNull())
                .targetMappings(link ? json.mappings(target.mappings()) : null)
                .availability(json.availability(domain.getAvailability()))
                .usedVersions(json.versions(domain.getUsedVersions()))
                .resultPropertyId(domain.getResultPropertyId())
                .proofId(domain.getProofId())
                .previewHash(domain.getPreviewHash())
                .completedByUserId(domain.getCompletedByUserId())
                .completedAt(domain.getCompletedAt())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }

    public OwnerRequest toDomain(OwnerRequestEntity entity) {
        return OwnerRequest.load(entity.getId(), entity.getIntakeId(), entity.getTargetUserId(), entity.getCodeHash(),
                entity.getFailedAttempts(), entity.getDisplaySnapshot(), entity.getExpiresAt(), entity.getConsumedAt(),
                entity.getVoidedAt(), entity.getDeclinedAt(), entity.getCreatedByUserId(), entity.getCreatedAt());
    }

    public OwnerRequestEntity toEntity(OwnerRequest domain) {
        return OwnerRequestEntity.builder()
                .id(domain.getId())
                .intakeId(domain.getIntakeId())
                .targetUserId(domain.getTargetUserId())
                .codeHash(domain.getCodeHash())
                .failedAttempts(domain.getFailedAttempts())
                .displaySnapshot(domain.getDisplaySnapshot())
                .expiresAt(domain.getExpiresAt())
                .consumedAt(domain.getConsumedAt())
                .voidedAt(domain.getVoidedAt())
                .declinedAt(domain.getDeclinedAt())
                .createdByUserId(domain.getCreatedByUserId())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    public OwnerProof toDomain(OwnerProofEntity entity) {
        return OwnerProof.load(entity.getId(), entity.getIntakeId(), entity.getRequestId(),
                new ProofBinding(entity.getLeadId(), entity.getIntentCycle(), entity.getChecklistId(), entity.getScopeHash()),
                entity.getUserId(), entity.getStatementVersion(), entity.getConfirmedAt(), entity.getWithdrawnAt(),
                entity.getVoidedAt(), entity.getOwnerCheck(), entity.getSignInLosses(), entity.getPhraseHash(),
                entity.getPhraseExpiresAt(), entity.getPhraseFailedAttempts(), entity.getOwnerCheckByUserId(),
                entity.getOwnerCheckBasis(), entity.getOwnerCheckAt());
    }

    public OwnerProofEntity toEntity(OwnerProof domain) {
        ProofBinding binding = domain.getBinding();
        return OwnerProofEntity.builder()
                .id(domain.getId())
                .intakeId(domain.getIntakeId())
                .requestId(domain.getRequestId())
                .leadId(binding.leadId())
                .intentCycle(binding.intentCycle())
                .checklistId(binding.checklistId())
                .userId(domain.getUserId())
                .scopeHash(binding.scopeHash())
                .statementVersion(domain.getStatementVersion())
                .confirmedAt(domain.getConfirmedAt())
                .withdrawnAt(domain.getWithdrawnAt())
                .voidedAt(domain.getVoidedAt())
                .ownerCheck(domain.getOwnerCheck())
                .signInLosses(domain.getSignInLosses())
                .phraseHash(domain.getPhraseHash())
                .phraseExpiresAt(domain.getPhraseExpiresAt())
                .phraseFailedAttempts(domain.getPhraseFailedAttempts())
                .ownerCheckByUserId(domain.getOwnerCheckByUserId())
                .ownerCheckBasis(domain.getOwnerCheckBasis())
                .ownerCheckAt(domain.getOwnerCheckAt())
                .build();
    }

    public DuplicateCase toDomain(DuplicateCaseEntity entity) {
        return DuplicateCase.load(entity.getId(), entity.getIntakeId(), entity.getCandidatePropertyId(),
                entity.getCandidateIntakeId(), entity.getPairKey(), entity.getScopeHash(), entity.getCandidateScopeHash(),
                entity.getSignal(), entity.getStatus(), entity.getOutcome(), entity.getProceedingIntakeId(),
                entity.getBasis(), entity.getDecidedByUserId(), entity.getDecidedAt(), entity.getVersion(),
                entity.getCreatedAt());
    }

    public DuplicateCaseEntity toEntity(DuplicateCase domain) {
        return DuplicateCaseEntity.builder()
                .id(domain.getId())
                .intakeId(domain.getIntakeId())
                .candidatePropertyId(domain.getCandidatePropertyId())
                .candidateIntakeId(domain.getCandidateIntakeId())
                .pairKey(domain.getPairKey())
                .scopeHash(domain.getScopeHash())
                .candidateScopeHash(domain.getCandidateScopeHash())
                .signal(domain.getSignal())
                .status(domain.getStatus())
                .outcome(domain.getOutcome())
                .proceedingIntakeId(domain.getProceedingIntakeId())
                .basis(domain.getBasis())
                .decidedByUserId(domain.getDecidedByUserId())
                .decidedAt(domain.getDecidedAt())
                .version(domain.getVersion())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    public IntakeSuspension toDomain(IntakeSuspensionEntity entity) {
        return IntakeSuspension.load(entity.getId(), entity.getLeadId(), entity.getIntakeId(), entity.getUnitId(),
                entity.getReason(), entity.getCreatedByUserId(), entity.getCreatedAt(), entity.getReleaseReason(),
                entity.getReleasedByUserId(), entity.getReleasedAt());
    }

    public IntakeSuspensionEntity toEntity(IntakeSuspension domain) {
        return IntakeSuspensionEntity.builder()
                .id(domain.getId())
                .leadId(domain.getLeadId())
                .intakeId(domain.getIntakeIdOrNull())
                .unitId(domain.getUnitIdOrNull())
                .reason(domain.getReason())
                .createdByUserId(domain.getCreatedByUserId())
                .createdAt(domain.getCreatedAt())
                .releaseReason(domain.getReleaseReason())
                .releasedByUserId(domain.getReleasedByUserId())
                .releasedAt(domain.getReleasedAt())
                .build();
    }

    public UnitMapping toDomain(IntakeUnitMappingEntity entity) {
        return new UnitMapping(entity.getIntakeId(), entity.getSpaceId(), entity.getSpaceCode(), entity.getUnitId(),
                entity.getCanonical());
    }

    public IntakeUnitMappingEntity toEntity(UnitMapping domain) {
        return new IntakeUnitMappingEntity(domain.intakeId(), domain.spaceId(), domain.spaceCode(), domain.unitId(),
                domain.canonical());
    }
}
