package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.property.core.domain.exception.IntakeConflictException;
import vn.sunvibe.property.core.domain.model.property.enums.AssetType;
import vn.sunvibe.property.core.domain.model.property.enums.RentalLayout;
import vn.sunvibe.property.core.domain.model.intake.AvailabilityChoice;
import vn.sunvibe.property.core.domain.model.intake.AvailabilityItem;
import vn.sunvibe.property.core.domain.model.intake.CaseOutcome;
import vn.sunvibe.property.core.domain.model.intake.CaseSignal;
import vn.sunvibe.property.core.domain.model.intake.CaseStatus;
import vn.sunvibe.property.core.domain.model.intake.DuplicateCase;
import vn.sunvibe.property.core.domain.model.intake.IntakeSourceRef;
import vn.sunvibe.property.core.domain.model.intake.IntakeStatus;
import vn.sunvibe.property.core.domain.model.intake.IntakeSuspension;
import vn.sunvibe.property.core.domain.model.intake.IntakeTarget;
import vn.sunvibe.property.core.domain.model.intake.InventoryIntake;
import vn.sunvibe.property.core.domain.model.intake.OwnerCheckState;
import vn.sunvibe.property.core.domain.model.intake.OwnerProof;
import vn.sunvibe.property.core.domain.model.intake.OwnerRequest;
import vn.sunvibe.property.core.domain.model.intake.ProofBinding;
import vn.sunvibe.property.core.domain.model.intake.SelectedSpace;
import vn.sunvibe.property.core.domain.model.intake.SpaceUnitMapping;
import vn.sunvibe.property.core.domain.model.intake.TargetMode;
import vn.sunvibe.property.core.domain.model.intake.UnitMapping;
import vn.sunvibe.property.core.domain.repository.DuplicateCaseDomainRepository;
import vn.sunvibe.property.core.domain.repository.IntakeSuspensionDomainRepository;
import vn.sunvibe.property.core.domain.repository.IntakeTrailDomainRepository;
import vn.sunvibe.property.core.domain.repository.IntakeUnitMappingDomainRepository;
import vn.sunvibe.property.core.domain.repository.InventoryIntakeDomainRepository;
import vn.sunvibe.property.core.domain.repository.OwnerProofDomainRepository;
import vn.sunvibe.property.core.domain.repository.OwnerRequestDomainRepository;

