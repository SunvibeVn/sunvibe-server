package vn.sunvibe.acquisition.core.application.usecase.impl;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.acquisition.core.application.exception.AdministrativeAreaNotFoundException;
import vn.sunvibe.acquisition.core.application.exception.IneligibleAssigneeException;
import vn.sunvibe.acquisition.core.application.query.SaleTerritoryView;
import vn.sunvibe.acquisition.core.application.shared.external.area.AdministrativeAreaPort;
import vn.sunvibe.acquisition.core.application.shared.external.area.AdministrativeWard;
import vn.sunvibe.acquisition.core.application.shared.external.staff.LeadAssigneeDirectoryPort;
import vn.sunvibe.acquisition.core.application.usecase.SaleTerritoryUseCase;
import vn.sunvibe.acquisition.core.domain.model.territory.SaleTerritory;
import vn.sunvibe.acquisition.core.domain.model.territory.SaleTerritoryChange;
import vn.sunvibe.acquisition.core.domain.repository.SaleTerritoryChangeDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.SaleTerritoryDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Use Case Implementation - SaleTerritoryService
 * <p>
 * Keeps the ward-to-person assignments (acquisition-owner-lead-queue BR-5: at most one person per ward) and records
 * every change of person (acquisition-owner-lead-queue BR-10). Wards come from the administrative-area catalog.
 * Existing leads never move when a ward changes hands (acquisition-owner-lead-queue BR-6): this service does not
 * touch leads.
 * The upsert clears the persistence context, so the answer is built from the domain objects, not from entities
 * loaded earlier in the transaction.
 */
@Service
@Transactional
public class SaleTerritoryService implements SaleTerritoryUseCase {

    private final AdministrativeAreaPort administrativeAreas;
    private final SaleTerritoryDomainRepository territories;
    private final SaleTerritoryChangeDomainRepository changes;
    private final LeadAssigneeDirectoryPort assigneeDirectory;
    private final IdGenerator<Long> idGenerator;

    public SaleTerritoryService(AdministrativeAreaPort administrativeAreas, SaleTerritoryDomainRepository territories,
                                SaleTerritoryChangeDomainRepository changes,
                                LeadAssigneeDirectoryPort assigneeDirectory,
                                @Qualifier("Snowflake") IdGenerator<Long> idGenerator) {
        this.administrativeAreas = administrativeAreas;
        this.territories = territories;
        this.changes = changes;
        this.assigneeDirectory = assigneeDirectory;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleTerritoryView> list(String provinceCode) {
        List<AdministrativeWard> wards = administrativeAreas.findWards(provinceCode)
                .orElseThrow(AdministrativeAreaNotFoundException::province);
        Map<String, SaleTerritory> byWard = territories
                .findByWardCodes(wards.stream().map(AdministrativeWard::code).toList()).stream()
                .collect(Collectors.toMap(SaleTerritory::getWardCode, Function.identity()));
        return wards.stream()
                .sorted(byName())
                .map(ward -> view(ward, Optional.ofNullable(byWard.get(ward.code()))))
                .toList();
    }

    @Override
    public SaleTerritoryView assign(String wardCode, String userId, String actorId) {
        AdministrativeWard ward = requireWard(wardCode);
        Optional<SaleTerritory> current = territories.findByWard(wardCode);
        // Giving the ward to the person who has it changes nothing and records nothing
        if (current.isPresent() && current.get().getUserId().equals(userId)) {
            return view(ward, current);
        }
        if (!assigneeDirectory.isEligibleAssignee(userId)) {
            throw new IneligibleAssigneeException("userId");
        }
        SaleTerritory territory = SaleTerritory.assign(ward.code(), userId, actorId);
        territories.upsert(territory);
        changes.save(SaleTerritoryChange.create(idGenerator.generateId(), ward.code(),
                current.map(SaleTerritory::getUserId).orElse(null), userId, actorId, territory.getAssignedAt()));
        return view(ward, Optional.of(territory));
    }

    @Override
    public void unassign(String wardCode, String actorId) {
        AdministrativeWard ward = requireWard(wardCode);
        Optional<SaleTerritory> current = territories.findByWard(wardCode);
        if (current.isEmpty()) {
            return;
        }
        territories.deleteByWard(ward.code());
        changes.save(SaleTerritoryChange.create(idGenerator.generateId(), ward.code(), current.get().getUserId(),
                null, actorId, AppClock.currentUTC()));
    }

    private AdministrativeWard requireWard(String wardCode) {
        return administrativeAreas.findWard(wardCode).orElseThrow(AdministrativeAreaNotFoundException::ward);
    }

    private static SaleTerritoryView view(AdministrativeWard ward, Optional<SaleTerritory> territory) {
        return new SaleTerritoryView(ward.code(), ward.name(), ward.type(),
                territory.map(SaleTerritory::getUserId).orElse(null),
                territory.map(SaleTerritory::getAssignedAt).orElse(null));
    }

    /** Ward names in Vietnamese order, as the catalog lists them; a new collator per call (not thread-safe). */
    private static Comparator<AdministrativeWard> byName() {
        Collator vietnamese = Collator.getInstance(Locale.forLanguageTag("vi"));
        return (a, b) -> vietnamese.compare(a.name(), b.name());
    }
}
