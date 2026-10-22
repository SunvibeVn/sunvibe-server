package vn.sunvibe.acquisition.core.application.usecase.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.sunvibe.acquisition.core.application.command.OwnerIntakeCommand;
import vn.sunvibe.acquisition.core.application.exception.IdempotencyKeyReusedException;
import vn.sunvibe.acquisition.core.application.exception.IntakeValidationException;
import vn.sunvibe.acquisition.core.application.query.OwnerIntakeResult;
import vn.sunvibe.acquisition.core.application.shared.external.area.AdministrativeWard;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivity;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentChannel;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentPurpose;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentRecord;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentRecordId;
import vn.sunvibe.acquisition.core.domain.model.idempotency.IdempotencyRecord;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadPropertyType;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadSource;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.acquisition.core.domain.model.lead.PhoneNumber;
import vn.sunvibe.acquisition.core.domain.model.lead.PriceUnit;
import vn.sunvibe.acquisition.core.domain.model.lead.RequestCode;
import vn.sunvibe.acquisition.core.domain.model.territory.SaleTerritory;
import vn.sunvibe.acquisition.core.testsupport.FakeConsentHasher;
import vn.sunvibe.acquisition.core.testsupport.InMemoryAdministrativeAreaPort;
import vn.sunvibe.acquisition.core.testsupport.InMemoryConsentRecordRepository;
import vn.sunvibe.acquisition.core.testsupport.InMemoryDuplicateCandidateRepository;
import vn.sunvibe.acquisition.core.testsupport.InMemoryIdempotencyKeyRepository;
import vn.sunvibe.acquisition.core.testsupport.InMemoryLeadAssigneeDirectory;
import vn.sunvibe.acquisition.core.testsupport.InMemoryOwnerLeadActivityRepository;
import vn.sunvibe.acquisition.core.testsupport.InMemoryOwnerLeadRepository;
import vn.sunvibe.acquisition.core.testsupport.InMemorySaleTerritoryRepository;
import vn.sunvibe.acquisition.core.testsupport.RecordingIntakeLock;
import vn.sunvibe.shared.generator.id.IdGenerator;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Submit use case of the public owner intake (acquisition-owner-intake spec 5.1 order of operations; W9-1 core,
 * W9-4..W9-11 logic; acquisition-owner-intake BR-5, BR-9, BR-10, US-4; lead-queue Q9-1, Q9-2 auto-assignment).
 */
class OwnerIntakeSubmitServiceTest {

    private static final Pattern CODE_FORMAT = Pattern.compile("^SV-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}$");
    private static final String KEY = "key-0001-abcdefgh";
    private static final String PHONE_TYPED = "0909 123 456";
    private static final String PHONE = "+84909123456";
    private static final String IP = "203.0.113.7";
    private static final String USER_AGENT = "Mozilla/5.0 test";
    private static final String SNAPSHOT = "{\"phone\":\"0909 123 456\",\"addressDetail\":\"12 Bạch Đằng\"}";
    private static final String SALE_USER = "01JSALEAAAAAAAAAAAAAAAAAAA";

    private static final AdministrativeWard HAI_CHAU =
            new AdministrativeWard("20242", "Hải Châu", "WARD", "48", "Đà Nẵng");
    private static final AdministrativeWard HOANG_SA =
            new AdministrativeWard("20333", "Hoàng Sa", "SPECIAL_ZONE", "48", "Đà Nẵng");
    private static final AdministrativeWard PHUC_XA =
            new AdministrativeWard("00001", "Phúc Xá", "WARD", "01", "Hà Nội");

    private final List<String> events = new ArrayList<>();
    private RecordingLeads leads;
    private InMemoryConsentRecordRepository consents;
    private InMemoryOwnerLeadActivityRepository activities;
    private InMemoryDuplicateCandidateRepository candidates;
    private RecordingIdempotencyKeys idempotencyKeys;
    private RecordingTerritories territories;
    private RecordingAreas areas;
    private RecordingDirectory directory;
    private RecordingLock lock;
    private FakeConsentHasher hasher;
    private Deque<String> scriptedCodes;
    private AtomicInteger codesDrawn;
    private OwnerIntakeSubmitService service;

