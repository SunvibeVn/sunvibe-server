package vn.sunvibe.survey.media;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;

import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;
import vn.sunvibe.survey.core.infrastructure.media.store.S3ClientSettings;
import vn.sunvibe.survey.core.infrastructure.media.store.S3MediaObjectStore;

/**
 * The store contract against a Cloudflare R2 test bucket (survey-media spec 4.12, A-15; plan PD-7): MinIO is not R2,
 * so this runs before go-live with {@code ./gradlew :app:r2ContractTest}. It fails, never skips, without the
 * R2_TEST_* environment.
 */
@Tag("r2-contract")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2MediaObjectStoreContractTest extends MediaObjectStoreContract {

	private S3MediaObjectStore store;
	private S3MediaObjectStore broken;

	@BeforeAll
	void connect() {
		String endpoint = required("R2_TEST_ENDPOINT");
		store = S3MediaObjectStore.create(new S3ClientSettings(URI.create(endpoint), "auto",
				required("R2_TEST_QUARANTINE_BUCKET"), required("R2_TEST_MEDIA_BUCKET"),
				required("R2_TEST_ACCESS_KEY_ID"), required("R2_TEST_SECRET_ACCESS_KEY"), 32, Duration.ofSeconds(2),
				Duration.ofSeconds(30)));
		broken = S3MediaObjectStore.create(new S3ClientSettings(URI.create(endpoint), "auto",
				required("R2_TEST_QUARANTINE_BUCKET"), required("R2_TEST_MEDIA_BUCKET"), "invalid-key-id",
				"invalid-secret", 2, Duration.ofSeconds(2), Duration.ofSeconds(30)));
	}

	@AfterAll
	void close() {
		if (store != null) {
			store.close();
			broken.close();
		}
	}

	@Override
	protected MediaObjectStore store() {
		return store;
	}

	@Override
	protected MediaObjectStore brokenStore() {
		return broken;
	}

	@Override
	protected int servingConnections() {
		return 32;
	}

	private static String required(String name) {
		String value = System.getenv(name);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("r2ContractTest needs the environment variable " + name
					+ " (an R2 test bucket token; see plan PD-7)");
		}
		return value;
	}
}
