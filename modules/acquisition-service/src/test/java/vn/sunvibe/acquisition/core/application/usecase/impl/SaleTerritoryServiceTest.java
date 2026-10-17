package vn.sunvibe.acquisition.core.application.usecase.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.sunvibe.acquisition.core.application.exception.IneligibleAssigneeException;
import vn.sunvibe.acquisition.core.application.query.SaleTerritoryView;
import vn.sunvibe.acquisition.core.application.shared.external.area.AdministrativeWard;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.acquisition.core.domain.model.territory.SaleTerritory;
import vn.sunvibe.acquisition.core.domain.model.territory.SaleTerritoryChange;
import vn.sunvibe.acquisition.core.testsupport.InMemoryAdministrativeAreaPort;
import vn.sunvibe.acquisition.core.testsupport.InMemoryLeadAssigneeDirectory;
import vn.sunvibe.acquisition.core.testsupport.InMemoryOwnerLeadRepository;
import vn.sunvibe.acquisition.core.testsupport.InMemorySaleTerritoryChangeRepository;
import vn.sunvibe.acquisition.core.testsupport.InMemorySaleTerritoryRepository;
import vn.sunvibe.acquisition.core.testsupport.OwnerLeadFixture;
import vn.sunvibe.shared.exception.ResourceNotFoundException;
import vn.sunvibe.shared.generator.id.IdGenerator;

import java.text.Collator;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ward territories (acquisition-owner-lead-queue spec 5.3, 6; Q9-12, Q9-13; brief BR-5, BR-6, BR-10).
 */
class SaleTerritoryServiceTest {

    private static final String SALE_A = "01JSALEAAAAAAAAAAAAAAAAAAA";
    private static final String SALE_B = "01JSALEBBBBBBBBBBBBBBBBBBB";
    private static final String ADMIN = "01JADMINAAAAAAAAAAAAAAAAAA";
    private static final String OLD_ADMIN = "01JADMINBBBBBBBBBBBBBBBBBB";
    private static final Instant T0 = Instant.parse("2026-10-10T08:00:00Z");
    /** Sixteen ward names of Da Nang, deliberately not in alphabetical order. */
    private static final List<String> WARD_NAMES = List.of("Thanh Khê", "Hải Châu", "Ân Thới", "An Khê",
            "Hòa Cường", "Sơn Trà", "Ngũ Hành Sơn", "Liên Chiểu", "Hòa Khánh", "Cẩm Lệ", "Hòa Xuân", "An Hải",
            "Mỹ An", "Ngũ Hành", "Bà Nà", "Hoàng Sa");

    private InMemoryAdministrativeAreaPort areas;
    private InMemorySaleTerritoryRepository territories;
    private InMemorySaleTerritoryChangeRepository changes;
    private InMemoryLeadAssigneeDirectory directory;
    private SaleTerritoryService service;

    @BeforeEach
    void setUp() {
        areas = new InMemoryAdministrativeAreaPort();
        for (int i = 0; i < WARD_NAMES.size(); i++) {
            areas.with(new AdministrativeWard(wardCode(i), WARD_NAMES.get(i), i == 15 ? "SPECIAL_ZONE" : "WARD",
                    "48", "Đà Nẵng"));
        }
        areas.with(new AdministrativeWard("00001", "Phúc Xá", "WARD", "01", "Hà Nội"));
        territories = new InMemorySaleTerritoryRepository();
        changes = new InMemorySaleTerritoryChangeRepository();
        directory = new InMemoryLeadAssigneeDirectory().withEligible(SALE_A).withEligible(SALE_B);
        IdGenerator<Long> ids = new AtomicLong(9_000L)::incrementAndGet;
        service = new SaleTerritoryService(areas, territories, changes, directory, ids);
    }

    @Test
    void list_coversEverySixteenWardsAssignedOrNot() {
        territories.with(SaleTerritory.load(wardCode(1), SALE_A, ADMIN, T0));
        territories.with(SaleTerritory.load(wardCode(5), SALE_B, ADMIN, T0.plusSeconds(60)));

        List<SaleTerritoryView> views = service.list("48");

        assertEquals(16, views.size());
        Collator vietnamese = Collator.getInstance(Locale.forLanguageTag("vi"));
        List<String> expectedOrder = WARD_NAMES.stream().sorted(vietnamese::compare).toList();
        assertEquals(expectedOrder, views.stream().map(SaleTerritoryView::wardName).toList());
        SaleTerritoryView haiChau = view(views, wardCode(1));
        assertEquals("Hải Châu", haiChau.wardName());
        assertEquals("WARD", haiChau.wardType());
        assertEquals(SALE_A, haiChau.userId());
        assertEquals(T0, haiChau.assignedAt());
        assertEquals(SALE_B, view(views, wardCode(5)).userId());
        SaleTerritoryView free = view(views, wardCode(0));
        assertNull(free.userId());
        assertNull(free.assignedAt());
        assertEquals("SPECIAL_ZONE", view(views, wardCode(15)).wardType());
        assertEquals(14, views.stream().filter(v -> v.userId() == null).count());
    }