    @BeforeEach
    void setUp() {
        leads = new RecordingLeads();
        consents = new InMemoryConsentRecordRepository();
        activities = new InMemoryOwnerLeadActivityRepository();
        candidates = new InMemoryDuplicateCandidateRepository();
        idempotencyKeys = new RecordingIdempotencyKeys();
        territories = new RecordingTerritories();
        areas = new RecordingAreas();
        areas.with(HAI_CHAU).with(HOANG_SA).with(PHUC_XA);
        directory = new RecordingDirectory();
        lock = new RecordingLock();
        hasher = new FakeConsentHasher();
        scriptedCodes = new ArrayDeque<>();
        codesDrawn = new AtomicInteger();
        AtomicLong sequence = new AtomicLong(1_000L);
        IdGenerator<Long> ids = sequence::incrementAndGet;
        OwnerIntakeSubmitService.RequestCodeSource codes = () -> {
            codesDrawn.incrementAndGet();
            return scriptedCodes.isEmpty()
                    ? RequestCode.generate(new java.security.SecureRandom())
                    : RequestCode.of(scriptedCodes.poll());
        };
        service = new OwnerIntakeSubmitService(leads, consents, activities, candidates, idempotencyKeys, territories,
                areas, directory, hasher, lock, ids, codes);
    }

    // ==================== W9-1 core ====================

    @Test
    void execute_validCommand_createsLeadConsentAndSubmittedActivity() {
        OwnerIntakeResult result = service.execute(cmd().build());

        assertFalse(result.replayed());
        assertTrue(CODE_FORMAT.matcher(result.requestCode()).matches());

        assertEquals(1, leads.all().size());
        OwnerLead lead = leads.all().get(0);
        assertEquals(result.requestCode(), lead.getRequestCode().getValue());
        assertEquals(OwnerLeadStatus.NEW, lead.getStatus());
        assertEquals(OwnerLeadSource.WEB, lead.getSource());
        assertEquals(SNAPSHOT, lead.getSubmissionSnapshot());
        assertEquals(PHONE, lead.getPhone().getValue());
        assertEquals("Nguyễn Thị Lan", lead.getContactName());
        assertEquals("48", lead.getProvinceCode());
        assertEquals("Đà Nẵng", lead.getProvinceName());
        assertEquals("20242", lead.getWardCode());
        assertEquals("Hải Châu", lead.getWardName());
        assertEquals("12 Bạch Đằng", lead.getAddressDetail());
        assertEquals("12 bạch đằng", lead.getAddressKey());
        assertEquals(15_000_000L, lead.getAskingPrice().orElseThrow().getAmount());
        assertTrue(lead.getGeoPoint().isPresent());
        assertNull(lead.getAssignedUserId());

        assertEquals(1, consents.all().size());
        ConsentRecord consent = consents.all().get(0);
        assertEquals(consent.getId(), lead.getConsentRecordId());
        assertEquals(ConsentRecord.CURRENT_NOTICE_VERSION, consent.getNoticeVersion());
        assertEquals(ConsentPurpose.OWNER_INTAKE_CONTACT, consent.getPurpose());
        assertEquals(ConsentChannel.WEB, consent.getChannel());
        assertEquals(hasher.hash(IP), consent.getIpHash());
        assertEquals(hasher.hash(USER_AGENT), consent.getUserAgentHash());

        assertEquals(1, activities.all().size());
        OwnerLeadActivity submitted = activities.all().get(0);
        assertEquals(OwnerLeadActivityType.SUBMITTED, submitted.getType());
        assertEquals(lead.getId(), submitted.getLeadId());
        assertNull(submitted.getActorUserId());

        assertEquals(0, idempotencyKeys.size());
        assertTrue(candidates.all().isEmpty());
    }

    @Test
    void execute_withoutIpOrUserAgent_storesNoHashes() {
        service.execute(cmd().clientIp(null).userAgent(null).build());

        ConsentRecord consent = consents.all().get(0);
        assertNull(consent.getIpHash());
        assertNull(consent.getUserAgentHash());
    }

    @Test
    void execute_withKey_storesTheKeyAgainstTheNewLead() {
        OwnerIntakeResult result = service.execute(cmd().key(KEY).build());

        IdempotencyRecord record = idempotencyKeys.findByKey(KEY).orElseThrow();
        assertEquals(leads.all().get(0).getId(), record.leadId());
        assertEquals(64, record.requestHash().length());
        assertTrue(record.requestHash().matches("[0-9a-f]{64}"));
        assertFalse(result.replayed());
    }

    // ==================== W9-10 lock order ====================

    @Test
    void execute_anyCommand_takesPhoneLockBeforeAnyRead() {
        service.execute(cmd().build());

        assertEquals(List.of("phone:" + PHONE), lock.locks());
        assertLocksComeFirst(List.of("lock:phone:" + PHONE));
    }

