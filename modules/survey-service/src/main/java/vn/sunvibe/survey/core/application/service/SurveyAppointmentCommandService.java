package vn.sunvibe.survey.core.application.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;
import vn.sunvibe.survey.core.application.command.BookAppointmentCommand;
import vn.sunvibe.survey.core.application.command.CancelAppointmentCommand;
import vn.sunvibe.survey.core.application.command.ChangeAppointmentCommand;
import vn.sunvibe.survey.core.application.command.ConfirmationCommand;
import vn.sunvibe.survey.core.application.command.MeetingPlaceInput;
import vn.sunvibe.survey.core.application.command.RecordVisitCommand;
import vn.sunvibe.survey.core.application.command.ResumeSurveyCommand;
import vn.sunvibe.survey.core.application.exception.InvalidSurveyStateException;
import vn.sunvibe.survey.core.application.exception.NoCurrentVisitException;
import vn.sunvibe.survey.core.application.exception.SurveyConcurrentModificationException;
import vn.sunvibe.survey.core.application.exception.SurveyLeadNotFoundException;
import vn.sunvibe.survey.core.application.exception.SurveyNotAllowedException;
import vn.sunvibe.survey.core.application.exception.SurveyorIneligibleException;
import vn.sunvibe.survey.core.application.query.LeadState;
import vn.sunvibe.survey.core.application.query.OverlapView;
import vn.sunvibe.survey.core.application.query.SurveyCommandResult;
import vn.sunvibe.survey.core.application.shared.SurveyBlockAssembler;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMove;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveEvent;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveRefusedException;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveResult;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveTarget;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadPlace;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadVersionConflictException;
import vn.sunvibe.survey.core.application.shared.external.lead.PlaceCheck;
import vn.sunvibe.survey.core.application.shared.external.lead.PlaceViolation;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyLeadPort;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.staff.SurveyorDirectoryPort;
import vn.sunvibe.survey.core.application.usecase.SurveyAppointmentCommandUseCase;
import vn.sunvibe.survey.core.domain.exception.AppointmentAlreadyScheduledException;
import vn.sunvibe.survey.core.domain.exception.AppointmentStateException;
import vn.sunvibe.survey.core.domain.exception.InvalidSurveyValueException;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentDetails;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentKind;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentStatus;
import vn.sunvibe.survey.core.domain.model.appointment.Coordinates;
import vn.sunvibe.survey.core.domain.model.appointment.LeadLocationSnapshot;
import vn.sunvibe.survey.core.domain.model.appointment.MeetingLocation;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyIntent;
import vn.sunvibe.survey.core.domain.model.appointment.VisitRecordKind;
import vn.sunvibe.survey.core.domain.model.log.SurveyAccessLog;
import vn.sunvibe.survey.core.domain.model.resumption.SurveyResumption;
import vn.sunvibe.survey.core.domain.policy.CurrentVisitPolicy;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;
import vn.sunvibe.survey.core.domain.repository.SurveyAccessLogDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyAppointmentDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyResumptionDomainRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Use Case Implementation - SurveyAppointmentCommandService
 * <p>
 * The survey commands of staff (survey-schedule spec 4.6). Each runs in one transaction and checks, in the order of
 * spec 5.5: the request values (400, done by the command and the meeting-place check), that the caller sees the lead
 * (404, also for an appointment of a lead they cannot see), the versions (409), the state (409), the values that need
 * stored data (400), the performer and the surveyor's eligibility (422), the no-op (survey-schedule spec A-6). Then
 * the lead row is moved and flushed first and the survey rows written after (lock order), and the answer carries the
 * whole survey block, the lead's new state and its History entry; every answer writes one survey access-log row
 * (survey-schedule spec A-13).
 */
@Service
@Transactional
public class SurveyAppointmentCommandService implements SurveyAppointmentCommandUseCase {

    private final SurveyAppointmentDomainRepository appointments;
    private final SurveyResumptionDomainRepository resumptions;
    private final SurveyAccessLogDomainRepository accessLogs;
    private final SurveyLeadPort leads;
    private final SurveyorDirectoryPort directory;
    private final SurveyBlockAssembler assembler;
    private final IdGenerator<String> ulids;
    private final IdGenerator<Long> snowflakes;

