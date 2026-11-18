package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import vn.sunvibe.brokerage.AgreementFixture;
import vn.sunvibe.brokerage.AgreementListFixture;
import vn.sunvibe.brokerage.AgreementListFixture.Version;
import vn.sunvibe.brokerage.core.application.query.AgreementPageView;
import vn.sunvibe.brokerage.core.application.usecase.AgreementListQuery;
import vn.sunvibe.brokerage.core.application.usecase.AgreementListUseCase;
import vn.sunvibe.brokerage.core.application.usecase.StaffViewer;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;

/**
 * LS-7 (US-4 AC-3; E05-S7 spec S7-D1; plan E05S7-P5): one list read costs the same statements for a page of 1 and of 50
 * agreements in effect, at most nine, for a Sale and for a manager. {@link StatementCounter} counts the calling thread
 * only, so the use case bean is called on the test thread, as {@link AdvertisingRightSetReadPostgresTest} calls its
 * use case; its annotation set, so they share one context. Needs Docker.
 */
@Import(StatementCounter.Config.class)
class AgreementListStatementsPostgresTest extends IntakeApiTestBase {

	@Autowired
	private AgreementListUseCase lists;

	@Autowired
	private StatementCounter counter;

	private final List<String> created = new ArrayList<>();

	@Override
	String phonePrefix() {
		return "0928";
	}

	@AfterAll
	void deleteAgreements() {
		AgreementFixture.delete(jdbc, created);
	}

	@Test
	void list_statements_sameForOneAndFiftyRows() throws Exception {
		String leadId = survey.lead(leader, sale, "ASSIGNED");
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		for (int index = 0; index < 50; index++) {
			created.add(AgreementListFixture.insert(jdbc, ids, leadId, null, "ACTIVE", now.plusSeconds(index),
					new Version(1, "EFFECTIVE", today().minusDays(30), today().plusDays(150))));
		}
		StaffViewer bySale = new StaffViewer(sale.userId(), Set.of("ROLE_SALE"));
		StaffViewer byManager = new StaffViewer(manager.userId(), Set.of("ROLE_SALES_MANAGER"));

		for (StaffViewer viewer : List.of(bySale, byManager)) {
			List<AgreementPageView> pages = new ArrayList<>();
			long forOne = counter.count(() -> pages.add(lists.list(query(1), viewer)));
			long forFifty = counter.count(() -> pages.add(lists.list(query(50), viewer)));

			assertEquals(1, pages.get(0).items().size(), viewer.userId());
			assertEquals(50, pages.get(1).items().size(), viewer.userId());
			assertTrue(created.containsAll(pages.get(1).items().stream()
					.map(item -> item.agreementId()).toList()), viewer.userId());
			assertEquals(forOne, forFifty, viewer.userId());
			assertTrue(forOne <= 9, viewer.userId() + " " + forOne);
			assertTrue(forOne >= 5, "the read reaches the set load: " + forOne);
		}
	}

	private static AgreementListQuery query(int size) {
		return new AgreementListQuery(Set.of(AgreementStatus.ACTIVE), false, 0, size);
	}
}