    @Test
    void execute_withKey_takesKeyLockThenPhoneLockBeforeAnyRead() {
        service.execute(cmd().key(KEY).build());

        assertEquals(List.of("idem:" + KEY, "phone:" + PHONE), lock.locks());
        assertLocksComeFirst(List.of("lock:idem:" + KEY, "lock:phone:" + PHONE));
    }

    @Test
    void execute_invalidPhone_failsOnPhoneBeforeAnyLockOrRead() {
        IntakeValidationException error = assertThrows(IntakeValidationException.class,
                () -> service.execute(cmd().phone("0123").build()));

        assertEquals("phone", error.getField());
        assertTrue(events.isEmpty());
        assertNothingStored();
    }

    // ==================== W9-7, W9-8 idempotency key ====================

    @Test
    void execute_sameKeySameBody_returnsSameCodeWithoutNewRows() {
        OwnerIntakeResult first = service.execute(cmd().key(KEY).build());
        events.clear();

        OwnerIntakeResult second = service.execute(cmd().key(KEY).build());

        assertTrue(second.replayed());
        assertEquals(first.requestCode(), second.requestCode());
        assertEquals(1, leads.all().size());
        assertEquals(1, consents.all().size());
        assertEquals(1, activities.all().size());
        assertEquals(1, idempotencyKeys.size());
        assertLocksComeFirst(List.of("lock:idem:" + KEY, "lock:phone:" + PHONE));
    }

    @Test
    void execute_sameKeyPhoneTypedDifferently_isTheSameBody() {
        OwnerIntakeResult first = service.execute(cmd().key(KEY).build());

        OwnerIntakeResult second = service.execute(cmd().key(KEY).phone("+84 909.123.456").build());

        assertTrue(second.replayed());
        assertEquals(first.requestCode(), second.requestCode());
        assertEquals(1, leads.all().size());
    }

    @Test
    void execute_sameKeyDifferentBody_throwsReusedAndStoresNothing() {
        service.execute(cmd().key(KEY).build());

        IdempotencyKeyReusedException error = assertThrows(IdempotencyKeyReusedException.class,
                () -> service.execute(cmd().key(KEY).notes("Một ghi chú khác hẳn").build()));

        assertFalse(error.getMessage().contains("ghi chú"));
        assertFalse(error.getMessage().contains(KEY));
        assertEquals(1, leads.all().size());
        assertEquals(1, consents.all().size());
        assertEquals(1, activities.all().size());
        assertEquals(1, idempotencyKeys.size());
    }

    @Test
    void execute_keyOlderThan24h_isDeletedAndRequestCreatedAsNew() {
        service.execute(cmd().key(KEY).build());
        OwnerLead firstLead = leads.all().get(0);
        IdempotencyRecord stored = idempotencyKeys.findByKey(KEY).orElseThrow();
        idempotencyKeys.save(new IdempotencyRecord(KEY, stored.requestHash(), firstLead.getId(),
                Instant.now().minus(Duration.ofHours(25))));

        OwnerIntakeResult result = service.execute(cmd().key(KEY).addressDetail("99 Trần Phú").build());

        assertFalse(result.replayed());
        assertEquals(2, leads.all().size());
        assertTrue(idempotencyKeys.deletedKeys.contains(KEY));
        IdempotencyRecord renewed = idempotencyKeys.findByKey(KEY).orElseThrow();
        OwnerLead newLead = leads.byRequestCode(result.requestCode()).orElseThrow();
        assertEquals(newLead.getId(), renewed.leadId());
        assertTrue(renewed.createdAt().isAfter(Instant.now().minus(Duration.ofMinutes(1))));
    }

    // ==================== W9-9 same submission within 5 minutes ====================

    @Test
    void execute_noKeySameSubmissionWithin5Minutes_returnsSameCode() {
        OwnerIntakeResult first = service.execute(cmd().build());

        OwnerIntakeResult second = service.execute(cmd().build());

        assertTrue(second.replayed());
        assertEquals(first.requestCode(), second.requestCode());
        assertEquals(1, leads.all().size());
        assertEquals(1, consents.all().size());
        assertEquals(1, activities.all().size());
    }

    @Test
    void execute_caseAndWhitespaceVariantOfAddress_isTheSameSubmission() {
        OwnerIntakeResult first = service.execute(cmd().addressDetail("12 Bạch Đằng").build());

        OwnerIntakeResult second = service.execute(cmd().addressDetail("  12   BẠCH   đằng ").build());

        assertTrue(second.replayed());
        assertEquals(first.requestCode(), second.requestCode());
        assertEquals(1, leads.all().size());
    }