    public SurveyAppointmentCommandService(SurveyAppointmentDomainRepository appointments,
                                           SurveyResumptionDomainRepository resumptions,
                                           SurveyAccessLogDomainRepository accessLogs, SurveyLeadPort leads,
                                           SurveyorDirectoryPort directory, SurveyBlockAssembler assembler,
                                           @Qualifier("Ulid") IdGenerator<String> ulids,
                                           @Qualifier("Snowflake") IdGenerator<Long> snowflakes) {
        this.appointments = appointments;
        this.resumptions = resumptions;
        this.accessLogs = accessLogs;
        this.leads = leads;
        this.directory = directory;
        this.assembler = assembler;
        this.ulids = ulids;
        this.snowflakes = snowflakes;
    }

    /**
     * survey-schedule US-1, US-3 AC-2: refused, in this order, when the lead already has a scheduled appointment,
     * when its status does not allow the booking kind, when it is not a rental lead, then by the domain's value rules
     * (re-survey reason, location reason, start window), then when the assignee may not take leads.
     */
    @Override
    public SurveyCommandResult book(SurveyViewer viewer, BookAppointmentCommand command) {
        Instant now = now();
        PlaceCheck place = command.meetingLocation() == null ? null : checkPlace(command.meetingLocation());
        LeadFacts lead = requireVisibleLead(command.leadId(), viewer);
        requireLeadVersion(lead, command.expectedLeadVersion());
        List<SurveyAppointment> ofLead = appointments.findByLeadId(lead.leadId());
        if (ofLead.stream().anyMatch(appointment -> appointment.getStatus() == AppointmentStatus.SCHEDULED)) {
            throw new AppointmentAlreadyScheduledException();
        }
        CurrentVisitPolicy.BookingKind kind = CurrentVisitPolicy.bookingKind(ofLead, lead.intentCycle());
        List<String> allowedStatuses = SurveyBlockAssembler.allowedStatusesFor(kind.kind());
        if (!allowedStatuses.contains(lead.status())) {
            throw new SurveyNotAllowedException("status", allowedStatuses);
        }
        if (!SurveyBlockAssembler.RENTAL_INTENTS.contains(lead.intent())) {
            throw new SurveyNotAllowedException("intent", SurveyBlockAssembler.RENTAL_INTENTS);
        }
        LeadLocationSnapshot leadPlace = snapshotOf(lead.place());
        MeetingLocation meeting = command.meetingLocation() == null ? MeetingLocation.fromLead(leadPlace)
                : meetingOf(command.meetingLocation(), place);
        AppointmentDetails details = AppointmentDetails.of(command.startAt(), command.durationMinutes(), meeting,
                command.accessNote(), command.onSiteContactName(), command.onSiteContactRole());
        SurveyAppointment appointment = SurveyAppointment.book(SurveyAppointmentId.of(ulids.generateId()),
                lead.leadId(), appointments.nextAppointmentNo(lead.leadId()), kind.kind(), kind.previousVisitId(),
                command.resurveyReason(), SurveyIntent.valueOf(lead.intent()), lead.intentCycle(), leadPlace, details,
                command.locationReason(), lead.assignedUserId(), command.ownerConfirmed(),
                command.confirmationNote(), viewer.userId(), now);
        if (!directory.isEligible(lead.assignedUserId())) {
            throw new SurveyorIneligibleException(null);
        }
        // survey-schedule spec 4.3: a first survey, or a re-survey of a CONTACTED lead, moves it to SURVEY_SCHEDULED
        LeadMoveTarget target = kind.kind() == AppointmentKind.FIRST || "CONTACTED".equals(lead.status())
                ? LeadMoveTarget.SURVEY_SCHEDULED : LeadMoveTarget.KEEP;
        LeadMoveResult moved = move(lead, command.expectedLeadVersion(), LeadMoveEvent.SURVEY_BOOKED, target,
                viewer, appointment, now);
        save(appointment);
        return answer(viewer, lead, moved, overlapsOf(viewer, appointment), now);
    }

