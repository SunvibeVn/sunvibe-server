package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;

/**
 * Shared helpers of the checklist API tests (survey-checklist spec 7.1), on top of {@link SurveyApiTestSupport}: a
 * VISITED appointment made through the W15 API (booked, moved a day back with {@code shiftBack}, then the own visit
 * record), start, take over, read and checkpoint calls, the lead moves that make a draft read-only, and SQL reads of
 * the rows a command must or must not write. Group saves send a group's content as read with the server fields taken
 * out ({@link #input}) and edited by the test; the JSON builders write wrappers and values as spec 7.2 names them.
 * Cleanup goes through {@link SurveyApiTestSupport#cleanUp}.
 */
public final class ChecklistApiTestSupport {

	public static final String CHECKLISTS = "/api/v1/staff/survey-checklists";
	public static final List<String> GROUPS = List.of("identification", "parts", "dimensions", "condition", "safety",
			"amenities", "terms");
	/** Keys only the server writes into a group's content (spec 7.2): a request that sends one back is refused. */
	private static final Set<String> SERVER_KEYS = Set.of("meta", "fieldMeta", "difference", "provinceName",
			"wardName", "receivedByName");

	private final SurveyApiTestSupport survey;
	private final JdbcTemplate jdbc;
	private final Supplier<String> ids;

	/** A lead with its VISITED appointment: the arrival lies about a day back. */
	public record Visit(String leadId, String appointmentId, Instant arrivedAt) {

		/** The Vietnam date of the arrival. */
		public LocalDate arrivalDate() {
			return SurveyTimeRules.vietnamDate(arrivedAt);
		}
	}

	/**
	 * @param ids ULIDs for the client ids of new spaces, hazards and other items (spec A-23)
	 */
	public ChecklistApiTestSupport(SurveyApiTestSupport survey, JdbcTemplate jdbc, Supplier<String> ids) {
		this.survey = survey;
		this.jdbc = jdbc;
		this.ids = ids;
	}

	/** A new client id. */
	public String newId() {
		return ids.get();
	}

	public AcquisitionApiTestSupport api() {
		return survey.api();
	}

	public SurveyApiTestSupport survey() {
		return survey;
	}

	/** A CONTACTED lead of the assignee, booked and visited by the assignee: SURVEY_SCHEDULED with a current visit. */
	public Visit visited(TestUser leader, TestUser assignee) throws Exception {
		return visited(leader, assignee, "LONG_TERM_RENT", AcquisitionApiTestSupport.price(12_000_000, "MONTH"));
	}

	/** As {@link #visited(TestUser, TestUser)} for a lead of the intent and price. */
	public Visit visited(TestUser leader, TestUser assignee, String intent, Map<String, Object> priceOrNull)
			throws Exception {
		String leadId = survey.lead(leader, assignee, "CONTACTED", intent, priceOrNull);
		return visit(assignee, leadId);
	}

	/** Books a survey of the lead (CONTACTED) for the caller and records the caller's own visit a day back. */
	public Visit visit(TestUser surveyor, String leadId) throws Exception {
		return visit(surveyor, leadId, Duration.ofDays(2));
	}

	/** As {@link #visit(TestUser, String)}, the appointment moved back by {@code back} from a day ahead. */
	public Visit visit(TestUser surveyor, String leadId, Duration back) throws Exception {
		Instant start = SurveyApiTestSupport.startIn(24);
		String appointmentId = survey.booked(surveyor, leadId, start);
		survey.shiftBack(appointmentId, back);
		Instant arrivedAt = start.minus(back);
		Result visited = recordVisit(surveyor, leadId, appointmentId, arrivedAt);
		assertEquals(200, visited.status(), visited.rawBody());
		return new Visit(leadId, appointmentId, arrivedAt);
	}