/**
 * The intake rows through their repositories on a real PostgreSQL (property-create intake spec 5.2, 5.3): every field reads
 * back, an open row cannot be doubled, a read after a lock sees the latest commit, and the guarded counters count in SQL.
 * The annotations are those of {@link vn.sunvibe.FlywayPostgresMigrationTest}, so both share one context. Needs Docker.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class InventoryIntakeRepositoryPostgresTest {

	private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);
	private static final String SPACE = "01SPACEA00000000000000000A";
	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	private InventoryIntakeDomainRepository intakes;

	@Autowired
	private OwnerRequestDomainRepository requests;

	@Autowired
	private OwnerProofDomainRepository proofs;

	@Autowired
	private DuplicateCaseDomainRepository cases;

	@Autowired
	private IntakeSuspensionDomainRepository suspensions;

	@Autowired
	private IntakeUnitMappingDomainRepository mappings;

	@Autowired
	private IntakeTrailDomainRepository trail;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void repository_roundTrip_eachEntity() {
		String lead = id();
		InventoryIntake intake = inserted(lead, 1);

		InventoryIntake read = tx(() -> intakes.findById(intake.getId())).orElseThrow();
		assertEquals(IntakeStatus.DRAFT, read.getStatus());
		assertEquals(lead, read.getLeadId());
		assertEquals(1, read.getIntakeNo());
		assertEquals("a".repeat(64), read.getSource().sha256());
		assertEquals(RentalLayout.PARTS, read.getRentalScope());
		assertEquals(AssetType.WHOLE_BUILDING, read.getAssetType());
		assertEquals("20242", read.getSource().wardCode());
		assertEquals("1 bach dang", read.getSource().addressNorm());
		assertEquals(16.07, read.getSource().latitude());
		assertEquals(List.of(new SelectedSpace(SPACE, "P101", "Floor 1")), read.getSelection());
		assertEquals(NOW, read.getCreatedAt());

		OwnerRequest request = OwnerRequest.create(id(), intake.getId(), id(), "h".repeat(64), "{\"leadCode\":\"SVQ-1\"}",
				NOW.plusSeconds(600), id(), NOW);
		run(() -> requests.save(request));
		OwnerRequest readRequest = tx(() -> requests.findById(request.getId())).orElseThrow();
		assertEquals(request.getTargetUserId(), readRequest.getTargetUserId());
		assertEquals("h".repeat(64), readRequest.getCodeHash());
		assertEquals(NOW.plusSeconds(600), readRequest.getExpiresAt());
		assertTrue(readRequest.getDisplaySnapshot().contains("SVQ-1"));

		OwnerProof proof = OwnerProof.create(id(), intake.getId(), request.getId(), new ProofBinding(lead, 1,
				intake.getSourceChecklistId(), intake.scopeHash()), id(), "owner-link-2026-11", 3, NOW);
		proof.startCheck("p".repeat(64), NOW.plusSeconds(600), id(), "Called first", NOW);
		run(() -> proofs.save(proof));
		OwnerProof readProof = tx(() -> proofs.findById(proof.getId())).orElseThrow();
		assertEquals(proof.getBinding(), readProof.getBinding());
		assertEquals(3, readProof.getSignInLosses());
		assertEquals(OwnerCheckState.REQUIRED, readProof.getOwnerCheck());
		assertEquals("p".repeat(64), readProof.getPhraseHash());
		assertEquals("Called first", readProof.getOwnerCheckBasis());
		assertEquals(Optional.of(proof.getId()), tx(() -> proofs.findInForce(intake.getId())).map(OwnerProof::getId));

		DuplicateCase row = DuplicateCase.open(id(), intake.getId(), id(), null, "P:" + id(), intake.scopeHash(), null,
				CaseSignal.ADDRESS, NOW);
		assertTrue(tx(() -> cases.insertIfAbsent(row)));
		DuplicateCase readCase = tx(() -> cases.findById(row.getId())).orElseThrow();
		assertEquals(CaseStatus.OPEN, readCase.getStatus());
		assertEquals(CaseSignal.ADDRESS, readCase.getSignal());
		readCase.decide(CaseOutcome.SAME_ASSET, null, "The same house", id(), NOW);
		run(() -> cases.update(readCase));
		DuplicateCase decided = tx(() -> cases.findById(row.getId())).orElseThrow();
		assertEquals(CaseOutcome.SAME_ASSET, decided.getOutcome());
		assertEquals("The same house", decided.getBasis());
		assertEquals(1L, decided.getVersion());

		IntakeSuspension suspension = IntakeSuspension.create(id(), lead, null, null, "Data request", id(), NOW);
		run(() -> suspensions.save(suspension));
		suspension.release("Answered", id(), NOW);
		run(() -> suspensions.save(suspension));
		IntakeSuspension readSuspension = tx(() -> suspensions.findByLeadId(lead)).getFirst();
		assertFalse(readSuspension.isActive());
		assertEquals("Answered", readSuspension.getReleaseReason());

		UnitMapping mapping = new UnitMapping(intake.getId(), SPACE, "P101", id(), true);
		run(() -> mappings.saveAll(List.of(mapping)));
		assertEquals(List.of(mapping), tx(() -> mappings.findByIntakeId(intake.getId())));

		String actor = id();
		run(() -> {
			trail.recordEvent(intake.getId(), actor, "SELECTION", NOW);
			trail.recordAccess(actor, lead, intake.getId(), null, "BLOCK_READ", NOW);
		});
		assertEquals(Set.of(actor), tx(() -> trail.actorsOfLead(lead)));
		assertEquals(1, jdbc.queryForObject("select count(*) from inventory_access_logs where lead_id = ?", Integer.class,
				lead));
	}

	/** Every stored field of an intake survives, the JSON columns included. */
	@Test
	void repository_intakeWorkSurvives() {
		InventoryIntake intake = inserted(id(), 1);
		intake.awaitOwner(NOW);
		intake.confirmOwner(NOW);
		intake.target(new IntakeTarget(TargetMode.LINK, id(), null, List.of(new SpaceUnitMapping(SPACE, id()))), NOW);
		intake.availability(List.of(new AvailabilityItem(SPACE, AvailabilityChoice.UNDER_REPAIR, LocalDate.of(2026, 11, 6),
				"Painting", LocalDate.of(2026, 12, 1), id())), NOW);
		run(() -> intakes.update(intake));

		InventoryIntake read = tx(() -> intakes.findById(intake.getId())).orElseThrow();

		assertEquals(IntakeStatus.OWNER_CONFIRMED, read.getStatus());
		assertEquals(intake.getVersion(), read.getVersion());
		assertEquals(intake.getTarget(), read.getTarget());
		assertEquals(intake.getAvailability(), read.getAvailability());
		assertEquals(LocalDate.of(2026, 12, 1), read.getAvailability().getFirst().expectedDateOrNull());
	}

	/** I-1 answers as a conflict: the caller turns it into 409, never 500. */
	@Test
	void repository_secondOpenRow_conflict() {
		String lead = id();
		inserted(lead, 1);

		assertThrows(IntakeConflictException.class, () -> inserted(lead, 2));
	}

	@Test
	void repository_nextIntakeNo_countsOnFromTheHighest() {
		String lead = id();
		assertEquals(1, tx(() -> intakes.nextIntakeNo(lead)));
		InventoryIntake first = inserted(lead, 1);
		first.abandon(NOW);
		run(() -> intakes.update(first));

		assertEquals(2, tx(() -> intakes.nextIntakeNo(lead)));
	}

	/** spec 5.3: open-in-view keeps entities; a read after a lock must see what changed meanwhile. */
	@Test
	void repository_lockInATransaction_readsTheLatestRow() {
		InventoryIntake intake = inserted(id(), 1);

		InventoryIntake locked = tx(() -> {
			intakes.findById(intake.getId()).orElseThrow();
			jdbc.update("update inventory_intakes set version = 7 where id = ?", intake.getId());
			return intakes.lockById(intake.getId()).orElseThrow();
		});

		assertEquals(7L, locked.getVersion());
	}

	@Test
	void repository_wrongCodeCounter_countsAndVoidsAtTheLimit() {
		InventoryIntake intake = inserted(id(), 1);
		OwnerRequest request = OwnerRequest.create(id(), intake.getId(), null, "h".repeat(64), "{}", NOW.plusSeconds(600),
				id(), NOW);
		run(() -> requests.save(request));

		for (int expected = 1; expected <= 4; expected++) {
			assertEquals(expected, tx(() -> requests.countWrongCode(request.getId(), OwnerRequest.MAX_WRONG_CODES, NOW)));
		}
		assertNull(tx(() -> requests.findById(request.getId())).orElseThrow().getVoidedAt());

		assertEquals(5, tx(() -> requests.countWrongCode(request.getId(), OwnerRequest.MAX_WRONG_CODES, NOW)));

		OwnerRequest voided = tx(() -> requests.findById(request.getId())).orElseThrow();
		assertEquals(NOW, voided.getVoidedAt());
		assertNull(voided.getDisplaySnapshot());
		assertEquals(0, tx(() -> requests.countWrongCode(request.getId(), OwnerRequest.MAX_WRONG_CODES, NOW)), "a closed row");
	}

	@Test
	void repository_pairKey_secondInsertDoesNothing() {
		InventoryIntake intake = inserted(id(), 1);
		String key = "P:" + id();
		DuplicateCase first = DuplicateCase.open(id(), intake.getId(), id(), null, key, intake.scopeHash(), null,
				CaseSignal.ADDRESS, NOW);
		DuplicateCase second = DuplicateCase.open(id(), intake.getId(), id(), null, key, intake.scopeHash(), null,
				CaseSignal.ADDRESS, NOW);

		assertTrue(tx(() -> cases.insertIfAbsent(first)));
		assertFalse(tx(() -> cases.insertIfAbsent(second)));

		assertEquals(first.getId(), tx(() -> cases.findLiveByPairKey(key)).orElseThrow().getId());
	}

	private InventoryIntake inserted(String lead, int no) {
		InventoryIntake intake = InventoryIntake.create(id(), no, lead, 1, new IntakeSourceRef(id(), "a".repeat(64),
						RentalLayout.PARTS, AssetType.WHOLE_BUILDING, "20242", "1 bach dang", 16.07, 108.22),
				List.of(new SelectedSpace(SPACE, "P101", "Floor 1")), NOW);
		run(() -> intakes.insert(intake));
		return intake;
	}

	private void run(Runnable body) {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> body.run());
	}

	private <T> T tx(java.util.function.Supplier<T> body) {
		return new TransactionTemplate(transactionManager).execute(status -> body.get());
	}

	/** A fresh 26-character id. */
	private static String id() {
		return String.format("01REPO%020d", SEQUENCE.incrementAndGet());
	}
}