    /** survey-schedule US-2 AC-1: a full new set of values with a reason; a change of time lists overlaps again. */
    @Override
    public SurveyCommandResult change(SurveyViewer viewer, ChangeAppointmentCommand command) {
        Instant now = now();
        PlaceCheck place = checkPlace(command.meetingLocation());
        SurveyAppointment appointment = requireVisibleAppointment(command.appointmentId(), viewer);
        LeadFacts lead = requireVisibleLead(appointment.getLeadId(), viewer);
        requireVersions(lead, command.expectedLeadVersion(), appointment, command.expectedAppointmentVersion());
        AppointmentDetails details = AppointmentDetails.of(command.startAt(), command.durationMinutes(),
                meetingOf(command.meetingLocation(), place), command.accessNote(), command.onSiteContactName(),
                command.onSiteContactRole());
        boolean timeChanged = details.timeDiffersFrom(appointment.getDetails());
        appointment.change(details, command.reason(), viewer.userId(), now);
        requireEligibleSurveyor(appointment);
        LeadMoveResult moved = move(lead, command.expectedLeadVersion(), LeadMoveEvent.SURVEY_CHANGED,
                LeadMoveTarget.KEEP, viewer, appointment, now);
        save(appointment);
        return answer(viewer, lead, moved, timeChanged ? overlapsOf(viewer, appointment) : List.of(), now);
    }

    /**
     * survey-schedule US-1 AC-5: record or remove the owner's confirmation; the same value again is a no-op that moves
     * nothing and writes no History entry (spec A-6), checked after every other rule.
     */
    @Override
    public SurveyCommandResult setConfirmation(SurveyViewer viewer, ConfirmationCommand command) {
        Instant now = now();
        SurveyAppointment appointment = requireVisibleAppointment(command.appointmentId(), viewer);
        LeadFacts lead = requireVisibleLead(appointment.getLeadId(), viewer);
        requireVersions(lead, command.expectedLeadVersion(), appointment, command.expectedAppointmentVersion());
        requireScheduled(appointment);
        requireEligibleSurveyor(appointment);
        boolean changed = command.confirmed()
                ? appointment.confirm(viewer.userId(), command.note(), now)
                : appointment.unconfirm(viewer.userId(), command.note(), now);
        if (!changed) {
            return answer(viewer, lead, null, List.of(), now);
        }
        LeadMoveResult moved = move(lead, command.expectedLeadVersion(),
                command.confirmed() ? LeadMoveEvent.SURVEY_CONFIRMED : LeadMoveEvent.SURVEY_UNCONFIRMED,
                LeadMoveTarget.KEEP, viewer, appointment, now);
        save(appointment);
        return answer(viewer, lead, moved, List.of(), now);
    }

    /**
     * survey-schedule US-2 AC-2, E-5: the lead stays SURVEY_SCHEDULED when its current visit can still be completed,
     * else it goes back to CONTACTED. An overdue appointment can be cancelled (BR-4); no eligibility is needed (BR-6).
     */
    @Override
    public SurveyCommandResult cancel(SurveyViewer viewer, CancelAppointmentCommand command) {
        Instant now = now();
        SurveyAppointment appointment = requireVisibleAppointment(command.appointmentId(), viewer);
        LeadFacts lead = requireVisibleLead(appointment.getLeadId(), viewer);
        requireVersions(lead, command.expectedLeadVersion(), appointment, command.expectedAppointmentVersion());
        appointment.cancel(command.cancelReason(), command.description(), viewer.userId(), now);
        boolean currentVisit = CurrentVisitPolicy.currentVisit(appointments.findByLeadId(lead.leadId()),
                lead.intentCycle()).isPresent();
        LeadMoveResult moved = move(lead, command.expectedLeadVersion(), LeadMoveEvent.SURVEY_CANCELLED,
                currentVisit ? LeadMoveTarget.KEEP : LeadMoveTarget.CONTACTED, viewer, appointment, now);
        save(appointment);
        return answer(viewer, lead, moved, List.of(), now);
    }