	/** The caller's own visit record of the appointment. */
	public Result recordVisit(TestUser surveyor, String leadId, String appointmentId, Instant arrivedAt)
			throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedLeadVersion", survey.version(leadId));
		body.put("expectedAppointmentVersion", survey.appointmentVersion(appointmentId));
		body.put("arrivedAt", arrivedAt.toString());
		body.put("note", "Met the owner");
		body.put("performedByUserId", surveyor.userId());
		return api().post(surveyor, SurveyApiTestSupport.APPOINTMENTS + "/" + appointmentId + "/visit", body);
	}

	// ==================== checklist calls ====================

	/** Starts with the intake (null: none); asserts nothing. */
	public Result start(TestUser caller, String appointmentId, Map<String, Object> intakeOrNull) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("intake", intakeOrNull);
		return api().post(caller, SurveyApiTestSupport.APPOINTMENTS + "/" + appointmentId + "/checklists", body);
	}

	/** Starts without an intake and returns the new checklist's id (asserts 201). */
	public String started(TestUser caller, String appointmentId) throws Exception {
		Result result = start(caller, appointmentId, null);
		assertEquals(201, result.status(), result.rawBody());
		return result.body().path("checklist").path("id").asString();
	}

	public Result read(TestUser caller, String checklistId) throws Exception {
		return api().get(caller, CHECKLISTS + "/" + checklistId);
	}

	/** Reads and asserts 200. */
	public Result readOk(TestUser caller, String checklistId) throws Exception {
		Result result = read(caller, checklistId);
		assertEquals(200, result.status(), result.rawBody());
		return result;
	}

	/** Takes over with the stored version and a basis dated on the arrival day; asserts nothing. */
	public Result takeOver(TestUser caller, String checklistId, Visit visit) throws Exception {
		return takeOver(caller, checklistId, checklistVersion(checklistId), "New assignee",
				visit.arrivalDate(), "Leader handed it over");
	}

	public Result takeOver(TestUser caller, String checklistId, long expectedVersion, String reason,
			LocalDate confirmedOn, String text) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedVersion", expectedVersion);
		body.put("reason", reason);
		body.put("basis", basis(confirmedOn, text));
		return api().post(caller, CHECKLISTS + "/" + checklistId + "/takeovers", body);
	}

	public Result checkpoint(TestUser caller, String checklistId, int takeoverNo) throws Exception {
		return api().get(caller, CHECKLISTS + "/" + checklistId + "/takeovers/" + takeoverNo);
	}

	// ==================== group saves ====================

	/** Sends a group save; {@code otherGroupVersions} only with a non-null map (identification only). */
	public Result put(TestUser caller, String checklistId, String group, long expectedVersion, JsonNode content,
			Map<String, Object> correctionOrNull, Map<String, Object> otherGroupVersionsOrNull) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedVersion", expectedVersion);
		if (otherGroupVersionsOrNull != null) {
			body.put("otherGroupVersions", otherGroupVersionsOrNull);
		}
		body.put("correction", correctionOrNull);
		body.put("content", content);
		return api().put(caller, CHECKLISTS + "/" + checklistId + "/" + group, body);
	}

	/** Reads as the caller, edits the group's input and saves it with the version read; asserts nothing. */
	public Result save(TestUser caller, String checklistId, String group, Consumer<ObjectNode> edit)
			throws Exception {
		return save(caller, checklistId, group, null, edit);
	}

	/**
	 * As {@link #save(TestUser, String, String, Consumer)} with a correction; a G1 save sends the versions read of
	 * every other group as {@code otherGroupVersions}.
	 */
	public Result save(TestUser caller, String checklistId, String group, Map<String, Object> correctionOrNull,
			Consumer<ObjectNode> edit) throws Exception {
		JsonNode checklist = readOk(caller, checklistId).body();
		ObjectNode content = input(checklist, group);
		edit.accept(content);
		return put(caller, checklistId, group, version(checklist, group), content, correctionOrNull,
				"identification".equals(group) ? otherVersions(checklist) : null);
	}

	/** Saves and asserts 200; the answer is the checklist. */
	public JsonNode saved(TestUser caller, String checklistId, String group, Consumer<ObjectNode> edit)
			throws Exception {
		return saved(caller, checklistId, group, null, edit);
	}

	public JsonNode saved(TestUser caller, String checklistId, String group, Map<String, Object> correctionOrNull,
			Consumer<ObjectNode> edit) throws Exception {
		Result result = save(caller, checklistId, group, correctionOrNull, edit);
		assertEquals(200, result.status(), result.rawBody());
		return result.body();
	}

	/** The group's content of a read as an input: the server-written keys taken out at every depth. */
	public static ObjectNode input(JsonNode checklist, String group) {
		ObjectNode content = (ObjectNode) checklist.path("groups").path(group).path("content").deepCopy();
		strip(content);
		return content;
	}

	public static long version(JsonNode checklist, String group) {
		return checklist.path("groups").path(group).path("version").asLong();
	}

	/** The versions of the six groups after G1, as a G1 save sends them. */
	public static Map<String, Object> otherVersions(JsonNode checklist) {
		Map<String, Object> versions = new LinkedHashMap<>();
		for (String group : GROUPS.subList(1, GROUPS.size())) {
			versions.put(group, version(checklist, group));
		}
		return versions;
	}

	public static Map<String, Object> correction(String reason, LocalDate confirmedOn, String text) {
		Map<String, Object> correction = new LinkedHashMap<>();
		correction.put("reason", reason);
		correction.put("basis", basis(confirmedOn, text));
		return correction;
	}

	/** Sets the node at a dotted path ({@code spaces.0.area}) of the content. */
	public static void set(ObjectNode content, String path, JsonNode value) {
		String[] parts = path.split("\\.");
		JsonNode parent = content;
		for (int i = 0; i < parts.length - 1; i++) {
			parent = parts[i].matches("\\d+") ? parent.path(Integer.parseInt(parts[i])) : parent.path(parts[i]);
		}
		String last = parts[parts.length - 1];
		if (parent instanceof ArrayNode array) {
			array.set(Integer.parseInt(last), value);
		} else {
			((ObjectNode) parent).set(last, value);
		}
	}

	private static void strip(JsonNode node) {
		if (node instanceof ObjectNode object) {
			SERVER_KEYS.forEach(object::remove);
		}
		node.forEach(ChecklistApiTestSupport::strip);
	}

	// ==================== JSON builders ====================

	public ObjectNode object() {
		return api().json().createObjectNode();
	}

	public JsonNode tree(Object value) {
		return api().json().valueToTree(value);
	}

	/** An unsourced wrapper holding the value. */
	public ObjectNode value(Object value) {
		ObjectNode wrapper = object();
		wrapper.put("state", "VALUE");
		wrapper.set("value", tree(value));
		wrapper.putNull("naReason");
		return wrapper;
	}

	/** A sourced wrapper with a source that is not an owner statement. */
	public ObjectNode sourced(Object value, String source) {
		ObjectNode wrapper = value(value);
		wrapper.put("source", source);
		wrapper.putNull("confirmedOn");
		wrapper.putNull("receivedByUserId");
		wrapper.putNull("relayBasis");
		return wrapper;
	}

	/** A sourced wrapper with an owner statement. */
	public ObjectNode ownerStated(Object value, LocalDate confirmedOn, String receivedByUserId,
			String relayBasisOrNull) {
		ObjectNode wrapper = sourced(value, "OWNER_STATED");
		wrapper.put("confirmedOn", confirmedOn.toString());
		wrapper.put("receivedByUserId", receivedByUserId);
		wrapper.put("relayBasis", relayBasisOrNull);
		return wrapper;
	}

	public ObjectNode unknown(boolean sourced) {
		ObjectNode wrapper = object();
		wrapper.put("state", "UNKNOWN");
		wrapper.putNull("value");
		wrapper.putNull("naReason");
		if (sourced) {
			wrapper.putNull("source");
			wrapper.putNull("confirmedOn");
			wrapper.putNull("receivedByUserId");
			wrapper.putNull("relayBasis");
		}
		return wrapper;
	}

	public ObjectNode notApplicable(String reason, boolean sourced) {
		ObjectNode wrapper = unknown(sourced);
		wrapper.put("state", "NOT_APPLICABLE");
		wrapper.put("naReason", reason);
		return wrapper;
	}

	/** An address in Đà Nẵng (province 48); a null pin is no pin. */
	public ObjectNode place(String addressText, String wardCode, BigDecimal latitude, BigDecimal longitude) {
		ObjectNode place = object();
		place.put("addressText", addressText);
		place.put("provinceCode", "48");
		place.put("wardCode", wardCode);
		place.put("latitude", latitude);
		place.put("longitude", longitude);
		return place;
	}

	public ObjectNode space(String spaceId, String code) {
		ObjectNode space = object();
		space.put("spaceId", spaceId);
		space.put("code", code);
		space.put("location", "Ground floor");
		space.putNull("description");
		return space;
	}

	public ObjectNode money(long amount) {
		ObjectNode money = object();
		money.put("amount", amount);
		money.put("currency", "VND");
		return money;
	}

	public ObjectNode rent(long amount) {
		ObjectNode rent = money(amount);
		rent.put("unit", "MONTH");
		return rent;
	}

	/** A charge: AMOUNT with an amount and unit, or another basis without them. */
	public ObjectNode charge(String basis, Long amountOrNull, String chargeUnitOrNull) {
		ObjectNode charge = object();
		charge.put("basis", basis);
		charge.set("amount", amountOrNull == null ? null : money(amountOrNull));
		charge.put("chargeUnit", chargeUnitOrNull);
		charge.putNull("note");
		return charge;
	}

	public ObjectNode areaCheck(String result, Boolean noneObserved, String note, String notEnteredReason) {
		ObjectNode check = object();
		check.put("result", result);
		check.put("noneObserved", noneObserved);
		check.put("note", note);
		check.put("notEnteredReason", notEnteredReason);
		return check;
	}

	/** An open hazard of the property (no space, no link). */
	public ObjectNode hazard(String hazardId, String severity, String status, String statusNoteOrNull) {
		ObjectNode hazard = object();
		hazard.put("hazardId", hazardId);
		hazard.putNull("spaceId");
		hazard.putNull("previous");
		hazard.put("description", "Loose balcony railing");
		hazard.put("severity", severity);
		hazard.put("status", status);
		hazard.put("statusNote", statusNoteOrNull);
		hazard.putNull("ownerReportedOn");
		hazard.putNull("receivedByUserId");
		hazard.putNull("relayBasis");
		return hazard;
	}

	/** A complete G1 input: an address seen on site in the ward, a house in scope, the scope and the spaces. */
	public ObjectNode identification(String scope, ObjectNode... spaces) {
		ObjectNode content = object();
		content.set("observedAddress", sourced(place("12 Bạch Đằng", SurveyApiTestSupport.WARD, null, null),
				"OBSERVED_ON_SITE"));
		content.putNull("differenceNote");
		content.set("propertyType", value("HOUSE"));
		ObjectNode fit = object();
		fit.put("assessment", "IN_SCOPE");
		fit.put("reason", "A whole house for long-term rent");
		content.set("fit", value(fit));
		content.put("rentalScope", scope);
		ArrayNode list = content.putArray("spaces");
		for (ObjectNode space : spaces) {
			list.add(space);
		}
		content.putNull("otherPartsNote");
		content.putNull("generalNote");
		return content;
	}

	public static Map<String, Object> intake(String reason, LocalDate confirmedOn, String text) {
		Map<String, Object> intake = new LinkedHashMap<>();
		intake.put("reason", reason);
		intake.put("basis", basis(confirmedOn, text));
		return intake;
	}

	public static Map<String, Object> basis(LocalDate confirmedOn, String text) {
		Map<String, Object> basis = new LinkedHashMap<>();
		basis.put("confirmedOn", confirmedOn == null ? null : confirmedOn.toString());
		basis.put("text", text);
		return basis;
	}

	// ==================== lead moves ====================

	/** Reassigns the lead with the staff endpoint (asserts 200). */
	public void reassign(TestUser leader, String leadId, TestUser to) throws Exception {
		SurveyApiTestSupport.ok(api().post(leader, SurveyApiTestSupport.LEADS + "/" + leadId + "/assign",
				Map.of("assigneeUserId", to.userId(), "expectedVersion", survey.version(leadId))));
	}

	/**
	 * Leaves the user only SALES_MANAGER: still staff who may call the checklist endpoints (roles are read on every
	 * request), but no longer someone who can take leads, so not eligible (survey-checklist spec 4.4).
	 */
	public void makeIneligible(TestUser user) {
		jdbc.update("delete from user_roles where user_id = ?", user.userId());
		jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where name = 'SALES_MANAGER'",
				user.userId());
	}

	// ==================== stored rows ====================

	public long checklistVersion(String checklistId) {
		return jdbc.queryForObject("select version from survey_checklists where id = ?", Long.class, checklistId);
	}

	/** The lead's History rows of the type, oldest first, with their reference columns and texts. */
	public List<Map<String, Object>> activities(String leadId, String type) {
		return jdbc.queryForList("select actor_user_id, reference_id, reference_no, reference_detail, note, from_status,"
				+ " to_status from owner_lead_activities where lead_id = ? and type = ? order by occurred_at, id",
				leadId, type);
	}

	/** The lead's security events, oldest first. */
	public List<Map<String, Object>> securityEvents(String leadId) {
		return jdbc.queryForList("select user_id, resource_type, resource_id, action, outcome, correlation_id from "
				+ "survey_security_events where lead_id = ? order by occurred_at, id", leadId);
	}

	/** The lead's survey access-log rows as "resource type:resource id", oldest first. */
	public List<String> accessLogs(String leadId) {
		return jdbc.queryForList("select resource_type || ':' || resource_id from survey_access_logs where lead_id = ? "
				+ "order by accessed_at, id", String.class, leadId);
	}

	public int rows(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}
}
