package vn.sunvibe.survey.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.utility.DockerImageName;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.Bucket;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.StoredObject;
import vn.sunvibe.survey.core.application.shared.external.media.StoreUnavailableException;
import vn.sunvibe.survey.core.infrastructure.media.store.S3ClientSettings;
import vn.sunvibe.survey.core.infrastructure.media.store.S3MediaObjectStore;

/**
 * The store contract on MinIO in the build (survey-media spec A-15), and the three bounded clients of A-25: an
 * exhausted GET pool answers unavailable within its 2 s acquisition timeout while background work and probes go on.
 * Needs Docker.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MinioMediaObjectStoreTest extends MediaObjectStoreContract {

	private static final String QUARANTINE = "sv-quarantine";
	private static final String MEDIA = "sv-media";

	/**
	 * The official minio/minio image is no longer published; Pigsty's maintained build of the same server, pinned by
	 * digest, stands in for it (plan Task A7 ruling).
	 */
	private final MinIOContainer minio = new MinIOContainer(DockerImageName.parse(
			"pgsty/minio:RELEASE.2026-08-04T00-00-00Z@sha256:b6bfe7239bfc83fb90d31612d9704d86039dd714f7904b3f1ad68f211e602372")
			.asCompatibleSubstituteFor("minio/minio"));
	private S3MediaObjectStore store;
	private S3MediaObjectStore broken;

	@BeforeAll
	void startMinio() {
		minio.start();
		try (S3Client admin = S3Client.builder().endpointOverride(URI.create(minio.getS3URL()))
				.region(Region.US_EAST_1)
				.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(minio.getUserName(),
						minio.getPassword())))
				.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build()) {
			admin.createBucket(b -> b.bucket(QUARANTINE));
			admin.createBucket(b -> b.bucket(MEDIA));
		}
		store = S3MediaObjectStore.create(settings(minio.getS3URL(), minio.getUserName(), 2));
		broken = S3MediaObjectStore.create(settings("http://127.0.0.1:1", minio.getUserName(), 2));
	}

	@AfterAll
	void stopMinio() {
		store.close();
		broken.close();
		minio.stop();
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
		return 2;
	}

	@Test
	void getPoolExhausted_answersUnavailableWithin2Seconds() throws Exception {
		String prefix = prefix();
		put(Bucket.MEDIA, prefix + "1/FULL", "full");
		List<StoredObject> held = new ArrayList<>();
		try {
			held.add(store.getForServing(prefix + "1/FULL"));
			held.add(store.getForServing(prefix + "1/FULL"));

			long start = System.nanoTime();
			StoreUnavailableException refused = assertThrows(StoreUnavailableException.class,
					() -> store.getForServing(prefix + "1/FULL"));
			Duration waited = Duration.ofNanos(System.nanoTime() - start);

			assertEquals(StoreUnavailableException.POOL_EXHAUSTED, refused.code());
			assertTrue(waited.compareTo(Duration.ofMillis(3500)) < 0, "waited " + waited);
		} finally {
			for (StoredObject object : held) {
				object.close();
			}
			store.delete(Bucket.MEDIA, prefix + "1/FULL");
		}
	}

	@Test
	void threeClients_haveSeparatePools() throws Exception {
		String prefix = prefix();
		put(Bucket.MEDIA, prefix + "1/FULL", "full");
		List<StoredObject> held = new ArrayList<>();
		try {
			held.add(store.getForServing(prefix + "1/FULL"));
			held.add(store.getForServing(prefix + "1/FULL"));

			long start = System.nanoTime();
			put(Bucket.MEDIA, prefix + "1/PREVIEW", "preview");
			assertEquals("preview", new String(read(Bucket.MEDIA, prefix + "1/PREVIEW")));
			store.probe(Bucket.MEDIA);
			store.putCreateOnly(Bucket.QUARANTINE, "quarantine/" + prefix.substring(6) + "1",
					new ByteArrayInputStream(bytes("q")), 1);
			assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofSeconds(2)) < 0);
		} finally {
			for (StoredObject object : held) {
				object.close();
			}
			store.delete(Bucket.MEDIA, prefix + "1/FULL");
			store.delete(Bucket.MEDIA, prefix + "1/PREVIEW");
			store.delete(Bucket.QUARANTINE, "quarantine/" + prefix.substring(6) + "1");
		}
	}

	private S3ClientSettings settings(String endpoint, String user, int getConnections) {
		return new S3ClientSettings(URI.create(endpoint), "us-east-1", QUARANTINE, MEDIA, user, minio.getPassword(),
				getConnections, Duration.ofSeconds(2), Duration.ofSeconds(5));
	}
}