    /**
     * survey-schedule US-3 AC-1, AC-1a, spec A-8: an own record is the current surveyor's, who held the appointment at
     * the arrival and may take leads now; every other record is a proxy record of a manager with a reason and a basis.
     * A SALE never records for someone else (403 right after the body checks).
     */
    @Override
    public SurveyCommandResult recordVisit(SurveyViewer viewer, RecordVisitCommand command) {
        Instant now = now();
        boolean proxy = command.proxy() != null;
        if (viewer.seesOwnLeadsOnly() && (proxy || !command.performedByUserId().equals(viewer.userId()))) {
            throw new AccessDeniedException("A SALE records only their own visits.");
        }
        SurveyAppointment appointment = requireVisibleAppointment(command.appointmentId(), viewer);
        LeadFacts lead = requireVisibleLead(appointment.getLeadId(), viewer);
        requireVersions(lead, command.expectedLeadVersion(), appointment, command.expectedAppointmentVersion());
        requireScheduled(appointment);
        if (!"SURVEY_SCHEDULED".equals(lead.status())) {
            throw new InvalidSurveyStateException(List.of());
        }
        VisitRecordKind kind;
        if (proxy) {
            kind = VisitRecordKind.PROXY;
        } else if (command.performedByUserId().equals(viewer.userId())
                && viewer.userId().equals(appointment.getSurveyorUserId())) {
            kind = VisitRecordKind.OWN;
        } else {
            throw new InvalidSurveyValueException("proxy", "proxy is required for a visit recorded for someone "
                    + "else or for an appointment you no longer hold.");
        }
        appointment.recordVisit(command.arrivedAt(), command.note(), command.performedByUserId(), viewer.userId(),
                kind, proxy ? command.proxy().reason() : null, proxy ? command.proxy().basis() : null, now);
        if (kind == VisitRecordKind.OWN && !directory.isEligible(viewer.userId())) {
            throw new SurveyorIneligibleException("performedByUserId");
        }
        LeadMoveResult moved = move(lead, command.expectedLeadVersion(), LeadMoveEvent.SURVEY_VISITED,
                LeadMoveTarget.KEEP, viewer, appointment, now);
        save(appointment);
        return answer(viewer, lead, moved, List.of(), now);
    }

    /**
     * survey-schedule BR-11, B-6: a held lead with a current visit goes back to SURVEY_SCHEDULED with a reason and a
     * basis dated from the day it went on hold (spec A-25).
     */
    @Override
    public SurveyCommandResult resume(SurveyViewer viewer, ResumeSurveyCommand command) {
        Instant now = now();
        LeadFacts lead = requireVisibleLead(command.leadId(), viewer);
        requireLeadVersion(lead, command.expectedLeadVersion());
        if (!"ON_HOLD".equals(lead.status())) {
            throw new InvalidSurveyStateException(List.of("ON_HOLD"));
        }
        SurveyAppointment visit = CurrentVisitPolicy.currentVisit(appointments.findByLeadId(lead.leadId()),
                lead.intentCycle()).orElseThrow(NoCurrentVisitException::new);
        command.basis().requireWithin("basis",
                lead.onHoldSince() == null ? null : SurveyTimeRules.vietnamDate(lead.onHoldSince()),
                SurveyTimeRules.vietnamDate(now));
        LeadMoveResult moved = move(lead, command.expectedLeadVersion(), LeadMoveEvent.SURVEY_RESUMED,
                LeadMoveTarget.SURVEY_SCHEDULED, viewer, visit, now);
        resumptions.save(SurveyResumption.create(snowflakes.generateId(), lead.leadId(), visit.getId(),
                command.reason(), command.basis(), viewer.userId(), now));
        return answer(viewer, lead, moved, List.of(), now);
    }

    // ==================== steps ====================

    /** survey-schedule spec A-15: the meeting place by the lead's location rules; a broken rule is a 400. */
    private PlaceCheck checkPlace(MeetingPlaceInput input) {
        PlaceCheck check = leads.checkPlace(input.provinceCode(), input.wardCode(), input.latitude(),
                input.longitude());
        if (!check.violations().isEmpty()) {
            PlaceViolation violation = check.violations().get(0);
            throw new InvalidSurveyValueException("meetingLocation." + violation.field(), violation.message());
        }
        return check;
    }

    private LeadFacts requireVisibleLead(String leadId, SurveyViewer viewer) {
        return leads.findVisible(leadId, viewer).orElseThrow(SurveyLeadNotFoundException::new);
    }

    /**
     * An unknown or malformed id is answered like an appointment of a lead the caller cannot see (survey-schedule
     * BR-1).
     */
    private SurveyAppointment requireVisibleAppointment(String appointmentId, SurveyViewer viewer) {
        if (!SurveyAppointmentId.isValid(appointmentId)) {
            throw new SurveyLeadNotFoundException();
        }
        return appointments.findById(SurveyAppointmentId.of(appointmentId))
                .orElseThrow(SurveyLeadNotFoundException::new);
    }

    private static void requireLeadVersion(LeadFacts lead, long expectedLeadVersion) {
        if (lead.version() != expectedLeadVersion) {
            throw new SurveyConcurrentModificationException("expectedLeadVersion");
        }
    }

