package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.property.core.application.usecase.intake.IntakeOriginUseCase;
import vn.sunvibe.property.core.application.usecase.intake.IntakeOriginView;
import vn.sunvibe.property.core.domain.model.intake.IntakeStatus;
import vn.sunvibe.property.core.domain.repository.IntakeUnitMappingDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryIntakeDomainRepository;

import java.util.Optional;

/**
 * Use Case Implementation - the origin of a COMPLETED intake (brokerage-agreement E05-S3 spec S3-D17). Two statements:
 * the intake row, then its unit mappings. No viewer: the consumer has already checked that its caller may see the lead.
 * Nothing is written.
 */
@Service
@RequiredArgsConstructor
public class IntakeOriginService implements IntakeOriginUseCase {

    private final InventoryIntakeDomainRepository intakes;
    private final IntakeUnitMappingDomainRepository mappings;

    @Override
    @Transactional(readOnly = true)
    public Optional<IntakeOriginView> originOf(String intakeId) {
        return intakes.findById(intakeId).filter(intake -> intake.getStatus() == IntakeStatus.COMPLETED)
                .map(intake -> new IntakeOriginView(intake.getLeadId(), intake.getIntentCycle(),
                        intake.getSourceChecklistId(), mappings.findByIntakeId(intake.getId()).stream()
                        .map(mapping -> new IntakeOriginView.UnitSpace(mapping.unitId(), mapping.spaceId()))
                        .toList()));
    }
}
