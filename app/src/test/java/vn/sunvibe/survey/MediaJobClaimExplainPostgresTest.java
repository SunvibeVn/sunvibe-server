package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import vn.sunvibe.survey.core.infrastructure.persistence.support.MediaSql;

/**
 * The worker's claim reads the pending PROCESS jobs through the partial index on kind and due time and never scans the
 * done ones (survey-media spec 4.4 step 3, 5.2; plan Task A15). The claim is prepared under
 * {@code plan_cache_mode = force_generic_plan} with {@code enable_seqscan = off}, as {@code MediaQueryPlanPostgresTest}
 * does, so an empty table cannot hide a missing index. Needs Docker.
 */
class MediaJobClaimExplainPostgresTest extends MediaApiTestBase {

	@Autowired
	private DataSource dataSource;

	@Test
	void claim_usesPartialIndexAndNoScanOfDoneJobs() {
		String sql = MediaSql.CLAIM_NEXT_PROCESS.replace(":token", "$1").replace(":leaseUntil", "$2")
				.replace(":now", "$3").replace(":hourStart", "$4").replace(":excluded", "$5");

		String plan = explain(sql, "'01JTOKEN000000000000000001', now() + interval '5 minutes', now(),"
				+ " date_trunc('hour', now()), '-'");

		assertTrue(plan.contains("idx_survey_media_jobs_pending_kind_due"), plan);
		assertFalse(plan.contains("Seq Scan on survey_media_jobs"), plan);
	}

	private String explain(String sql, String arguments) {
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (Statement statement = connection.createStatement()) {
				statement.execute("SET LOCAL enable_seqscan = off");
				statement.execute("SET LOCAL plan_cache_mode = force_generic_plan");
				statement.execute("PREPARE media_claim AS " + sql);
				StringBuilder plan = new StringBuilder();
				try (ResultSet rows = statement.executeQuery("EXPLAIN EXECUTE media_claim(" + arguments + ")")) {
					while (rows.next()) {
						plan.append(rows.getString(1)).append('\n');
					}
				}
				statement.execute("DEALLOCATE media_claim");
				return plan.toString();
			} finally {
				connection.rollback();
			}
		} catch (SQLException e) {
			throw new IllegalStateException(e);
		}
	}
}