    private static void requireVersions(LeadFacts lead, long expectedLeadVersion, SurveyAppointment appointment,
                                        long expectedAppointmentVersion) {
        requireLeadVersion(lead, expectedLeadVersion);
        if (appointment.getVersion() == null || appointment.getVersion() != expectedAppointmentVersion) {
            throw new SurveyConcurrentModificationException("expectedAppointmentVersion");
        }
    }

    private static void requireScheduled(SurveyAppointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new AppointmentStateException(appointment.getStatus().name());
        }
    }

    /** survey-schedule BR-6, spec A-7: booking, change and confirmation need a surveyor who may take leads now. */
    private void requireEligibleSurveyor(SurveyAppointment appointment) {
        if (!directory.isEligible(appointment.getSurveyorUserId())) {
            throw new SurveyorIneligibleException(null);
        }
    }

    /** Lock order (spec 5.5): the lead row is written and flushed before any survey row. */
    private LeadMoveResult move(LeadFacts lead, long expectedLeadVersion, LeadMoveEvent event, LeadMoveTarget target,
                                SurveyViewer viewer, SurveyAppointment appointment, Instant now) {
        try {
            return leads.apply(lead.leadId(), expectedLeadVersion, new LeadMove(event, target, viewer.userId(),
                    appointment.getId().getValue(), appointment.getAppointmentNo(), now));
        } catch (LeadVersionConflictException e) {
            throw new SurveyConcurrentModificationException("expectedLeadVersion");
        } catch (LeadMoveRefusedException e) {
            throw new InvalidSurveyStateException(List.of());
        }
    }

    private void save(SurveyAppointment appointment) {
        try {
            appointments.save(appointment);
        } catch (OptimisticLockingFailureException e) {
            // survey-schedule spec 5.5: a concurrent command changed the appointment row after the lead row
            throw new SurveyConcurrentModificationException("expectedAppointmentVersion");
        }
    }

    /** survey-schedule spec A-10: the other scheduled appointments of the surveyor at the same time. */
    private List<OverlapView> overlapsOf(SurveyViewer viewer, SurveyAppointment appointment) {
        Instant start = appointment.getDetails().startAt();
        List<SurveyAppointment> overlapping = appointments.findOverlapping(appointment.getSurveyorUserId(), start,
                start.plus(Duration.ofMinutes(appointment.getDetails().durationMinutes())),
                Optional.of(appointment.getId()));
        if (overlapping.isEmpty()) {
            return List.of();
        }
        return SurveyBlockAssembler.overlapViews(overlapping,
                leads.cards(viewer, overlapping.stream().map(SurveyAppointment::getLeadId).distinct().toList()));
    }

    /**
     * The command answer (survey-schedule spec 7.2, A-23); a null move is a no-op: the lead is as read. One access-log
     * row (survey-schedule spec A-13).
     */
    private SurveyCommandResult answer(SurveyViewer viewer, LeadFacts lead, LeadMoveResult movedOrNull,
                                       List<OverlapView> overlaps, Instant now) {
        LeadFacts after = movedOrNull == null ? lead
                : lead.moved(movedOrNull.status(), movedOrNull.version(), movedOrNull.lastActivityAt());
        accessLogs.save(SurveyAccessLog.create(snowflakes.generateId(), lead.leadId(), viewer.userId(), now));
        return new SurveyCommandResult(assembler.assemble(after, viewer),
                new LeadState(after.leadId(), after.status(), after.version(), after.lastActivityAt()),
                movedOrNull == null ? null : movedOrNull.entry(), overlaps);
    }

    private static LeadLocationSnapshot snapshotOf(LeadPlace place) {
        return new LeadLocationSnapshot(place.addressDetail(), place.provinceCode(), place.provinceName(),
                place.wardCode(), place.wardName(), Coordinates.ofNullable(place.latitude(), place.longitude()));
    }

    private static MeetingLocation meetingOf(MeetingPlaceInput input, PlaceCheck check) {
        return MeetingLocation.of(input.addressText(), input.provinceCode(), check.provinceName(), input.wardCode(),
                check.wardName(), Coordinates.ofNullable(check.latitude(), check.longitude()));
    }

    /** Microseconds, as PostgreSQL stores them, so an answer shows the time a later read returns. */
    private static Instant now() {
        return AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
    }
}
