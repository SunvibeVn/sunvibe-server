package vn.sunvibe.survey.core.presentation.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.survey.core.application.command.BookAppointmentCommand;
import vn.sunvibe.survey.core.application.command.ChangeAppointmentCommand;
import vn.sunvibe.survey.core.domain.exception.InvalidSurveyValueException;
import vn.sunvibe.survey.core.presentation.converter.SurveyRequestMapper;
import vn.sunvibe.survey.core.presentation.dto.request.BasisRequest;
import vn.sunvibe.survey.core.presentation.dto.request.BookAppointmentRequest;
import vn.sunvibe.survey.core.presentation.dto.request.ChangeAppointmentRequest;
import vn.sunvibe.survey.core.presentation.dto.request.MeetingLocationRequest;
import vn.sunvibe.survey.core.presentation.dto.request.ProxyRequest;
import vn.sunvibe.survey.core.presentation.dto.request.RecordVisitRequest;
import vn.sunvibe.survey.core.presentation.dto.request.ResumeSurveyRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The booking body (survey-schedule spec 7.1, BR-4): bean validation catches missing keys and outer lengths, then the
 * command checks the cleaned values (whole minutes, durations, phone numbers). Messages never repeat the value.
 */
class SurveyRequestValidationTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();
    private static final Instant START = Instant.parse("2026-11-20T03:00:00Z");
    private static final String LEAD = "01JLEADAAAAAAAAAAAAAAAAAAA";

    private final SurveyRequestMapper mapper = new SurveyRequestMapper();

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    @Test
    void booking_smallestValidBody_hasNoViolationAndDefaultsTheDuration() {
        BookAppointmentRequest request = booking(0L, START, null, null);

        assertEquals(Set.of(), fields(request));
        BookAppointmentCommand command = mapper.toCommand(LEAD, request);
        assertEquals(60, command.durationMinutes());
        assertFalse(command.ownerConfirmed());
        assertEquals(null, command.meetingLocation());
    }

    @Test
    void booking_missingVersionOrStart_orNegativeVersion_areViolations() {
        assertEquals(Set.of("expectedLeadVersion", "startAt"), fields(booking(null, null, null, null)));
        assertEquals(Set.of("expectedLeadVersion"), fields(booking(-1L, START, null, null)));
    }

    @Test
    void booking_textsOverTheirLimits_areViolationsWithoutTheValue() {
        BookAppointmentRequest request = new BookAppointmentRequest(0L, START, null,
                new MeetingLocationRequest("secret-" + "x".repeat(260), "48", "20194", null, null), "r".repeat(501),
                "a".repeat(1001), "n".repeat(101), "o".repeat(101), null, "c".repeat(501), "s".repeat(501));

        Set<ConstraintViolation<BookAppointmentRequest>> violations = VALIDATOR.validate(request);

        assertEquals(Set.of("meetingLocation.addressText", "locationReason", "accessNote", "onSiteContactName",
                "onSiteContactRole", "confirmationNote", "resurveyReason"), names(violations));
        assertTrue(violations.stream().noneMatch(violation -> violation.getMessage().contains("secret")));
    }

    @Test
    void booking_meetingPlaceWithoutCodes_areViolations() {
        assertEquals(Set.of("meetingLocation.provinceCode", "meetingLocation.wardCode", "meetingLocation.addressText"),
                fields(booking(0L, START, new MeetingLocationRequest(null, null, null, null, null), null)));
    }

    @Test
    void mapper_cleanedValues_areCheckedByTheCommand() {
        assertEquals("startAt", assertThrows(InvalidSurveyValueException.class,
                () -> mapper.toCommand(LEAD, booking(0L, START.plusSeconds(1), null, null))).getField());
        assertEquals("durationMinutes", assertThrows(InvalidSurveyValueException.class,
                () -> mapper.toCommand(LEAD, booking(0L, START, null, 50))).getField());
        assertEquals("accessNote", assertThrows(InvalidSurveyValueException.class, () -> mapper.toCommand(LEAD,
                new BookAppointmentRequest(0L, START, null, null, null, "0905 123 456", null, null, null, null,
                        null))).getField());
        assertEquals("meetingLocation.addressText", assertThrows(InvalidSurveyValueException.class,
                () -> mapper.toCommand(LEAD, booking(0L, START, new MeetingLocationRequest("  ", "48", "20194",
                        null, null), null))).getField());
    }

    @Test
    void mapper_meetingPlace_isPassedOn() {
        BookAppointmentCommand command = mapper.toCommand(LEAD, booking(3L, START,
                new MeetingLocationRequest(" Cafe 3B ", "48", "20194", new BigDecimal("16.05"),
                        new BigDecimal("108.2")), 90));

        assertEquals("Cafe 3B", command.meetingLocation().addressText());
        assertEquals(new BigDecimal("16.05"), command.meetingLocation().latitude());
        assertEquals(90, command.durationMinutes());
        assertEquals(3L, command.expectedLeadVersion());
    }

    // ==================== change, visit, resume ====================

    /** survey-schedule spec A-4: every key of the change body is present; an optional value may be null. */
    @Test
    void changeBody_missingOptionalKey_failsOnItsPath_explicitNullClears() {
        JsonMapper json = JsonMapper.builder().build();
        String place = "\"meetingLocation\":{\"addressText\":\"12 Bach Dang\",\"provinceCode\":\"48\","
                + "\"wardCode\":\"20194\"}";
        String common = "\"expectedLeadVersion\":1,\"expectedAppointmentVersion\":0,\"reason\":\"r\","
                + "\"startAt\":\"2026-11-20T03:00:00Z\",\"durationMinutes\":60," + place;

        MismatchedInputException missing = assertThrows(MismatchedInputException.class, () -> json.readValue(
                "{" + common + ",\"onSiteContactName\":null,\"onSiteContactRole\":null}",
                ChangeAppointmentRequest.class));
        ChangeAppointmentRequest cleared = json.readValue("{" + common + ",\"accessNote\":null,"
                + "\"onSiteContactName\":\"Anh Tuan\",\"onSiteContactRole\":null}", ChangeAppointmentRequest.class);

        assertEquals("accessNote", missing.getPath().get(missing.getPath().size() - 1).getPropertyName());
        ChangeAppointmentCommand command = mapper.toCommand("01JAPPTAAAAAAAAAAAAAAAAAAA", cleared);
        assertEquals(null, command.accessNote());
        assertEquals("Anh Tuan", command.onSiteContactName());
    }

    @Test
    void visitAndResumeBodies_nestedMissingValues_areViolationsOnTheirPath() {
        RecordVisitRequest visit = new RecordVisitRequest(1L, 0L, START, "Visited", "01JSALEAAAAAAAAAAAAAAAAAAA",
                new ProxyRequest(null, new BasisRequest(null, null)));
        ResumeSurveyRequest resume = new ResumeSurveyRequest(1L, null, null);

        assertEquals(Set.of("proxy.reason", "proxy.basis.confirmedOn", "proxy.basis.text"),
                names(VALIDATOR.validate(visit)));
        assertEquals(Set.of("reason", "basis"), names(VALIDATOR.validate(resume)));
    }

    @Test
    void mapper_basisFieldsCarryTheirPrefix() {
        RecordVisitRequest visit = new RecordVisitRequest(1L, 0L, START, "Visited", "01JSALEAAAAAAAAAAAAAAAAAAA",
                new ProxyRequest("No signal", new BasisRequest(LocalDate.parse("2026-11-20"), "0905 123 456")));
        ResumeSurveyRequest resume = new ResumeSurveyRequest(1L, "Back",
                new BasisRequest(LocalDate.parse("2026-11-20"), " "));

        assertEquals("proxy.basis.text", assertThrows(InvalidSurveyValueException.class,
                () -> mapper.toCommand("01JAPPTAAAAAAAAAAAAAAAAAAA", visit)).getField());
        assertEquals("basis.text", assertThrows(InvalidSurveyValueException.class,
                () -> mapper.toCommand(LEAD, resume)).getField());
    }

    private static <T> Set<String> names(Set<ConstraintViolation<T>> violations) {
        return violations.stream().map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private static BookAppointmentRequest booking(Long version, Instant start, MeetingLocationRequest place,
                                                  Integer duration) {
        return new BookAppointmentRequest(version, start, duration, place, null, null, null, null, null, null, null);
    }

    private static Set<String> fields(BookAppointmentRequest request) {
        return names(VALIDATOR.validate(request));
    }
}
