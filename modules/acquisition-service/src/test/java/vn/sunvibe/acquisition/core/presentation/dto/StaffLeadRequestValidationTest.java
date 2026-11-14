package vn.sunvibe.acquisition.core.presentation.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.acquisition.core.presentation.controller.OwnerLeadController;
import vn.sunvibe.acquisition.core.presentation.controller.SaleTerritoryController;
import vn.sunvibe.acquisition.core.presentation.dto.request.AddLeadNoteRequest;
import vn.sunvibe.acquisition.core.presentation.dto.request.AssignLeadRequest;
import vn.sunvibe.acquisition.core.presentation.dto.request.AssignLeadsRequest;
import vn.sunvibe.acquisition.core.presentation.dto.request.AssignTerritoryRequest;
import vn.sunvibe.acquisition.core.presentation.dto.request.ChangeLeadStatusRequest;
import vn.sunvibe.acquisition.core.presentation.dto.request.LeadVersionInput;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bean validation of the staff lead and territory inputs (acquisition-owner-lead-queue spec 6): user ids are at most
 * 26 characters (the length of users.id), ward codes at most 10, a version is never negative, and a next action time
 * must be one the database can store. Messages are English and never repeat the submitted value.
 */
class StaffLeadRequestValidationTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();
    private static final String ULID = "01JSALEAAAAAAAAAAAAAAAAAAA";
    private static final String TOO_LONG_ID = ULID + "X";
    private static final Instant NEXT = Instant.parse("2026-11-20T03:00:00Z");

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    // ==================== user ids ====================

    @Test
    void validate_assigneeUserIdOf26Chars_hasNoViolation() {
        assertEquals(Set.of(), fields(new AssignLeadRequest(ULID, 0L, null)));
    }

    @Test
    void validate_assigneeUserIdOver26Chars_reportsAssigneeUserIdWithoutTheValue() {
        Set<ConstraintViolation<AssignLeadRequest>> violations =
                VALIDATOR.validate(new AssignLeadRequest(TOO_LONG_ID, 0L, null));

        assertEquals(Set.of("assigneeUserId"), fieldsOf(violations));
        violations.forEach(v -> assertFalse(v.getMessage().contains(TOO_LONG_ID), v.getMessage()));
    }

    @Test
    void validate_territoryUserIdOver26Chars_reportsUserId() {
        assertEquals(Set.of(), fields(new AssignTerritoryRequest(ULID)));
        assertEquals(Set.of("userId"), fields(new AssignTerritoryRequest(TOO_LONG_ID)));
    }

    /**
     * acquisition-owner-lead-queue C-17: every refused duplicate target has one message, so an over-long id is not
     * refused here with a message of its own; the service refuses it with the shared message.
     */
    @Test
    void validate_duplicateOfLeadIdOver26Chars_isLeftToTheTargetRule() {
        assertEquals(Set.of(), fields(statusChange(0L, "x".repeat(1_000_000), null)));
    }

    // ==================== expectedVersion ====================

    @Test
    void validate_statusChangeNegativeExpectedVersion_reportsExpectedVersion() {
        assertEquals(Set.of(), fields(statusChange(0L, null, null)));
        assertEquals(Set.of("expectedVersion"), fields(statusChange(-1L, null, null)));
    }

    // ==================== nextActionAt ====================

    @ParameterizedTest
    @ValueSource(strings = {"1970-01-01T00:00:00Z", "2026-11-20T03:00:00Z", "9999-12-31T23:59:59.999999999Z"})
    void validate_nextActionAtInStorableRange_hasNoViolation(String value) {
        Instant at = Instant.parse(value);

        assertEquals(Set.of(), fields(statusChange(0L, null, at)));
        assertEquals(Set.of(), fields(new AddLeadNoteRequest("note", at)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1969-12-31T23:59:59.999999999Z", "+10000-01-01T00:00:00Z", "+1000000-01-01T00:00:00Z",
            "-1000000-01-01T00:00:00Z"})
    void validate_nextActionAtOutsideStorableRange_reportsNextActionAtWithoutTheValue(String value) {
        Instant at = Instant.parse(value);

        Set<ConstraintViolation<ChangeLeadStatusRequest>> change = VALIDATOR.validate(statusChange(0L, null, at));
        Set<ConstraintViolation<AddLeadNoteRequest>> note = VALIDATOR.validate(new AddLeadNoteRequest("note", at));

        assertEquals(Set.of("nextActionAt"), fieldsOf(change));
        assertEquals(Set.of("nextActionAt"), fieldsOf(note));
        change.forEach(v -> assertFalse(v.getMessage().contains(String.valueOf(at.atZone(java.time.ZoneOffset.UTC)
                .getYear())), v.getMessage()));
    }

    @Test
    void validate_nextActionAtMinAndMaxInstant_reportNextActionAt() {
        assertEquals(Set.of("nextActionAt"), fields(new AddLeadNoteRequest("note", Instant.MIN)));
        assertEquals(Set.of("nextActionAt"), fields(new AddLeadNoteRequest("note", Instant.MAX)));
        assertEquals(Set.of(), fields(new AddLeadNoteRequest("note", null)));
    }

    // ==================== ward codes ====================

    @Test
    void validateParameters_listWardCodeOver10Chars_reportsWardCode() throws Exception {
        OwnerLeadController controller = new OwnerLeadController(null, null, null, null, null);
        Method list = method(OwnerLeadController.class, "list");

        assertEquals(Set.of(), violatedParameters(controller, list,
                new Object[]{"mine", List.<OwnerLeadStatus>of(), "1234567890", null, null, 0, 20}));
        assertEquals(Set.of(2), violatedParameters(controller, list,
                new Object[]{"mine", List.<OwnerLeadStatus>of(), "12345678901", null, null, 0, 20}));
    }

    /** acquisition-owner-lead-queue-ui C-27: the assignee of a list call is 1 to 26 characters. */
    @Test
    void validateParameters_listAssigneeUserIdLength_reportsAssigneeUserId() throws Exception {
        OwnerLeadController controller = new OwnerLeadController(null, null, null, null, null);
        Method list = method(OwnerLeadController.class, "list");

        assertEquals(Set.of(), violatedParameters(controller, list,
                new Object[]{"all", List.<OwnerLeadStatus>of(), null, null, "A", 0, 20}));
        assertEquals(Set.of(), violatedParameters(controller, list,
                new Object[]{"all", List.<OwnerLeadStatus>of(), null, null, "A".repeat(26), 0, 20}));
        assertEquals(Set.of(4), violatedParameters(controller, list,
                new Object[]{"all", List.<OwnerLeadStatus>of(), null, null, "A".repeat(27), 0, 20}));
        assertEquals(Set.of(4), violatedParameters(controller, list,
                new Object[]{"all", List.<OwnerLeadStatus>of(), null, null, "", 0, 20}));
    }

    @Test
    void validateParameters_territoryPathWardCodeOver10Chars_reportsWardCode() throws Exception {
        SaleTerritoryController controller = new SaleTerritoryController(null, null, null);
        Method assign = method(SaleTerritoryController.class, "assign");
        Method unassign = method(SaleTerritoryController.class, "unassign");
        AssignTerritoryRequest body = new AssignTerritoryRequest(ULID);

        assertEquals(Set.of(), violatedParameters(controller, assign, new Object[]{"1234567890", body}));
        assertEquals(Set.of(0), violatedParameters(controller, assign, new Object[]{"12345678901", body}));
        assertEquals(Set.of(), violatedParameters(controller, unassign, new Object[]{"1234567890"}));
        assertEquals(Set.of(0), violatedParameters(controller, unassign, new Object[]{"x".repeat(1_000_000)}));
    }

    @Test
    void messages_ofEveryNewRule_areEnglishWithoutTheValue() {
        String value = "SECRETVALUE".repeat(3);
        Set<String> messages = VALIDATOR.validate(new AssignLeadRequest(value, 0L, null)).stream()
                .map(ConstraintViolation::getMessage).collect(Collectors.toSet());
        messages.addAll(VALIDATOR.validate(new AssignTerritoryRequest(value)).stream()
                .map(ConstraintViolation::getMessage).toList());

        assertEquals(2, messages.size(), messages.toString());
        messages.forEach(message -> assertFalse(message.contains("SECRETVALUE"), message));
        assertTrue(messages.stream().allMatch(message -> message.endsWith(".")), messages.toString());
    }

    // ==================== bulk assignment (acquisition-owner-lead-bulk-assign D-4) ====================

    /**
     * acquisition-owner-lead-bulk-assign BR-3, D-4: 1 to 20 items, each with a lead id of 1 to 26 characters and a
     * version of 0 or more; the assignee and note limits of one assignment. Spring reports the null item as
     * {@code leads[0]}; Hibernate Validator writes a container element node after it, so only the prefix is checked here.
     */
    @Test
    void validate_assignLeadsRequest_boundsOfLeadsAndItems() {
        String value = "SECRETVALUE";
        String longId = (value + "X".repeat(26)).substring(0, 27);
        String longNote = value + "n".repeat(2001 - value.length());

        assertEquals(List.of("leads"), paths(bulk(ULID, null, null)));
        assertEquals(List.of("leads"), paths(bulk(ULID, null, List.of())));
        assertEquals(List.of("leads"), paths(bulk(ULID, null, items(21))));
        assertEquals(List.of("leads[0].leadId"), paths(bulk(ULID, null, List.of(item(" ", 0L)))));
        assertEquals(List.of("leads[0].leadId"), paths(bulk(ULID, null, List.of(item(longId, 0L)))));
        assertEquals(List.of("leads[0].expectedVersion"), paths(bulk(ULID, null, List.of(item(ULID, null)))));
        assertEquals(List.of("leads[0].expectedVersion"), paths(bulk(ULID, null, List.of(item(ULID, -1L)))));
        assertEquals(List.of("assigneeUserId"), paths(bulk(" ", null, items(1))));
        assertEquals(List.of("assigneeUserId"), paths(bulk(longId, null, items(1))));
        assertEquals(List.of("note"), paths(bulk(ULID, longNote, items(1))));
        List<LeadVersionInput> withNull = new java.util.ArrayList<>();
        withNull.add(null);
        List<String> nullItem = paths(bulk(ULID, null, withNull));
        assertEquals(1, nullItem.size(), nullItem.toString());
        assertTrue(nullItem.get(0).startsWith("leads[0]"), nullItem.toString());

        assertEquals(List.of(), paths(bulk(ULID, "n".repeat(2000), items(20))));

        List<AssignLeadsRequest> refused = List.of(bulk(longId, longNote, List.of(item(longId, -1L))),
                bulk(value, null, List.of(item(value + " ".repeat(26), 0L))));
        refused.forEach(request -> VALIDATOR.validate(request)
                .forEach(v -> assertFalse(v.getMessage().contains(value), v.getMessage())));
    }

    // ==================== helpers ====================

    private static AssignLeadsRequest bulk(String assigneeUserId, String note, List<LeadVersionInput> leads) {
        return new AssignLeadsRequest(assigneeUserId, note, leads);
    }

    private static LeadVersionInput item(String leadId, Long expectedVersion) {
        return new LeadVersionInput(leadId, expectedVersion);
    }

    /** Distinct lead ids of 26 characters, version 0. */
    private static List<LeadVersionInput> items(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> item(String.format("01JLEAD%019d", i), 0L))
                .toList();
    }

    private static <T> List<String> paths(T request) {
        return VALIDATOR.validate(request).stream().map(v -> v.getPropertyPath().toString()).sorted().toList();
    }

    private static ChangeLeadStatusRequest statusChange(Long expectedVersion, String duplicateOf, Instant next) {
        return new ChangeLeadStatusRequest(OwnerLeadStatus.CONTACTED, expectedVersion, null, duplicateOf, null, next);
    }

    private static <T> Set<String> fields(T request) {
        return fieldsOf(VALIDATOR.validate(request));
    }

    private static <T> Set<String> fieldsOf(Set<ConstraintViolation<T>> violations) {
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    private static <T> Set<Integer> violatedParameters(T controller, Method method, Object[] args) {
        return VALIDATOR.forExecutables().validateParameters(controller, method, args).stream()
                .map(v -> {
                    Path.ParameterNode parameter = null;
                    for (Path.Node node : v.getPropertyPath()) {
                        if (node.getKind() == jakarta.validation.ElementKind.PARAMETER) {
                            parameter = node.as(Path.ParameterNode.class);
                        }
                    }
                    return parameter == null ? -1 : parameter.getParameterIndex();
                })
                .collect(Collectors.toSet());
    }

    private static Method method(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
