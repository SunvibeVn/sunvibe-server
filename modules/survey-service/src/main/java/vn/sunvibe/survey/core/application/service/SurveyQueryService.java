package vn.sunvibe.survey.core.application.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;
import vn.sunvibe.survey.core.application.exception.SurveyLeadNotFoundException;
import vn.sunvibe.survey.core.application.query.OverlapView;
import vn.sunvibe.survey.core.application.query.SurveyBlock;
import vn.sunvibe.survey.core.application.query.SurveyListItem;
import vn.sunvibe.survey.core.application.query.SurveyListQuery;
import vn.sunvibe.survey.core.application.query.SurveyListScope;
import vn.sunvibe.survey.core.application.shared.SecurityEventRecorder;
import vn.sunvibe.survey.core.application.shared.SurveyBlockAssembler;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadCard;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyLeadPort;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.staff.SurveyorDirectoryPort;
import vn.sunvibe.survey.core.application.usecase.SurveyQueryUseCase;
import vn.sunvibe.survey.core.domain.exception.InvalidSurveyValueException;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;
import vn.sunvibe.survey.core.domain.model.log.SurveyAccessLog;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.model.security.SecurityOutcome;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.policy.ListGroup;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;
import vn.sunvibe.survey.core.domain.repository.PageResult;
import vn.sunvibe.survey.core.domain.repository.ScheduledSearch;
import vn.sunvibe.survey.core.domain.repository.SurveyAccessLogDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyAppointmentDomainRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Use Case Implementation - SurveyQueryService
 * <p>
 * The survey reads of staff (survey-schedule US-4, spec 7.1): a lead's survey block (one survey access-log row per
 * read, A-13), the survey list and the overlap pre-check. The list and the pre-check carry no phone, address, pin,
 * access note or on-site contact and write no access log (spec 4.9).
 */
@Service
@Transactional
public class SurveyQueryService implements SurveyQueryUseCase {

    private final SurveyAppointmentDomainRepository appointments;
    private final SurveyAccessLogDomainRepository accessLogs;
    private final SurveyLeadPort leads;
    private final SurveyorDirectoryPort directory;
    private final SurveyBlockAssembler assembler;
    private final IdGenerator<Long> snowflakes;
    private final SecurityEventRecorder securityEvents;

    public SurveyQueryService(SurveyAppointmentDomainRepository appointments,
                              SurveyAccessLogDomainRepository accessLogs, SurveyLeadPort leads,
                              SurveyorDirectoryPort directory, SurveyBlockAssembler assembler,
                              @Qualifier("Snowflake") IdGenerator<Long> snowflakes,
                              SecurityEventRecorder securityEvents) {
        this.appointments = appointments;
        this.accessLogs = accessLogs;
        this.leads = leads;
        this.directory = directory;
        this.assembler = assembler;
        this.snowflakes = snowflakes;
        this.securityEvents = securityEvents;
    }

