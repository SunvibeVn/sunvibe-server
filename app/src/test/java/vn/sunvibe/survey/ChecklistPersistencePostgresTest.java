package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManagerFactory;
import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.survey.core.domain.exception.ChecklistAlreadyStartedException;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentDetails;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentKind;
import vn.sunvibe.survey.core.domain.model.appointment.Basis;
import vn.sunvibe.survey.core.domain.model.appointment.Coordinates;
import vn.sunvibe.survey.core.domain.model.appointment.LeadLocationSnapshot;
import vn.sunvibe.survey.core.domain.model.appointment.MeetingLocation;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyIntent;
import vn.sunvibe.survey.core.domain.model.appointment.VisitRecordKind;
import vn.sunvibe.survey.core.domain.model.checklist.AmenityCode;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSave;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSnapshot;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistTakeover;
import vn.sunvibe.survey.core.domain.model.checklist.Correction;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsContent;
import vn.sunvibe.survey.core.domain.model.checklist.DimensionsSpace;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.checklist.TakeoverKind;
import vn.sunvibe.survey.core.domain.model.checklist.YesNo;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupDocument;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;
import vn.sunvibe.survey.core.domain.model.checklist.unit.Tombstone;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitKey;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitMeta;
import vn.sunvibe.survey.core.domain.model.log.AccessResourceType;
import vn.sunvibe.survey.core.domain.model.log.SurveyAccessLog;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.model.security.SecurityOutcome;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.model.security.SurveySecurityEvent;
import vn.sunvibe.survey.core.domain.policy.checklist.CorrectedUnit;
import vn.sunvibe.survey.core.domain.repository.SurveyAccessLogDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyAppointmentDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistGroupDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistSaveDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistTakeoverDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveySecurityEventDomainRepository;

