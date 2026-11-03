package vn.sunvibe.survey;

import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import vn.sunvibe.PostgresContainerConfig;

/**
 * The one test configuration of the survey-media API and persistence tests (plan Global Constraints): the property set
 * of the checklist API tests plus the test-only switch toggle ({@link MediaTestSwitches}), so they all share one Spring
 * context and one PostgreSQL container. Switches vary through that runtime toggle, never through other properties.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate",
			"app.security.rate-limit.enabled=false",
			MediaApiTestBase.STORE,
			MediaApiTestBase.STORE_ROOT,
			MediaApiTestBase.SPOOL_DIR,
			MediaApiTestBase.NO_BACKGROUND_SWEEP,
			MediaApiTestBase.NO_BACKGROUND_SWEEP_INTERVAL,
			MediaApiTestBase.NO_BACKGROUND_PROBES,
			MediaApiTestBase.NO_BACKGROUND_DELETION,
			MediaApiTestBase.NO_BACKGROUND_LISTING })
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, MediaTestSwitches.Config.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class MediaApiTestBase {

	/** The local object store of the media tests (spec A-1: dev, test and lan only), under the JVM's temp folder. */
	static final String STORE = "app.survey.media.store=local";
	static final String STORE_ROOT = "app.survey.media.local.root=${java.io.tmpdir}/sunvibe-media-api-test/store";
	static final String SPOOL_DIR = "app.survey.media.spool-dir=${java.io.tmpdir}/sunvibe-media-api-test/spool";
	/**
	 * No background sweep or probe poll in the shared media context (spec 4.4 step 4 runs them every 5 minutes and
	 * 10 seconds): with no scanner or processor configured, a sweep would pause intake (PROCESSING_OFF) under the
	 * other tests; the sweep and probe tests call them directly.
	 */
	static final String NO_BACKGROUND_SWEEP = "app.survey.media.housekeeping.sweep-start-delay=24h";
	static final String NO_BACKGROUND_SWEEP_INTERVAL = "app.survey.media.housekeeping.sweep-interval=24h";
	static final String NO_BACKGROUND_PROBES = "app.survey.media.housekeeping.probe-poll=24h";
	static final String NO_BACKGROUND_DELETION = "app.survey.media.housekeeping.deletion-poll=24h";
	static final String NO_BACKGROUND_LISTING = "app.survey.media.housekeeping.listing-poll=24h";
	/** The HTTP-stack tests' short read bounds (spec A-25 defaults are asserted by MediaPropertiesDefaultsTest). */
	static final String NO_PROGRESS = "app.survey.media.reads.no-progress-timeout=2s";
	static final String FULL_DEADLINE = "app.survey.media.reads.full-deadline=6s";
}