    @Test
    void execute_sameSubmissionAfter5Minutes_createsNewLead() {
        OwnerLead old = existingLead("SV-AAAA-BBBB", PHONE_TYPED, OwnerLeadStatus.NEW, "12 Bạch Đằng",
                Instant.now().minus(Duration.ofMinutes(6)));

        OwnerIntakeResult result = service.execute(cmd().build());

        assertFalse(result.replayed());
        assertNotEquals(old.getRequestCode().getValue(), result.requestCode());
        assertEquals(2, leads.all().size());
    }

    @Test
    void execute_replayWithNewKey_storesTheKeyAgainstTheExistingLead() {
        OwnerIntakeResult first = service.execute(cmd().build());
        OwnerLead lead = leads.all().get(0);

        OwnerIntakeResult second = service.execute(cmd().key(KEY).build());

        assertTrue(second.replayed());
        assertEquals(first.requestCode(), second.requestCode());
        assertEquals(lead.getId(), idempotencyKeys.findByKey(KEY).orElseThrow().leadId());
        assertEquals(1, leads.all().size());

        OwnerIntakeResult third = service.execute(cmd().key(KEY).build());
        assertTrue(third.replayed());
        assertEquals(first.requestCode(), third.requestCode());
    }

    // ==================== W9-5 catalog ====================

    @Test
    void execute_unknownWard_failsOnWardCode() {
        IntakeValidationException error = assertThrows(IntakeValidationException.class,
                () -> service.execute(cmd().wardCode("99999").build()));

        assertEquals("wardCode", error.getField());
        assertFalse(error.getMessage().contains("99999"));
        assertNothingStored();
    }

    @Test
    void execute_wardOfAnotherProvince_failsOnWardCode() {
        IntakeValidationException error = assertThrows(IntakeValidationException.class,
                () -> service.execute(cmd().wardCode(PHUC_XA.code()).build()));

        assertEquals("wardCode", error.getField());
        assertNothingStored();
    }

    @Test
    void execute_unknownProvince_failsOnProvinceCode() {
        IntakeValidationException error = assertThrows(IntakeValidationException.class,
                () -> service.execute(cmd().provinceCode("99").build()));

        assertEquals("provinceCode", error.getField());
        assertFalse(error.getMessage().contains("99"));
        assertNothingStored();
    }

    // ==================== W9-6 coordinates ====================

    @Test
    void execute_specialZoneWardWithCoordinates_failsOnLatitude() {
        IntakeValidationException error = assertThrows(IntakeValidationException.class,
                () -> service.execute(cmd().wardCode(HOANG_SA.code()).build()));

        assertEquals("latitude", error.getField());
        assertNothingStored();
    }

    @Test
    void execute_specialZoneWardWithoutCoordinates_isAccepted() {
        OwnerIntakeResult result = service.execute(cmd().wardCode(HOANG_SA.code()).latitude(null).longitude(null)
                .build());

        assertFalse(result.replayed());
        assertTrue(leads.all().get(0).getGeoPoint().isEmpty());
    }

    @Test
    void execute_coordinatesOutsideBox_failsOnLatitude() {
        IntakeValidationException error = assertThrows(IntakeValidationException.class,
                () -> service.execute(cmd().latitude(new BigDecimal("21.0285")).longitude(new BigDecimal("105.8542"))
                        .build()));

        assertEquals("latitude", error.getField());
        assertFalse(error.getMessage().contains("21.0285"));
        assertNothingStored();
    }

    /**
     * Security gate F1: a coordinate with an absurd exponent (which bean validation refuses first at the API) is
     * refused on {@code latitude} without being written out digit by digit for the request hash or rounded.
     */
    @Test
    void execute_coordinateWithHugeExponent_failsOnLatitudeQuickly() {
        IntakeValidationException error = assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> assertThrows(IntakeValidationException.class, () -> service.execute(
                        cmd().key(KEY).latitude(new BigDecimal("1E+999999999")).build())));

