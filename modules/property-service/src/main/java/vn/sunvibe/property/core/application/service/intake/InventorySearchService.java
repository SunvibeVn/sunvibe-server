package vn.sunvibe.property.core.application.service.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.property.core.application.exception.IntakeError;
import vn.sunvibe.property.core.application.exception.IntakeRefusedException;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLead;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLeadPort;
import vn.sunvibe.property.core.application.usecase.intake.IntakeActor;
import vn.sunvibe.property.core.application.usecase.intake.InventorySearchUseCase;
import vn.sunvibe.property.core.domain.repository.IntakeTrailDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository.PropertyFacts;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Use Case Implementation - search (property-create intake spec 4.7, A-6). Only properties of the confirmed account show, read as
 * scalars, so an odd legacy row fails nothing. Not read-only: it writes the access log row.
 */
@Service
@RequiredArgsConstructor
public class InventorySearchService implements InventorySearchUseCase {

    private static final int PAGE_SIZE = 20;
    private static final int MAX_PAGE = 10_000;
    private static final String SEARCH_READ = "INVENTORY_SEARCH";

    private final IntakeLeadPort leads;
    private final IntakeEvaluator evaluator;
    private final IntakeCommandSupport support;
    private final InventoryReadRepository inventory;
    private final IntakeTrailDomainRepository trail;

    @Override
    @Transactional
    public SearchPage search(String leadId, IntakeActor actor, String propertyCodeOrNull, String wardCodeOrNull, String qOrNull,
                             int page) {
        String code = propertyCodeOrNull == null ? null : IntakeText.code("propertyCode", propertyCodeOrNull);
        String ward = wardCodeOrNull == null ? null : IntakeText.code("wardCode", wardCodeOrNull);
        String q = qOrNull == null ? null : IntakeText.require("q", qOrNull, IntakeText.MAX_QUERY);
        if ((code == null && ward == null && q == null) || page < 0 || page > MAX_PAGE) {
            throw new IntakeRefusedException(IntakeError.VALIDATION_FAILED, List.of("q"));
        }
        Instant now = IntakeCommandSupport.now();
        IntakeLead lead = leads.findVisible(leadId, actor.viewer())
                .orElseThrow(() -> new IntakeRefusedException(IntakeError.NOT_FOUND));
        support.requireAssignee(lead, actor);
        IntakeState state = evaluator.read(lead, now);
        support.requireCheckedRow(state);
        support.requireGate(state);

        trail.recordAccess(actor.userId(), leadId, state.currentOrNull().getId(), null, SEARCH_READ, now);
        String owner = state.proofOrNull().getUserId();
        if (code != null) {
            Optional<PropertyFacts> found = inventory.ownedByCode(owner, code);
            return new SearchPage(found.stream().map(this::item).toList(), 0, PAGE_SIZE, found.isPresent() ? 1 : 0);
        }
        List<Item> items = inventory.ownedPage(owner, ward, q, page * PAGE_SIZE, PAGE_SIZE).stream().map(this::item).toList();
        return new SearchPage(items, page, PAGE_SIZE, inventory.ownedCount(owner, ward, q));
    }

    private Item item(PropertyFacts property) {
        return new Item(property.id(), property.code(), property.assetTypeOrNull(), property.wardName(),
                inventory.unitsOf(property.id()).stream()
                        .map(unit -> new SearchUnit(unit.id(), unit.code(), unit.locationLabelOrNull())).toList());
    }
}
