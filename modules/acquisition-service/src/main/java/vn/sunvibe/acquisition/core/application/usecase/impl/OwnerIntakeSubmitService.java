package vn.sunvibe.acquisition.core.application.usecase.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.acquisition.core.application.command.OwnerIntakeCommand;
import vn.sunvibe.acquisition.core.application.exception.IdempotencyKeyReusedException;
import vn.sunvibe.acquisition.core.application.exception.IntakeValidationException;
import vn.sunvibe.acquisition.core.application.query.OwnerIntakeResult;
import vn.sunvibe.acquisition.core.application.shared.external.area.AdministrativeAreaPort;
import vn.sunvibe.acquisition.core.application.shared.external.area.AdministrativeWard;
import vn.sunvibe.acquisition.core.application.shared.external.security.ConsentHasher;
import vn.sunvibe.acquisition.core.application.shared.external.staff.LeadAssigneeDirectoryPort;
import vn.sunvibe.acquisition.core.application.shared.lock.IntakeLockPort;
import vn.sunvibe.acquisition.core.application.usecase.OwnerIntakeSubmitUseCase;
import vn.sunvibe.acquisition.core.domain.exception.InvalidIntakeValueException;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivity;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentChannel;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentPurpose;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentRecord;
import vn.sunvibe.acquisition.core.domain.model.idempotency.IdempotencyRecord;
import vn.sunvibe.acquisition.core.domain.model.lead.AskingPrice;
import vn.sunvibe.acquisition.core.domain.model.lead.GeoPoint;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.lead.PhoneNumber;
import vn.sunvibe.acquisition.core.domain.model.lead.RequestCode;
import vn.sunvibe.acquisition.core.domain.model.territory.SaleTerritory;
import vn.sunvibe.acquisition.core.domain.repository.ConsentRecordDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.DuplicateCandidateDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.IdempotencyKeyDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadActivityDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.SaleTerritoryDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Use Case Implementation - OwnerIntakeSubmitService
 * <p>
 * Creates an owner lead from the public intake form, or returns the code of the lead an identical earlier request
 * created (acquisition-owner-intake BR-10). Everything runs in one READ COMMITTED transaction: the advisory locks
 * (idempotency key first, then phone) are taken before any lookup, so concurrent identical requests wait for each
 * other and each lookup after the locks sees what the other request committed. A failure stores nothing.
 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class OwnerIntakeSubmitService implements OwnerIntakeSubmitUseCase {

    // acquisition-owner-intake BR-10: a key replays its first result for 24 hours; the same phone, intent, ward and
    // address within 5 minutes is the same submission
    private static final Duration KEY_LIFETIME = Duration.ofHours(24);
    private static final Duration REPEAT_WINDOW = Duration.ofMinutes(5);
    private static final int REQUEST_CODE_ATTEMPTS = 3;

    /**
     * Draws a new random request code. The default uses one {@link SecureRandom}; tests give their own.
     */
    @FunctionalInterface
    public interface RequestCodeSource {

        RequestCode next();

        static RequestCodeSource secureRandom() {
            SecureRandom random = new SecureRandom();
            return () -> RequestCode.generate(random);
        }
    }

    private final OwnerLeadDomainRepository leads;
    private final ConsentRecordDomainRepository consents;
    private final OwnerLeadActivityDomainRepository activities;
    private final DuplicateCandidateDomainRepository duplicateCandidates;
    private final IdempotencyKeyDomainRepository idempotencyKeys;
    private final SaleTerritoryDomainRepository territories;
    private final AdministrativeAreaPort administrativeAreas;
    private final LeadAssigneeDirectoryPort assigneeDirectory;
    private final ConsentHasher consentHasher;
    private final IntakeLockPort intakeLock;
    private final IdGenerator<Long> idGenerator;
    private final RequestCodeSource requestCodes;

    @Autowired
    public OwnerIntakeSubmitService(OwnerLeadDomainRepository leads, ConsentRecordDomainRepository consents,
                                    OwnerLeadActivityDomainRepository activities,
                                    DuplicateCandidateDomainRepository duplicateCandidates,
                                    IdempotencyKeyDomainRepository idempotencyKeys,
                                    SaleTerritoryDomainRepository territories,
                                    AdministrativeAreaPort administrativeAreas,
                                    LeadAssigneeDirectoryPort assigneeDirectory, ConsentHasher consentHasher,
                                    IntakeLockPort intakeLock, @Qualifier("Snowflake") IdGenerator<Long> idGenerator) {
        this(leads, consents, activities, duplicateCandidates, idempotencyKeys, territories, administrativeAreas,
                assigneeDirectory, consentHasher, intakeLock, idGenerator, RequestCodeSource.secureRandom());
    }

    OwnerIntakeSubmitService(OwnerLeadDomainRepository leads, ConsentRecordDomainRepository consents,
                             OwnerLeadActivityDomainRepository activities,
                             DuplicateCandidateDomainRepository duplicateCandidates,
                             IdempotencyKeyDomainRepository idempotencyKeys,
                             SaleTerritoryDomainRepository territories, AdministrativeAreaPort administrativeAreas,
                             LeadAssigneeDirectoryPort assigneeDirectory, ConsentHasher consentHasher,
                             IntakeLockPort intakeLock, IdGenerator<Long> idGenerator,
                             RequestCodeSource requestCodes) {
        this.leads = leads;
        this.consents = consents;
        this.activities = activities;
        this.duplicateCandidates = duplicateCandidates;
        this.idempotencyKeys = idempotencyKeys;
        this.territories = territories;
        this.administrativeAreas = administrativeAreas;
        this.assigneeDirectory = assigneeDirectory;
        this.consentHasher = consentHasher;
        this.intakeLock = intakeLock;
        this.idGenerator = idGenerator;
        this.requestCodes = requestCodes;
    }

    @Override
    public OwnerIntakeResult execute(OwnerIntakeCommand command) {
        // 0. Parse the phone (no database), then lock: idempotency key first if given, then always the phone.
        //    No lookup may run before the locks.
        PhoneNumber phone = valid(() -> PhoneNumber.parse(command.phone()));
        String key = command.idempotencyKey();
        if (key != null) {
            intakeLock.lockIdempotencyKey(key);
        }
        intakeLock.lockPhone(phone.getValue());

        Instant now = AppClock.currentUTC();
        String requestHash = requestHash(command, phone);

        // 1. A key younger than 24 hours replays its lead when the body is the same and is refused otherwise;
        //    an older key is forgotten and the request goes on as new.
        if (key != null) {
            Optional<IdempotencyRecord> stored = idempotencyKeys.findByKey(key);
            if (stored.isPresent()) {
                IdempotencyRecord record = stored.get();
                if (record.isOlderThan(KEY_LIFETIME, now)) {
                    idempotencyKeys.deleteByKey(key);
                } else if (record.requestHash().equals(requestHash)) {
                    return new OwnerIntakeResult(requestCodeOf(record.leadId()), true);
                } else {
                    throw new IdempotencyKeyReusedException();
                }
            }
        }

        // 2. Catalog checks, then the remaining values: consent, price, position.
        AdministrativeWard ward = resolveWard(command);
        ConsentRecord consent = consent(command, now);
        OwnerLead.SubmitFields fields = submitFields(command, phone, ward);

        // 3. The same phone, intent, ward and address within 5 minutes is the same submission.
        Optional<OwnerLead> recent = leads.findRecentSameSubmission(phone.getValue(), fields.intent(),
                fields.wardCode(), fields.addressKey(), now.minus(REPEAT_WINDOW));
        if (recent.isPresent()) {
            OwnerLead lead = recent.get();
            rememberKey(key, requestHash, lead.getId(), now);
            return new OwnerIntakeResult(lead.getRequestCode().getValue(), true);
        }

        // 4. Create. Every read comes before the first write, the consent before the lead it belongs to.
        //    acquisition-owner-lead-queue: a new lead goes at once to the person of its ward if they may take it.
        Optional<String> assignee = territories.findByWard(fields.wardCode())
                .map(SaleTerritory::getUserId)
                .filter(assigneeDirectory::isEligibleAssignee);
        RequestCode requestCode = freeRequestCode();
        // Open leads with the same phone may be the same owner (acquisition-owner-intake US-4); looked up before the
        // new lead is saved so it is never its own candidate. Only the ids are read: this runs under the phone lock.
        List<OwnerLeadId> openLeadsOfPhone = leads.findOpenIdsByPhone(phone.getValue());
        OwnerLead lead = OwnerLead.submit(fields, consent.getId(), assignee, command.submissionSnapshot(),
                requestCode);

        consents.save(consent);
        leads.save(lead);
        activities.save(OwnerLeadActivity.submitted(idGenerator.generateId(), lead.getId()));
        assignee.ifPresent(userId ->
                activities.save(OwnerLeadActivity.autoAssigned(idGenerator.generateId(), lead.getId(), userId)));
        if (!openLeadsOfPhone.isEmpty()) {
            duplicateCandidates.saveAll(lead.getId(), openLeadsOfPhone, now);
        }
        rememberKey(key, requestHash, lead.getId(), now);
        return new OwnerIntakeResult(requestCode.getValue(), false);
    }

    /**
     * The ward from the catalog. An unknown province is reported on the province; an unknown ward, or a ward of
     * another province, on the ward (acquisition-owner-intake BR-1).
     */
    private AdministrativeWard resolveWard(OwnerIntakeCommand command) {
        if (administrativeAreas.findWards(command.provinceCode()).isEmpty()) {
            throw new IntakeValidationException("provinceCode", "Province is not in the catalog.");
        }
        return administrativeAreas.findWard(command.wardCode())
                .filter(ward -> ward.provinceCode().equals(command.provinceCode()))
                .orElseThrow(() -> new IntakeValidationException("wardCode",
                        "Ward is not in the catalog of the chosen province."));
    }

    /**
     * acquisition-owner-intake BR-9: consent must be given to the current notice; IP and user agent are kept only as
     * keyed hashes.
     */
    private ConsentRecord consent(OwnerIntakeCommand command, Instant now) {
        if (!command.consentAccepted()) {
            throw new IntakeValidationException("consent.accepted", "Consent to be contacted must be accepted.");
        }
        String ipHash = command.clientIp() == null ? null : consentHasher.hash(command.clientIp());
        String userAgentHash = command.userAgent() == null ? null : consentHasher.hash(command.userAgent());
        return valid(() -> ConsentRecord.create(command.consentNoticeVersion(), ConsentPurpose.OWNER_INTAKE_CONTACT,
                ConsentChannel.WEB, ipHash, userAgentHash, now));
    }

    private OwnerLead.SubmitFields submitFields(OwnerIntakeCommand command, PhoneNumber phone,
                                                AdministrativeWard ward) {
        Optional<AskingPrice> askingPrice = askingPrice(command);
        Optional<GeoPoint> geoPoint = valid(() ->
                GeoPoint.of(command.latitude(), command.longitude(), ward.isSpecialZone()));
        String addressDetail = OwnerLead.normalizeText(command.addressDetail());
        return new OwnerLead.SubmitFields(OwnerLead.normalizeText(command.contactName()), phone,
                optionalText(command.email()), command.intent(), ward.provinceCode(), ward.provinceName(),
                ward.code(), ward.name(), addressDetail, OwnerLead.addressKey(addressDetail), geoPoint,
                command.propertyType(), askingPrice, command.availableFrom(), optionalText(command.notes()),
                optionalText(command.preferredCallbackTime()));
    }

    /** No price when amount, currency and unit are all left out; otherwise a full valid price. */
    private static Optional<AskingPrice> askingPrice(OwnerIntakeCommand command) {
        if (command.askingPriceAmount() == null && command.askingPriceCurrency() == null
                && command.askingPriceUnit() == null) {
            return Optional.empty();
        }
        if (command.askingPriceAmount() == null) {
            throw new IntakeValidationException("askingPrice.amount", "Asking price amount is required.");
        }
        return Optional.of(valid(() -> AskingPrice.of(command.askingPriceAmount(), command.askingPriceCurrency(),
                command.askingPriceUnit(), command.intent())));
    }

    /** A random request code no lead has yet; the unique index stays the last guard. */
    private RequestCode freeRequestCode() {
        for (int attempt = 0; attempt < REQUEST_CODE_ATTEMPTS; attempt++) {
            RequestCode code = requestCodes.next();
            if (!leads.existsByRequestCode(code.getValue())) {
                return code;
            }
        }
        throw new IllegalStateException("No free request code after " + REQUEST_CODE_ATTEMPTS + " attempts.");
    }

    private String requestCodeOf(OwnerLeadId leadId) {
        return leads.findById(leadId)
                .map(lead -> lead.getRequestCode().getValue())
                .orElseThrow(() -> new IllegalStateException("The lead of a stored idempotency key is missing."));
    }

    private void rememberKey(String key, String requestHash, OwnerLeadId leadId, Instant now) {
        if (key != null) {
            idempotencyKeys.save(new IdempotencyRecord(key, requestHash, leadId, now));
        }
    }

    /**
     * SHA-256 hex of the normalized command: every field except the idempotency key, IP, user agent and snapshot,
     * text normalized like the stored lead and the phone in its stored form, so the same request typed with other
     * spacing has the same hash. Each value is length-prefixed so no two different commands encode alike.
     */
    private static String requestHash(OwnerIntakeCommand command, PhoneNumber phone) {
        StringBuilder canonical = new StringBuilder();
        for (Object part : new Object[]{
                OwnerLead.normalizeText(command.contactName()), phone.getValue(), optionalText(command.email()),
                command.intent(), OwnerLead.normalizeText(command.provinceCode()),
                OwnerLead.normalizeText(command.wardCode()), OwnerLead.normalizeText(command.addressDetail()),
                canonical(command.latitude()), canonical(command.longitude()), command.propertyType(),
                command.askingPriceAmount(), OwnerLead.normalizeText(command.askingPriceCurrency()),
                command.askingPriceUnit(), command.availableFrom(), optionalText(command.notes()),
                optionalText(command.preferredCallbackTime()), command.consentAccepted(),
                OwnerLead.normalizeText(command.consentNoticeVersion())}) {
            if (part == null) {
                canonical.append("-|");
            } else {
                String text = part.toString();
                canonical.append(text.length()).append(':').append(text).append('|');
            }
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    /**
     * The same value in any scale gives the same text (16.0 and 16.000000 are one position). The value is not yet
     * validated, so it is written as digits and exponent ({@code 1E+999999999}): writing it out in full could take
     * all the memory.
     */
    private static String canonical(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toString();
    }

    /** Normalized text, or null when the owner left the optional field out or blank. */
    private static String optionalText(String text) {
        String normalized = OwnerLead.normalizeText(text);
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }

    /** Runs a domain factory and reports its value error on the same field. */
    private static <T> T valid(Supplier<T> factory) {
        try {
            return factory.get();
        } catch (InvalidIntakeValueException e) {
            throw IntakeValidationException.from(e);
        }
    }
}