/**
 * The checklist tables built by Flyway and the checklist adapters on a real PostgreSQL (survey-checklist spec 5.2,
 * 5.5): documents with metadata and tombstones round-trip; both unique indexes become "already started"; the CHECKs
 * refuse broken rows; the group locks are the PostgreSQL row locks the save order relies on; a read loads the seven
 * groups in one statement. Needs Docker.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.generate_statistics=true" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class ChecklistPersistencePostgresTest {

	private static final String LEAD = "01JLEADCHECK1AAAAAAAAAAAAA";
	private static final String OTHER_LEAD = "01JLEADCHECK2AAAAAAAAAAAAA";
	private static final String SALE_A = "01JSALEAAAAAAAAAAAAAAAAAAA";
	private static final String SALE_B = "01JSALEBBBBBBBBBBBBBBBBBBB";
	private static final Instant BOOKED_AT = Instant.parse("2026-10-23T03:05:30.123456Z");
	private static final Instant START = Instant.parse("2026-10-24T03:00:00Z");
	private static final Instant NOW = Instant.parse("2026-10-24T05:00:00.654321Z");
	private static final String SPACE = "01JSPACE000000000000000001";
	private static final String LOCK_NOT_AVAILABLE = "55P03";
	/** A G4 document with a scope tombstone exactly as the W16a wave commit 6d5d53f wrote it (schema 1). */
	private static final String SCHEMA_1_CONDITION = "{\"schema\":1,\"content\":{\"sharedKitchen\":null,"
			+ "\"sharedBathroom\":null,\"spaces\":[]},\"meta\":{},\"tombstones\":{\"condition/sharedKitchen\":{"
			+ "\"enteredByUserId\":\"" + SALE_A + "\",\"enteredAt\":\"2026-10-20T05:00:00.123456Z\","
			+ "\"observedByUserId\":\"" + SALE_A + "\",\"observedOn\":\"2026-10-20\",\"formField\":null,"
			+ "\"lastSource\":null,\"correctionSaveId\":7}},\"place\":null}";

	@Autowired
	private SurveyAppointmentDomainRepository appointments;

	@Autowired
	private SurveyChecklistDomainRepository checklists;

	@Autowired
	private SurveyChecklistGroupDomainRepository groups;

	@Autowired
	private SurveyChecklistTakeoverDomainRepository takeovers;

	@Autowired
	private SurveyChecklistSaveDomainRepository saves;

	@Autowired
	private SurveySecurityEventDomainRepository securityEvents;

	@Autowired
	private SurveyAccessLogDomainRepository accessLogs;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@BeforeEach
	@AfterEach
	void cleanSurveyTables() {
		for (String table : List.of("survey_security_events", "survey_checklist_saves", "survey_checklist_takeovers",
				"survey_checklist_groups", "survey_checklists", "survey_access_logs", "survey_resumptions",
				"survey_appointment_events", "survey_appointment_revisions", "survey_appointments")) {
			jdbc.update("DELETE FROM " + table);
		}
	}

	// ==================== round trip ====================

	@Test
	void roundTrip_everyGroupWithMetaAndTombstones() {
		SurveyChecklist checklist = started(visited(LEAD, 1), 1);
		assertEquals(0L, checklist.getVersion());
		UnitKey area = UnitKey.spaceField(ChecklistGroup.DIMENSIONS, SPACE, "area");
		UnitMeta meta = new UnitMeta(SALE_A, NOW, SALE_A, LocalDate.parse("2026-10-24"), null,
				ChecklistSource.MEASURED, 77L);
		GroupDocument document = new GroupDocument(ChecklistGroup.DIMENSIONS, new DimensionsContent(List.of(
				new DimensionsSpace(SPACE, SourcedField.of(new BigDecimal("45.5"), ChecklistSource.MEASURED),
						SourcedField.of(2, ChecklistSource.OBSERVED_ON_SITE), SourcedField.unknown(),
						SourcedField.of(YesNo.NO, ChecklistSource.OBSERVED_ON_SITE), "Phòng ngủ hướng đông"))),
				Map.of(area, meta), Map.of(UnitKey.field(ChecklistGroup.CONDITION, "sharedKitchen"),
						Tombstone.of(meta)));

		GroupState written = inTransaction(() -> {
			groups.lockForUpdate(checklist.getId(), ChecklistGroup.DIMENSIONS);
			return groups.update(checklist.getId(), ChecklistGroup.DIMENSIONS, 0L,
					new GroupState(document, 0L, NOW, SALE_A));
		});
		Map<ChecklistGroup, GroupState> stored = groups.findAll(checklist.getId());

		assertEquals(1L, written.version());
		assertEquals(7, stored.size());
		assertEquals(new GroupState(document, 1L, NOW, SALE_A), stored.get(ChecklistGroup.DIMENSIONS));
		assertEquals(new GroupState(GroupDocument.initial(ChecklistGroup.TERMS), 0L, null, null),
				stored.get(ChecklistGroup.TERMS));
		SurveyChecklist reloaded = checklists.findById(checklist.getId()).orElseThrow();
		assertEquals(checklist.snapshot(), reloaded.snapshot());
		assertEquals(1, reloaded.getChecklistNo());
		assertEquals(LEAD, reloaded.getLeadId());
		assertEquals(List.of(checklist.getId()), checklists.findByLeadId(LEAD).stream().map(SurveyChecklist::getId)
				.toList());
		assertEquals(2, checklists.nextChecklistNo(LEAD));
		assertEquals(1, checklists.nextChecklistNo(OTHER_LEAD));
	}

	@Test
	void takeoverSaveEventAndAccessLog_roundTrip() {
		SurveyChecklist checklist = started(visited(LEAD, 1), 1);
		Map<ChecklistGroup, GroupState> before = groups.findAll(checklist.getId());
		Basis basis = new Basis(LocalDate.parse("2026-10-24"), "Bàn giao từ Sale A");
		ChecklistTakeover intake = ChecklistTakeover.startByOther(9001L, checklist.getId(), SALE_A, "Nhận lại",
				basis, NOW);
		ChecklistTakeover takeover = new ChecklistTakeover(9002L, checklist.getId(), 2, TakeoverKind.TAKEOVER,
				SALE_A, SALE_B, "Được giao lại", basis, new ChecklistSnapshot(before), NOW.plusSeconds(60));
		takeovers.save(intake);
		takeovers.save(takeover);
		Correction correction = new Correction("Đo lại", basis, List.of(new CorrectedUnit(
				"identification/spaces/" + SPACE, "P101"), new CorrectedUnit("terms/common/water", null)));
		saves.save(new ChecklistSave(9101L, checklist.getId(), ChecklistGroup.IDENTIFICATION, 1L, SALE_B, NOW,
				correction));
		saves.save(new ChecklistSave(9102L, checklist.getId(), ChecklistGroup.DIMENSIONS, 1L, SALE_B, NOW, null));
		securityEvents.save(new SurveySecurityEvent(9201L, SALE_B, LEAD, SecurityResourceType.CHECKPOINT,
				checklist.getId().getValue() + ":2", SecurityAction.READ, SecurityOutcome.NOT_VISIBLE, "corr-1", NOW));
		accessLogs.save(SurveyAccessLog.create(9301L, LEAD, SALE_B, NOW, AccessResourceType.CHECKLIST,
				checklist.getId().getValue()));
		accessLogs.save(SurveyAccessLog.create(9302L, LEAD, SALE_B, NOW.plusSeconds(1)));

		assertEquals(List.of(intake, takeover), takeovers.findByChecklistId(checklist.getId()));
		assertEquals(takeover, takeovers.find(checklist.getId(), 2).orElseThrow());
		assertEquals(3, takeovers.findNextTakeoverNo(checklist.getId()));
		assertEquals(before, takeovers.find(checklist.getId(), 2).orElseThrow().snapshotOrNull().groups());
		assertEquals(correction, saves.findByChecklistId(checklist.getId()).get(0).correctionOrNull());
		assertNull(saves.findByChecklistId(checklist.getId()).get(1).correctionOrNull());
		assertEquals("corr-1", securityEvents.findByLeadId(LEAD).get(0).correlationIdOrNull());
		List<SurveyAccessLog> logs = accessLogs.findByLeadId(LEAD);
		assertEquals(AccessResourceType.CHECKLIST, logs.get(0).resourceTypeOrNull());
		assertEquals(AccessResourceType.SURVEY_BLOCK, logs.get(1).resourceTypeOrNull());
		assertEquals(LEAD, logs.get(1).resourceIdOrNull());
	}

	// ==================== stored schema (spec A-29, plan PD-1) ====================

	/** spec A-29: every tombstone form is written and read back in a new transaction, equal; written as schema 2. */
	@Test
	void tombstones_surviveWriteAndRead() {
		SurveyChecklist checklist = started(visited(LEAD, 1), 1);
		Tombstone scope = new Tombstone(SALE_B, NOW, SALE_A, LocalDate.parse("2026-10-24"), null, null, 77L, null);
		Tombstone item = new Tombstone(SALE_B, NOW, SALE_B, LocalDate.parse("2026-10-22"), null,
				ChecklistSource.OWNER_STATED, 78L, null);
		Tombstone hazard = new Tombstone(SALE_B, NOW, SALE_A, LocalDate.parse("2026-10-24"), null, null, null,
				new Tombstone.OwnerReportFacts(SALE_B, LocalDate.parse("2026-10-23")));
		Map<ChecklistGroup, GroupDocument> documents = Map.of(
				ChecklistGroup.CONDITION, tombstoned(ChecklistGroup.CONDITION, Map.of(UnitKey.field(
						ChecklistGroup.CONDITION, "sharedKitchen"), scope, UnitKey.override(SPACE, "water"), scope)),
				ChecklistGroup.AMENITIES, tombstoned(ChecklistGroup.AMENITIES, Map.of(
						UnitKey.sharedItem(AmenityCode.AIR_CONDITIONER), item,
						UnitKey.spaceItem(SPACE, AmenityCode.BED), item,
						UnitKey.otherItem("01JTEM000000000000000000X1"), item)),
				ChecklistGroup.SAFETY, tombstoned(ChecklistGroup.SAFETY, Map.of(
						UnitKey.hazard("01JHAZARD00000000000000002"), hazard)));

		inTransaction(() -> {
			documents.forEach((group, document) -> {
				groups.lockForUpdate(checklist.getId(), group);
				groups.update(checklist.getId(), group, 0L, new GroupState(document, 0L, NOW, SALE_B));
			});
			return null;
		});
		Map<ChecklistGroup, GroupState> read = inTransaction(() -> groups.findAll(checklist.getId()));

		documents.forEach((group, document) -> assertEquals(document, read.get(group).document(), group.name()));
		assertTrue(jdbc.queryForObject("SELECT content FROM survey_checklist_groups WHERE checklist_id = ? AND "
				+ "group_key = 'SAFETY'", String.class, checklist.getId().getValue()).startsWith("{\"schema\":2,"));
	}

	/**
	 * spec A-29, risk 9.2: a group row and a takeover checkpoint as the W16a wave wrote them (schema 1) read through
	 * the adapters with no report facts, and nothing is rewritten.
	 */
	@Test
	void schema1Rows_readThroughAdapters() {
		SurveyChecklist checklist = started(visited(LEAD, 1), 1);
		String id = checklist.getId().getValue();
		jdbc.update("UPDATE survey_checklist_groups SET content = ? WHERE checklist_id = ? AND group_key = 'CONDITION'",
				SCHEMA_1_CONDITION, id);
		StringBuilder snapshot = new StringBuilder("{\"schema\":1,\"groups\":{");
		for (ChecklistGroup group : ChecklistGroup.stored()) {
			// a document without tombstones has the same layout in schema 1 and 2, apart from the number
			String document = group == ChecklistGroup.CONDITION ? SCHEMA_1_CONDITION : jdbc.queryForObject(
					"SELECT content FROM survey_checklist_groups WHERE checklist_id = ? AND group_key = ?",
					String.class, id, group.name()).replace("{\"schema\":2,", "{\"schema\":1,");
			snapshot.append(group == ChecklistGroup.IDENTIFICATION ? "" : ",").append('"').append(group.name())
					.append("\":{\"version\":0,\"updatedAt\":null,\"updatedByUserId\":null,\"document\":")
					.append(document).append('}');
		}
		snapshot.append("}}");
		jdbc.update("INSERT INTO survey_checklist_takeovers (id, checklist_id, takeover_no, kind, from_user_id, "
				+ "to_user_id, reason, basis_confirmed_on, basis_text, snapshot, occurred_at) VALUES (9003, ?, 1, "
				+ "'TAKEOVER', ?, ?, 'Được giao lại', DATE '2026-10-24', 'Bàn giao', ?, now())", id, SALE_A, SALE_B,
				snapshot.toString());
		GroupDocument expected = new GroupDocument(ChecklistGroup.CONDITION, GroupDocument.initial(
				ChecklistGroup.CONDITION).content(), Map.of(), Map.of(UnitKey.field(ChecklistGroup.CONDITION,
				"sharedKitchen"), new Tombstone(SALE_A, Instant.parse("2026-10-20T05:00:00.123456Z"), SALE_A,
				LocalDate.parse("2026-10-20"), null, null, 7L, null)));

		GroupDocument row = groups.findAll(checklist.getId()).get(ChecklistGroup.CONDITION).document();
		GroupDocument checkpoint = takeovers.find(checklist.getId(), 1).orElseThrow().snapshotOrNull().groups()
				.get(ChecklistGroup.CONDITION).document();

		assertEquals(expected, row);
		assertEquals(expected, checkpoint);
		assertNull(row.tombstone(UnitKey.field(ChecklistGroup.CONDITION, "sharedKitchen")).ownerReportOrNull());
		assertEquals(SCHEMA_1_CONDITION, jdbc.queryForObject("SELECT content FROM survey_checklist_groups WHERE "
				+ "checklist_id = ? AND group_key = 'CONDITION'", String.class, id));
	}

	/** spec A-10: access-log rows written before the checklist keep a null resource. */
	@Test
	void accessLogRowsOfW15_keepNullResourceColumns() {
		jdbc.update("INSERT INTO survey_access_logs (id, lead_id, user_id, accessed_at) VALUES (9401, ?, ?, now())",
				LEAD, SALE_A);

		SurveyAccessLog old = accessLogs.findByLeadId(LEAD).get(0);

		assertNull(old.resourceTypeOrNull());
		assertNull(old.resourceIdOrNull());
	}

	// ==================== unique indexes (spec I-2, 5.5) ====================

	@Test
	void insert_secondDraftForAppointment_throwsAlreadyStarted() {
		SurveyAppointment visit = visited(LEAD, 1);
		started(visit, 1);

		assertThrows(ChecklistAlreadyStartedException.class, () -> checklists.insert(SurveyChecklist.start(
				SurveyChecklistId.generate(), LEAD, 2, visit.getId(), 1, SALE_A, NOW)));
	}

	@Test
	void insert_sameChecklistNo_throwsAlreadyStarted() {
		started(visited(LEAD, 1), 1);
		SurveyAppointment second = visited(LEAD, 2);

		assertThrows(ChecklistAlreadyStartedException.class, () -> checklists.insert(SurveyChecklist.start(
				SurveyChecklistId.generate(), LEAD, 1, second.getId(), 1, SALE_A, NOW)));
	}

	@Test
	void update_staleVersion_failsOptimistically() {
		SurveyChecklist checklist = started(visited(LEAD, 1), 1);

		assertThrows(OptimisticLockingFailureException.class, () -> inTransaction(() -> groups.update(
				checklist.getId(), ChecklistGroup.PARTS, 5L, new GroupState(GroupDocument.initial(ChecklistGroup.PARTS),
						5L, NOW, SALE_A))));
		SurveyChecklist stale = checklists.findById(checklist.getId()).orElseThrow();
		SurveyChecklist fresh = checklists.findById(checklist.getId()).orElseThrow();
		fresh.takeOver(SALE_B);
		checklists.update(fresh);
		stale.takeOver(SALE_B);
		assertThrows(OptimisticLockingFailureException.class, () -> checklists.update(stale));
		assertEquals(1L, fresh.getVersion());
	}

	// ==================== CHECKs ====================

	@Test
	void checks_refuseBrokenRows() {
		SurveyChecklist checklist = started(visited(LEAD, 1), 1);
		String id = "'" + checklist.getId().getValue() + "'";

		assertRejected("INSERT INTO survey_checklist_groups (checklist_id, group_key, content, version) VALUES (" + id
				+ ", 'EVIDENCE', '{}', 0)", "chk_survey_checklist_groups_group_key");
		jdbc.update("DELETE FROM survey_checklist_groups WHERE group_key = 'PARTS'");
		assertRejected("INSERT INTO survey_checklist_groups (checklist_id, group_key, content, version) VALUES (" + id
				+ ", 'PARTS', '[]', 0)", "chk_survey_checklist_groups_content");
		assertRejected("INSERT INTO survey_checklist_takeovers (id, checklist_id, takeover_no, kind, from_user_id,"
				+ " to_user_id, reason, basis_confirmed_on, basis_text, occurred_at) VALUES (1, " + id + ", 1,"
				+ " 'START_BY_OTHER', '" + SALE_A + "', '" + SALE_B + "', 'r', DATE '2026-10-24', 'b', now())",
				"chk_survey_checklist_takeovers_from_user");
		assertRejected("INSERT INTO survey_checklist_saves (id, checklist_id, group_key, group_version, actor_user_id,"
				+ " saved_at, correction_reason) VALUES (2, " + id + ", 'PARTS', 1, '" + SALE_A + "', now(), 'r')",
				"chk_survey_checklist_saves_correction");
		assertRejected("INSERT INTO survey_security_events (id, user_id, lead_id, resource_type, resource_id, action,"
				+ " outcome, occurred_at) VALUES (3, '" + SALE_A + "', '" + LEAD + "', 'CHECKLIST', " + id + ", 'READ',"
				+ " 'OTHER', now())", "chk_survey_security_events_outcome");
		assertRejected("INSERT INTO survey_access_logs (id, lead_id, user_id, accessed_at, resource_type) VALUES (4, '"
				+ LEAD + "', '" + SALE_A + "', now(), 'CHECKLIST')", "chk_survey_access_logs_resource_pair");
		assertRejected("INSERT INTO survey_checklists (id, lead_id, checklist_no, visit_appointment_id, intent_cycle,"
				+ " holder_user_id, template_code, template_version, kind, status, created_by, created_at) VALUES"
				+ " ('01JCHECKBROKEN000000000001', '" + OTHER_LEAD + "', 1, (SELECT visit_appointment_id FROM"
				+ " survey_checklists LIMIT 1), 1, '" + SALE_A + "', 'LONG_TERM_RENT', 1, 'FULL_ASSESSMENT', 'SUBMITTED',"
				+ " '" + SALE_A + "', now())", "chk_survey_checklists_status");
	}

	// ==================== row locks (spec 5.5) ====================

	@Test
	void lockForShare_blocksUpdateNowait() {
		SurveyChecklist checklist = started(visited(LEAD, 1), 1);

		inTransaction(() -> {
			groups.lockForShare(checklist.getId(), ChecklistGroup.IDENTIFICATION);
			probe(other -> {
				execute(other, "SELECT 1 FROM survey_checklist_groups WHERE checklist_id = ? AND group_key ="
						+ " 'IDENTIFICATION' FOR SHARE NOWAIT", checklist.getId().getValue());
				SqlFailure refused = assertThrows(SqlFailure.class, () -> execute(other, "SELECT 1 FROM"
						+ " survey_checklist_groups WHERE checklist_id = ? AND group_key = 'IDENTIFICATION' FOR UPDATE"
						+ " NOWAIT", checklist.getId().getValue()));
				assertEquals(LOCK_NOT_AVAILABLE, refused.sqlState());
			});
			return null;
		});
	}

	@Test
	void lockForUpdate_blocksShareNowait() {
		SurveyChecklist checklist = started(visited(LEAD, 1), 1);

		inTransaction(() -> {
			GroupState state = groups.lockForUpdate(checklist.getId(), ChecklistGroup.DIMENSIONS);
			assertEquals(0L, state.version());
			probe(other -> {
				SqlFailure refused = assertThrows(SqlFailure.class, () -> execute(other, "SELECT 1 FROM"
						+ " survey_checklist_groups WHERE checklist_id = ? AND group_key = 'DIMENSIONS' FOR SHARE"
						+ " NOWAIT", checklist.getId().getValue()));
				assertEquals(LOCK_NOT_AVAILABLE, refused.sqlState());
			});
			return null;
		});
	}

	/** spec 5.5: a read loads all group rows in one statement, so a GET never shows half of a cascade. */
	@Test
	void findAll_readsSevenGroupsInOneStatement() {
		SurveyChecklist checklist = started(visited(LEAD, 1), 1);
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		Map<ChecklistGroup, GroupState> all = groups.findAll(checklist.getId());

		assertEquals(7, all.size());
		assertEquals(1, statistics.getPrepareStatementCount());
	}

	@Test
	void findLeadIdById_unknown_isEmpty() {
		SurveyChecklist checklist = started(visited(LEAD, 1), 1);

		assertEquals(LEAD, checklists.findLeadIdById(checklist.getId()).orElseThrow());
		assertTrue(checklists.findLeadIdById(SurveyChecklistId.generate()).isEmpty());
		assertEquals(LEAD, appointments.findLeadIdById(checklist.getVisitAppointmentId()).orElseThrow());
		assertTrue(appointments.findLeadIdById(SurveyAppointmentId.generate()).isEmpty());
	}

	// ==================== helpers ====================

	private SurveyAppointment visited(String leadId, int appointmentNo) {
		LeadLocationSnapshot place = new LeadLocationSnapshot("12 Bach Dang", "48", "Da Nang", "20194", "Hai Chau",
				new Coordinates(new BigDecimal("16.054407"), new BigDecimal("108.202167")));
		SurveyAppointment appointment = SurveyAppointment.book(SurveyAppointmentId.generate(), leadId, appointmentNo,
				AppointmentKind.FIRST, null, null, SurveyIntent.LONG_TERM_RENT, 1, place,
				AppointmentDetails.of(START, 60, MeetingLocation.fromLead(place), null, null, null), null, SALE_A,
				false, null, SALE_A, BOOKED_AT);
		appointment.recordVisit(START, "Đã đến", SALE_A, SALE_A, VisitRecordKind.OWN, null, null, NOW);
		appointments.save(appointment);
		return appointment;
	}

	private SurveyChecklist started(SurveyAppointment visit, int checklistNo) {
		SurveyChecklist checklist = SurveyChecklist.start(SurveyChecklistId.generate(), visit.getLeadId(), checklistNo,
				visit.getId(), 1, SALE_A, NOW);
		inTransaction(() -> {
			checklists.insert(checklist);
			Map<ChecklistGroup, GroupState> initial = new EnumMap<>(ChecklistGroup.class);
			for (ChecklistGroup group : ChecklistGroup.stored()) {
				initial.put(group, new GroupState(GroupDocument.initial(group), 0L, null, null));
			}
			groups.insertAll(checklist.getId(), initial);
			return null;
		});
		return checklist;
	}

	/** The group's empty content holding only the tombstones. */
	private static GroupDocument tombstoned(ChecklistGroup group, Map<UnitKey, Tombstone> tombstones) {
		return new GroupDocument(group, GroupDocument.initial(group).content(), Map.of(), tombstones);
	}

	private <T> T inTransaction(java.util.function.Supplier<T> work) {
		return new TransactionTemplate(transactionManager).execute(status -> work.get());
	}

	private void probe(Consumer<Connection> check) {
		try (Connection other = dataSource.getConnection()) {
			other.setAutoCommit(false);
			check.accept(other);
			other.rollback();
		} catch (SQLException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void execute(Connection connection, String sql, String id) {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, id);
			statement.executeQuery().close();
		} catch (SQLException e) {
			throw new SqlFailure(e);
		}
	}

	private void assertRejected(String sql, String constraint) {
		DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update(sql));
		assertTrue(thrown.getMessage().contains(constraint), "expected constraint " + constraint + ": "
				+ thrown.getMessage());
	}

	/** An unchecked carrier of the SQL failure, so the probe lambdas can assert its state. */
	private static final class SqlFailure extends RuntimeException {
		SqlFailure(SQLException cause) {
			super(cause);
		}

		String sqlState() {
			return ((SQLException) getCause()).getSQLState();
		}
	}
}