    /**
     * survey-checklist spec 4.10: a lead that exists but is hidden from the caller writes one LEAD event; an
     * unknown lead writes none. The access-log row names the survey block (spec A-10).
     */
    @Override
    public SurveyBlock block(SurveyViewer viewer, String leadId) {
        LeadFacts lead = leads.findVisible(leadId, viewer).orElseThrow(() -> {
            if (leads.leadExists(leadId)) {
                securityEvents.refused(viewer.userId(), leadId, SecurityResourceType.LEAD, leadId, SecurityAction.READ,
                        SecurityOutcome.NOT_VISIBLE);
            }
            return new SurveyLeadNotFoundException();
        });
        SurveyBlock block = assembler.assemble(lead, viewer);
        accessLogs.save(SurveyAccessLog.create(snowflakes.generateId(), lead.leadId(), viewer.userId(),
                AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS)));
        return block;
    }

    /**
     * survey-schedule US-4, spec A-11: SCHEDULED appointments in the groups OVERDUE, TODAY, UPCOMING. A SALE sees
     * only their own ({@code scope=all} is forbidden); a row whose lead the caller cannot see is left out while the
     * total stays the count of the search.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<SurveyListItem> list(SurveyViewer viewer, SurveyListQuery query) {
        if (query.scope() == SurveyListScope.ALL && viewer.seesOwnLeadsOnly()) {
            throw new AccessDeniedException("A SALE lists only their own surveys.");
        }
        Optional<String> surveyor = query.scope() == SurveyListScope.MINE ? Optional.of(viewer.userId())
                : query.surveyorUserId();
        Instant now = AppClock.currentUTC();
        PageResult<SurveyAppointment> page = appointments.searchScheduled(new ScheduledSearch(surveyor), now,
                SurveyTimeRules.startOfNextVietnamDay(now), query.page(), query.size());
        if (page.items().isEmpty()) {
            return new PageResult<>(List.of(), page.page(), page.size(), page.totalElements());
        }
        Map<String, LeadCard> cards = leads.cards(viewer,
                page.items().stream().map(SurveyAppointment::getLeadId).distinct().toList());
        Set<String> surveyors = page.items().stream().map(SurveyAppointment::getSurveyorUserId)
                .collect(Collectors.toSet());
        Set<String> eligible = directory.eligibleAmong(surveyors);
        Map<String, String> names = directory.displayNames(surveyors);
        List<SurveyListItem> items = page.items().stream()
                .filter(appointment -> cards.containsKey(appointment.getLeadId()))
                .map(appointment -> item(appointment, cards.get(appointment.getLeadId()), names, eligible, now))
                .toList();
        return new PageResult<>(items, page.page(), page.size(), page.totalElements());
    }

    /**
     * survey-schedule US-1 AC-4, spec A-10: the scheduled appointments of the lead's surveyor that overlap the planned
     * time; on an edit the appointment itself is left out. Nothing is written.
     */
    @Override
    @Transactional(readOnly = true)
    public List<OverlapView> overlaps(SurveyViewer viewer, String leadId, Instant startAt, Integer durationMinutes,
                                      Optional<String> excludeAppointmentId) {
        SurveyTimeRules.requireWholeMinute("startAt", startAt);
        if (durationMinutes == null) {
            throw new InvalidSurveyValueException("durationMinutes", "durationMinutes is required.");
        }
        SurveyTimeRules.requireDuration(durationMinutes);
        Optional<String> exclude = excludeAppointmentId.filter(id -> !id.isBlank());
        if (exclude.isPresent() && !SurveyAppointmentId.isValid(exclude.get())) {
            throw new InvalidSurveyValueException("excludeAppointmentId", "excludeAppointmentId is not valid.");
        }
        LeadFacts lead = leads.findVisible(leadId, viewer).orElseThrow(SurveyLeadNotFoundException::new);
        if (lead.assignedUserId() == null) {
            return List.of();
        }
        List<SurveyAppointment> overlapping = appointments.findOverlapping(lead.assignedUserId(), startAt,
                startAt.plus(Duration.ofMinutes(durationMinutes)), exclude.map(SurveyAppointmentId::of));
        if (overlapping.isEmpty()) {
            return List.of();
        }
        return SurveyBlockAssembler.overlapViews(overlapping,
                leads.cards(viewer, overlapping.stream().map(SurveyAppointment::getLeadId).distinct().toList()));
    }

    private static SurveyListItem item(SurveyAppointment appointment, LeadCard card, Map<String, String> names,
                                       Set<String> eligible, Instant now) {
        return new SurveyListItem(appointment.getId().getValue(), appointment.getLeadId(), card.requestCode(),
                card.contactName(), card.wardName(), appointment.getDetails().startAt(),
                appointment.getDetails().durationMinutes(), appointment.getSurveyorUserId(),
                names.get(appointment.getSurveyorUserId()), appointment.getConfirmation().state(),
                appointment.getConfirmation().cause(), appointment.getConfirmation().everConfirmed(),
                appointment.getStatus(), ListGroup.of(appointment.getDetails().startAt(),
                        appointment.getDetails().durationMinutes(), now),
                appointment.getKind(), !eligible.contains(appointment.getSurveyorUserId()));
    }
}
