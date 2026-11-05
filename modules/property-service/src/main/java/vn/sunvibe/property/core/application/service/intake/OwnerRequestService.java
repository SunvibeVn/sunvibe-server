package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.property.core.application.exception.IntakeError;
import vn.sunvibe.property.core.application.exception.IntakeRefusedException;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLead;
import vn.sunvibe.property.core.application.shared.external.snapshot.OwnerSnapshotCodec;
import vn.sunvibe.property.core.application.shared.external.survey.IntakeSource;
import vn.sunvibe.property.core.application.shared.external.user.OwnerRoute;
import vn.sunvibe.property.core.application.shared.external.user.UserDirectoryPort;
import vn.sunvibe.property.core.application.usecase.intake.IntakeActor;
import vn.sunvibe.property.core.application.usecase.intake.OwnerRequestSnapshot;
import vn.sunvibe.property.core.application.usecase.intake.OwnerRequestUseCase;
import vn.sunvibe.property.core.domain.model.intake.IntakeStatus;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.model.intake.OwnerRequest;
import vn.sunvibe.property.core.domain.policy.intake.IntakeCodes;
import vn.sunvibe.property.core.domain.policy.intake.ReadinessReason;
import vn.sunvibe.property.core.domain.repository.InventoryIntakeDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository;
import vn.sunvibe.property.core.domain.repository.OwnerRequestDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Use Case Implementation - the owner request (property-create intake spec 4.6 step 1, S-4). A FOUND and a NOT_FOUND address take
 * the same path and give the same answer; only the stored target and snapshot differ, and no reader of the block sees those.
 */
@Service
@RequiredArgsConstructor
public class OwnerRequestService implements OwnerRequestUseCase {

    private static final int MAX_PER_WINDOW = 5;
    private static final Duration WINDOW = Duration.ofHours(24);
    private static final Duration VALID_FOR = Duration.ofDays(7);
    private static final String EMAIL_FIELD = "ownerEmail";

    private final IntakeCommandSupport support;
    private final IntakeReadiness readiness;
    private final UserDirectoryPort users;
    private final InventoryIntakeDomainRepository intakes;
    private final OwnerRequestDomainRepository requests;
    private final InventoryReadRepository inventory;
    private final OwnerSnapshotCodec snapshots;
    private final IdGenerator<String> ulidGenerator;

    @Override
    @Transactional
    public IssuedRequest request(String leadId, IntakeActor actor, String ownerEmail, Long expectedVersion) {
        Instant now = IntakeCommandSupport.now();
        IntakeLead lead = support.lockVisibleLead(leadId, actor);
        support.requireAssignee(lead, actor);
        IntakeState state = support.lockedState(lead, now);
        requireState(state);
        support.requireGate(state);
        support.requireVersion(state, expectedVersion);
        OwnerRoute route = users.lookupConfirmable(ownerEmail);
        if (route.outcome() == OwnerRoute.Outcome.INVALID) {
            throw new IntakeRefusedException(IntakeError.VALIDATION_FAILED, List.of(EMAIL_FIELD));
        }
        if (requests.countCreatedSince(state.currentOrNull().getId(), now.minus(WINDOW)) >= MAX_PER_WINDOW) {
            throw new IntakeRefusedException(IntakeError.OWNER_REQUEST_LIMIT);
        }

        InventoryIntake row = support.settle(state, now);
        voidEarlier(row.getId(), now);
        String target = target(route, row, resultOwner(row));
        String snapshot = snapshots.write(snapshot(state, row));
        String code = IntakeCodes.newCode();
        String id = ulidGenerator.generateId();
        Instant expiresAt = now.plus(VALID_FOR);
        requests.save(OwnerRequest.create(id, row.getId(), target, IntakeCodes.hash(id, code),
                target == null ? null : snapshot, expiresAt, actor.userId(), now));
        row.awaitOwner(now);
        intakes.update(row);
        support.event(row.getId(), actor, IntakeEventAction.OWNER_REQUEST, now);
        return new IssuedRequest(id, code, expiresAt);
    }

    /** spec 4.2: DRAFT, AWAITING_OWNER, or a result whose owner proof no longer stands. */
    private void requireState(IntakeState state) {
        IntakeStatus effective = state.effectiveOrNull();
        boolean open = effective == IntakeStatus.DRAFT || effective == IntakeStatus.AWAITING_OWNER;
        boolean unconfirmedResult = effective == IntakeStatus.COMPLETED
                && readiness.result(state).reasons().contains(ReadinessReason.OWNER_NOT_CONFIRMED);
        if (!open && !unconfirmedResult) {
            throw new IntakeRefusedException(IntakeError.INTAKE_STATE);
        }
    }

    private void voidEarlier(String intakeId, Instant now) {
        for (OwnerRequest earlier : requests.findByIntakeId(intakeId)) {
            if (!earlier.isClosed()) {
                earlier.voidNow(now);
                requests.save(earlier);
            }
        }
    }

    /** Read for every address, so FOUND and NOT_FOUND run the same queries (property-create BR-5). Null on a row with no result. */
    private String resultOwner(InventoryIntake row) {
        if (row.getStatus() != IntakeStatus.COMPLETED) {
            return null;
        }
        return inventory.property(row.getResultPropertyId()).map(InventoryReadRepository.PropertyFacts::ownerId).orElse(null);
    }

    /** spec 4.2: on a COMPLETED row only the owner of the result can bind; anyone else reads as NOT_FOUND. */
    private static String target(OwnerRoute route, InventoryIntake row, String resultOwnerOrNull) {
        if (route.outcome() != OwnerRoute.Outcome.FOUND) {
            return null;
        }
        boolean binds = row.getStatus() != IntakeStatus.COMPLETED || route.userIdOrNull().equals(resultOwnerOrNull);
        return binds ? route.userIdOrNull() : null;
    }

    private static OwnerRequestSnapshot snapshot(IntakeState state, InventoryIntake row) {
        IntakeSource source = state.source().orElseThrow(() -> new IntakeRefusedException(IntakeError.INTAKE_STATE));
        List<OwnerRequestSnapshot.Part> parts = row.getSelection().stream()
                .map(space -> new OwnerRequestSnapshot.Part(space.code(), space.location())).toList();
        return new OwnerRequestSnapshot(state.lead().leadCode(), state.lead().wardName(), source.assetTypeOrNull(),
                source.addressText(), parts);
    }
}