    @Test
    void list_unknownProvince_throwsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.list("99"));
    }

    @Test
    void assign_replacesPreviousHolderAndWritesChangeRow() {
        territories.with(SaleTerritory.load(wardCode(1), SALE_A, OLD_ADMIN, T0));
        Instant before = Instant.now();

        SaleTerritoryView view = service.assign(wardCode(1), SALE_B, ADMIN);

        assertEquals(wardCode(1), view.wardCode());
        assertEquals("Hải Châu", view.wardName());
        assertEquals(SALE_B, view.userId());
        assertFalse(view.assignedAt().isBefore(before));
        SaleTerritory stored = territories.findByWard(wardCode(1)).orElseThrow();
        assertEquals(SALE_B, stored.getUserId());
        assertEquals(ADMIN, stored.getAssignedBy());
        assertEquals(1, territories.all().size());
        List<SaleTerritoryChange> rows = changes.all();
        assertEquals(1, rows.size());
        assertEquals(wardCode(1), rows.get(0).getWardCode());
        assertEquals(SALE_A, rows.get(0).getOldUserId());
        assertEquals(SALE_B, rows.get(0).getNewUserId());
        assertEquals(ADMIN, rows.get(0).getActorUserId());
        assertEquals(view.assignedAt(), rows.get(0).getOccurredAt());
        assertNotNull(rows.get(0).getId());
    }

    @Test
    void assign_freeWard_writesChangeRowFromNobody() {
        service.assign(wardCode(2), SALE_A, ADMIN);

        SaleTerritoryChange row = changes.all().get(0);
        assertNull(row.getOldUserId());
        assertEquals(SALE_A, row.getNewUserId());
    }

    @Test
    void assign_samePerson_isNoOp() {
        territories.with(SaleTerritory.load(wardCode(1), SALE_A, OLD_ADMIN, T0));
        InMemoryLeadAssigneeDirectory nobodyEligible = new InMemoryLeadAssigneeDirectory();
        service = new SaleTerritoryService(areas, territories, changes, nobodyEligible,
                new AtomicLong(1L)::incrementAndGet);

        SaleTerritoryView view = service.assign(wardCode(1), SALE_A, ADMIN);

        assertEquals(SALE_A, view.userId());
        assertEquals(T0, view.assignedAt());
        assertEquals(OLD_ADMIN, territories.findByWard(wardCode(1)).orElseThrow().getAssignedBy());
        assertTrue(changes.all().isEmpty());
    }

    @Test
    void assign_ineligibleUser_throwsIneligibleOnUserId() {
        territories.with(SaleTerritory.load(wardCode(1), SALE_A, OLD_ADMIN, T0));

        IneligibleAssigneeException ex = assertThrows(IneligibleAssigneeException.class,
                () -> service.assign(wardCode(1), "01JOWNERAAAAAAAAAAAAAAAAAA", ADMIN));

        assertEquals("userId", ex.getField());
        assertEquals(SALE_A, territories.findByWard(wardCode(1)).orElseThrow().getUserId());
        assertTrue(changes.all().isEmpty());
    }

    @Test
    void assign_unknownWard_throwsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.assign("99999", SALE_A, ADMIN));

        assertTrue(territories.all().isEmpty());
        assertTrue(changes.all().isEmpty());
    }

    @Test
    void unassign_holder_removesAndWritesChange() {
        territories.with(SaleTerritory.load(wardCode(1), SALE_A, OLD_ADMIN, T0));

        service.unassign(wardCode(1), ADMIN);

        assertTrue(territories.findByWard(wardCode(1)).isEmpty());
        SaleTerritoryChange row = changes.all().get(0);
        assertEquals(1, changes.all().size());
        assertEquals(SALE_A, row.getOldUserId());
        assertNull(row.getNewUserId());
        assertEquals(ADMIN, row.getActorUserId());
    }

    @Test
    void unassign_nobody_noChangeRow() {
        service.unassign(wardCode(3), ADMIN);

        assertTrue(changes.all().isEmpty());
    }

    @Test
    void unassign_unknownWard_throwsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.unassign("99999", ADMIN));
    }

    @Test
    void assign_wardWithExistingLeads_neverMovesThem() {
        InMemoryOwnerLeadRepository leads = new InMemoryOwnerLeadRepository();
        OwnerLead lead = OwnerLeadFixture.newLead(OwnerLeadStatus.ASSIGNED, SALE_A, wardCode(1), T0);
        leads.save(lead);
        territories.with(SaleTerritory.load(wardCode(1), SALE_A, OLD_ADMIN, T0));
        int saves = leads.saveCount;

        service.assign(wardCode(1), SALE_B, ADMIN);
        service.unassign(wardCode(1), ADMIN);

        OwnerLead after = leads.findById(lead.getId()).orElseThrow();
        assertEquals(SALE_A, after.getAssignedUserId());
        assertEquals(OwnerLeadStatus.ASSIGNED, after.getStatus());
        assertEquals(0L, after.getVersion());
        assertEquals(saves, leads.saveCount);
    }

    private static String wardCode(int index) {
        return String.valueOf(20100 + index);
    }

    private static SaleTerritoryView view(List<SaleTerritoryView> views, String wardCode) {
        return views.stream().filter(v -> v.wardCode().equals(wardCode)).findFirst().orElseThrow();
    }
}