        assertEquals("latitude", error.getField());
        assertNothingStored();
    }

    @Test
    void execute_coordinateWithTinyExponent_failsOnLatitudeQuickly() {
        IntakeValidationException error = assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> assertThrows(IntakeValidationException.class, () -> service.execute(
                        cmd().longitude(new BigDecimal("1E-999999999")).build())));

        assertEquals("latitude", error.getField());
        assertNothingStored();
    }

    @Test
    void execute_onlyLatitude_failsOnLatitude() {
        IntakeValidationException error = assertThrows(IntakeValidationException.class,
                () -> service.execute(cmd().longitude(null).build()));

        assertEquals("latitude", error.getField());
        assertNothingStored();
    }

    // ==================== W9-4 consent, W9-3 price ====================

    @Test
    void execute_consentNotAccepted_failsOnConsentAccepted() {
        IntakeValidationException error = assertThrows(IntakeValidationException.class,
                () -> service.execute(cmd().consentAccepted(false).build()));

        assertEquals("consent.accepted", error.getField());
        assertNothingStored();
    }

    @Test
    void execute_wrongNoticeVersion_failsOnConsentNoticeVersion() {
        IntakeValidationException error = assertThrows(IntakeValidationException.class,
                () -> service.execute(cmd().consentNoticeVersion("owner-intake-2020-01").build()));

        assertEquals("consent.noticeVersion", error.getField());
        assertFalse(error.getMessage().contains("2020-01"));
        assertNothingStored();
    }

    @Test
    void execute_priceUnitNotMatchingIntent_failsOnAskingPriceUnit() {
        IntakeValidationException error = assertThrows(IntakeValidationException.class,
                () -> service.execute(cmd().askingPriceUnit(PriceUnit.NIGHT).build()));

        assertEquals("askingPrice.unit", error.getField());
        assertNothingStored();
    }

    @Test
    void execute_noAskingPrice_storesLeadWithoutPrice() {
        service.execute(cmd().askingPriceAmount(null).askingPriceCurrency(null).askingPriceUnit(null).build());

        assertTrue(leads.all().get(0).getAskingPrice().isEmpty());
    }

    // ==================== W9-11 duplicate candidates ====================

    @Test
    void execute_openLeadWithSamePhone_addsCandidateRows() {
        Instant longAgo = Instant.now().minus(Duration.ofDays(3));
        OwnerLead newOne = existingLead("SV-AAAA-BBBB", PHONE_TYPED, OwnerLeadStatus.NEW, "1 Lê Lợi", longAgo);
        OwnerLead contacted = existingLead("SV-CCCC-DDDD", PHONE_TYPED, OwnerLeadStatus.CONTACTED, "2 Lê Lợi", longAgo);
        existingLead("SV-EEEE-FFFF", "0912 345 678", OwnerLeadStatus.NEW, "3 Lê Lợi", longAgo);

        OwnerIntakeResult result = service.execute(cmd().build());

        OwnerLeadId created = leads.byRequestCode(result.requestCode()).orElseThrow().getId();
        List<InMemoryDuplicateCandidateRepository.Link> links = candidates.all();
        assertEquals(2, links.size());
        assertTrue(links.stream().allMatch(link -> link.leadId().equals(created)));
        assertEquals(java.util.Set.of(newOne.getId(), contacted.getId()),
                links.stream().map(InMemoryDuplicateCandidateRepository.Link::candidateId)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void execute_closedLeadWithSamePhone_isNotACandidate() {
        existingLead("SV-AAAA-BBBB", PHONE_TYPED, OwnerLeadStatus.REJECTED, "1 Lê Lợi",
                Instant.now().minus(Duration.ofDays(3)));

        service.execute(cmd().build());

        assertTrue(candidates.all().isEmpty());
    }

    @Test
    void execute_newLeadIsNotItsOwnCandidate() {
        OwnerLead open = existingLead("SV-AAAA-BBBB", PHONE_TYPED, OwnerLeadStatus.ON_HOLD, "1 Lê Lợi",
                Instant.now().minus(Duration.ofDays(3)));

        OwnerIntakeResult result = service.execute(cmd().build());

        OwnerLeadId created = leads.byRequestCode(result.requestCode()).orElseThrow().getId();
        assertEquals(1, candidates.all().size());
        assertEquals(open.getId(), candidates.all().get(0).candidateId());
        assertTrue(candidates.all().stream().noneMatch(link -> link.candidateId().equals(created)));
    }

    /**
     * Security gate F2: under the phone lock the duplicate check reads only the ids of the open leads, never the
     * whole leads with their snapshots.
     */
    @Test
    void execute_openLeadsWithSamePhone_readsOnlyTheirIds() {
        existingLead("SV-AAAA-BBBB", PHONE_TYPED, OwnerLeadStatus.NEW, "1 Lê Lợi",
                Instant.now().minus(Duration.ofDays(3)));

        service.execute(cmd().build());

        assertTrue(events.contains("read:lead.findOpenIdsByPhone"), "events: " + events);
        assertEquals(1, candidates.all().size());
    }

    // ==================== Q9-1, Q9-2 auto-assignment ====================

    @Test
    void execute_wardWithEligibleAssignee_createsAssignedLeadAndAutoAssignedActivity() {
        territories.with(SaleTerritory.load(HAI_CHAU.code(), SALE_USER, "01JLEADERAAAAAAAAAAAAAAAAA",
                Instant.now().minus(Duration.ofDays(1))));
        directory.withEligible(SALE_USER);

        service.execute(cmd().build());

        OwnerLead lead = leads.all().get(0);
        assertEquals(OwnerLeadStatus.ASSIGNED, lead.getStatus());
        assertEquals(SALE_USER, lead.getAssignedUserId());
        List<OwnerLeadActivity> history = activities.findByLeadIdOrderByOccurredAt(lead.getId());
        assertEquals(List.of(OwnerLeadActivityType.SUBMITTED, OwnerLeadActivityType.AUTO_ASSIGNED),
                history.stream().map(OwnerLeadActivity::getType).toList());
        OwnerLeadActivity assigned = history.get(1);
        assertEquals(SALE_USER, assigned.getToAssigneeId());
        assertEquals(OwnerLeadStatus.NEW, assigned.getFromStatus());
        assertEquals(OwnerLeadStatus.ASSIGNED, assigned.getToStatus());
        assertNotEquals(history.get(0).getId(), assigned.getId());
    }

    @Test
    void execute_wardWithIneligibleAssignee_staysNew() {
        territories.with(SaleTerritory.load(HAI_CHAU.code(), SALE_USER, "01JLEADERAAAAAAAAAAAAAAAAA",
                Instant.now().minus(Duration.ofDays(1))));

        service.execute(cmd().build());

        OwnerLead lead = leads.all().get(0);
        assertEquals(OwnerLeadStatus.NEW, lead.getStatus());
        assertNull(lead.getAssignedUserId());
        assertEquals(List.of(OwnerLeadActivityType.SUBMITTED),
                activities.all().stream().map(OwnerLeadActivity::getType).toList());
    }

    @Test
    void execute_wardWithoutTerritory_staysNew() {
        directory.withEligible(SALE_USER);

        service.execute(cmd().build());

        assertEquals(OwnerLeadStatus.NEW, leads.all().get(0).getStatus());
        assertEquals(1, activities.all().size());
    }

    // ==================== Request code ====================

    @Test
    void execute_requestCodeTaken_triesAgainUpToThreeTimes() {
        Instant longAgo = Instant.now().minus(Duration.ofDays(3));
        existingLead("SV-AAAA-BBBB", "0912 345 678", OwnerLeadStatus.NEW, "1 Lê Lợi", longAgo);
        existingLead("SV-CCCC-DDDD", "0912 345 678", OwnerLeadStatus.NEW, "2 Lê Lợi", longAgo);
        scriptedCodes.addAll(List.of("SV-AAAA-BBBB", "SV-CCCC-DDDD", "SV-EEEE-FFFF"));

        OwnerIntakeResult result = service.execute(cmd().build());

        assertEquals("SV-EEEE-FFFF", result.requestCode());
        assertEquals(3, codesDrawn.get());
        assertEquals(3, leads.all().size());
    }

    @Test
    void execute_requestCodeAlwaysTaken_throws() {
        existingLead("SV-AAAA-BBBB", "0912 345 678", OwnerLeadStatus.NEW, "1 Lê Lợi",
                Instant.now().minus(Duration.ofDays(3)));
        scriptedCodes.addAll(List.of("SV-AAAA-BBBB", "SV-AAAA-BBBB", "SV-AAAA-BBBB", "SV-EEEE-FFFF"));

        assertThrows(IllegalStateException.class, () -> service.execute(cmd().key(KEY).build()));

        assertEquals(3, codesDrawn.get());
        assertEquals(1, leads.all().size());
        assertTrue(consents.all().isEmpty());
        assertTrue(activities.all().isEmpty());
        assertTrue(candidates.all().isEmpty());
        assertEquals(0, idempotencyKeys.size());
    }

    // ==================== Request hash ====================

    @Test
    void execute_requestHash_ignoresIpAndUserAgentAndSnapshot() {
        OwnerIntakeResult first = service.execute(cmd().key(KEY).build());

        OwnerIntakeResult second = service.execute(cmd().key(KEY).clientIp("198.51.100.9")
                .userAgent("Another browser").snapshot("{\"phone\":\"+84 909.123.456\"}").build());

        assertTrue(second.replayed());
        assertEquals(first.requestCode(), second.requestCode());
        assertEquals(1, leads.all().size());
    }

    /**
     * Each value has its own boundary in the hash: text moved from one field to the next is another body, even when
     * the separator is typed into a field. Same key, so a second body with the same hash would be replayed.
     */
    @Test
    void execute_requestHash_textMovedBetweenNotesAndCallbackTime_isAnotherBody() {
        service.execute(cmd().key(KEY).notes("a|b").preferredCallbackTime(null).build());

        assertThrows(IdempotencyKeyReusedException.class, () ->
                service.execute(cmd().key(KEY).notes("a").preferredCallbackTime("b").build()));
    }

    @Test
    void execute_requestHash_separatorTypedInBothFields_isAnotherBody() {
        service.execute(cmd().key(KEY).notes("a|b").preferredCallbackTime("c").build());

        assertThrows(IdempotencyKeyReusedException.class, () ->
                service.execute(cmd().key(KEY).notes("a").preferredCallbackTime("b|c").build()));
        assertEquals(1, leads.all().size());
    }

    /** The same position written with more trailing zeros is the same body (equal value, other scale). */
    @Test
    void execute_requestHash_sameCoordinatesWithOtherScale_isTheSameBody() {
        OwnerIntakeResult first = service.execute(cmd().key(KEY).latitude(new BigDecimal("16.0678"))
                .longitude(new BigDecimal("108.22")).build());

        OwnerIntakeResult second = service.execute(cmd().key(KEY).latitude(new BigDecimal("16.067800"))
                .longitude(new BigDecimal("108.2200000")).build());

        assertTrue(second.replayed());
        assertEquals(first.requestCode(), second.requestCode());
        assertEquals(1, leads.all().size());
    }

    @Test
    void execute_requestHash_otherCoordinates_isAnotherBody() {
        service.execute(cmd().key(KEY).latitude(new BigDecimal("16.0678")).build());

        assertThrows(IdempotencyKeyReusedException.class, () ->
                service.execute(cmd().key(KEY).latitude(new BigDecimal("16.0679")).build()));
    }

    // ==================== Helpers ====================

    private void assertLocksComeFirst(List<String> expectedLocks) {
        assertTrue(events.size() > expectedLocks.size(), "expected reads after the locks: " + events);
        assertEquals(expectedLocks, events.subList(0, expectedLocks.size()), "events: " + events);
        assertTrue(events.subList(expectedLocks.size(), events.size()).stream()
                .noneMatch(event -> event.startsWith("lock:")), "events: " + events);
    }

    private void assertNothingStored() {
        assertTrue(leads.all().isEmpty());
        assertTrue(consents.all().isEmpty());
        assertTrue(activities.all().isEmpty());
        assertTrue(candidates.all().isEmpty());
        assertEquals(0, idempotencyKeys.size());
    }

    private OwnerLead existingLead(String code, String phone, OwnerLeadStatus status, String address,
                                   Instant createdAt) {
        OwnerLead lead = OwnerLead.load(OwnerLeadId.generate(), RequestCode.of(code), status,
                OwnerLeadIntent.LONG_TERM_RENT, OwnerLeadSource.WEB, "Old Owner", PhoneNumber.parse(phone), null,
                "48", "Đà Nẵng", HAI_CHAU.code(), HAI_CHAU.name(), address, OwnerLead.addressKey(address),
                Optional.empty(), null, Optional.empty(), null, null, null, ConsentRecordId.generate(), "{}",
                status == OwnerLeadStatus.NEW ? null : SALE_USER, null, null, null, createdAt, createdAt, null);
        leads.save(lead);
        return lead;
    }

    private static Cmd cmd() {
        return new Cmd();
    }

    /** A valid command that each test changes where it needs to. */
    private static final class Cmd {
        private String key;
        private String contactName = "  Nguyễn Thị   Lan ";
        private String phone = PHONE_TYPED;
        private String email = "lan@example.com";
        private OwnerLeadIntent intent = OwnerLeadIntent.LONG_TERM_RENT;
        private String provinceCode = "48";
        private String wardCode = HAI_CHAU.code();
        private String addressDetail = "12 Bạch Đằng";
        private BigDecimal latitude = new BigDecimal("16.0678");
        private BigDecimal longitude = new BigDecimal("108.2208");
        private OwnerLeadPropertyType propertyType = OwnerLeadPropertyType.APARTMENT;
        private Long askingPriceAmount = 15_000_000L;
        private String askingPriceCurrency = "VND";
        private PriceUnit askingPriceUnit = PriceUnit.MONTH;
        private LocalDate availableFrom = LocalDate.parse("2026-11-01");
        private String notes = "Có nội thất";
        private String preferredCallbackTime = "Buổi tối";
        private boolean consentAccepted = true;
        private String consentNoticeVersion = ConsentRecord.CURRENT_NOTICE_VERSION;
        private String clientIp = IP;
        private String userAgent = USER_AGENT;
        private String snapshot = SNAPSHOT;

        Cmd key(String value) { key = value; return this; }
        Cmd phone(String value) { phone = value; return this; }
        Cmd provinceCode(String value) { provinceCode = value; return this; }
        Cmd wardCode(String value) { wardCode = value; return this; }
        Cmd addressDetail(String value) { addressDetail = value; return this; }
        Cmd latitude(BigDecimal value) { latitude = value; return this; }
        Cmd longitude(BigDecimal value) { longitude = value; return this; }
        Cmd askingPriceAmount(Long value) { askingPriceAmount = value; return this; }
        Cmd askingPriceCurrency(String value) { askingPriceCurrency = value; return this; }
        Cmd askingPriceUnit(PriceUnit value) { askingPriceUnit = value; return this; }
        Cmd notes(String value) { notes = value; return this; }
        Cmd preferredCallbackTime(String value) { preferredCallbackTime = value; return this; }
        Cmd consentAccepted(boolean value) { consentAccepted = value; return this; }
        Cmd consentNoticeVersion(String value) { consentNoticeVersion = value; return this; }
        Cmd clientIp(String value) { clientIp = value; return this; }
        Cmd userAgent(String value) { userAgent = value; return this; }
        Cmd snapshot(String value) { snapshot = value; return this; }

        OwnerIntakeCommand build() {
            return new OwnerIntakeCommand(key, contactName, phone, email, intent, provinceCode, wardCode,
                    addressDetail, latitude, longitude, propertyType, askingPriceAmount, askingPriceCurrency,
                    askingPriceUnit, availableFrom, notes, preferredCallbackTime, consentAccepted,
                    consentNoticeVersion, clientIp, userAgent, snapshot);
        }
    }

    // ==================== Fakes that record the order of locks and reads ====================

    private final class RecordingLock extends RecordingIntakeLock {
        @Override
        public void lockIdempotencyKey(String key) {
            events.add("lock:idem:" + key);
            super.lockIdempotencyKey(key);
        }

        @Override
        public void lockPhone(String phoneNormalized) {
            events.add("lock:phone:" + phoneNormalized);
            super.lockPhone(phoneNormalized);
        }
    }

    private final class RecordingLeads extends InMemoryOwnerLeadRepository {
        @Override
        public Optional<OwnerLead> findById(OwnerLeadId id) {
            events.add("read:lead.findById");
            return super.findById(id);
        }

        @Override
        public boolean existsByRequestCode(String requestCode) {
            events.add("read:lead.existsByRequestCode");
            return super.existsByRequestCode(requestCode);
        }

        @Override
        public Optional<OwnerLead> findRecentSameSubmission(String phoneNormalized, OwnerLeadIntent intent,
                                                            String wardCode, String addressKey,
                                                            Instant createdAtOrAfter) {
            events.add("read:lead.findRecentSameSubmission");
            return super.findRecentSameSubmission(phoneNormalized, intent, wardCode, addressKey, createdAtOrAfter);
        }

        @Override
        public List<OwnerLeadId> findOpenIdsByPhone(String phoneNormalized) {
            events.add("read:lead.findOpenIdsByPhone");
            return super.findOpenIdsByPhone(phoneNormalized);
        }
    }

    private final class RecordingIdempotencyKeys extends InMemoryIdempotencyKeyRepository {
        private final List<String> deletedKeys = new ArrayList<>();

        @Override
        public Optional<IdempotencyRecord> findByKey(String key) {
            events.add("read:idempotency.findByKey");
            return super.findByKey(key);
        }

        @Override
        public void deleteByKey(String key) {
            deletedKeys.add(key);
            super.deleteByKey(key);
        }
    }

    private final class RecordingAreas extends InMemoryAdministrativeAreaPort {
        @Override
        public Optional<AdministrativeWard> findWard(String wardCode) {
            events.add("read:area.findWard");
            return super.findWard(wardCode);
        }

        @Override
        public Optional<List<AdministrativeWard>> findWards(String provinceCode) {
            events.add("read:area.findWards");
            return super.findWards(provinceCode);
        }
    }

    private final class RecordingTerritories extends InMemorySaleTerritoryRepository {
        @Override
        public Optional<SaleTerritory> findByWard(String wardCode) {
            events.add("read:territory.findByWard");
            return super.findByWard(wardCode);
        }
    }

    private final class RecordingDirectory extends InMemoryLeadAssigneeDirectory {
        @Override
        public boolean isEligibleAssignee(String userId) {
            events.add("read:directory.isEligibleAssignee");
            return super.isEligibleAssignee(userId);
        }
    }
}
