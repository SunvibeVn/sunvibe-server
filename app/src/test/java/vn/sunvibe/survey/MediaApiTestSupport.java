package vn.sunvibe.survey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;

import com.github.f4b6a3.ulid.UlidCreator;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;
import vn.sunvibe.survey.core.infrastructure.persistence.adapter.MediaSql;

/**
 * Calls and stored rows of the survey-media API tests (survey-media spec 7.1): the create call, request bodies, real
 * SHA-256 hashes and photo rows written directly in every state of spec 4.2, so limits and identity can be set up
 * without the upload flow.
 */
public final class MediaApiTestSupport {

	public static final String MEDIA = "/api/v1/staff/survey-media";

	private final ChecklistApiTestSupport checklists;
	private final JdbcTemplate jdbc;

	public MediaApiTestSupport(ChecklistApiTestSupport checklists, JdbcTemplate jdbc) {
		this.checklists = checklists;
		this.jdbc = jdbc;
	}

	// ==================== calls ====================

	public Result create(TestUser caller, String checklistId, Object body) throws Exception {
		return checklists.api().post(caller, ChecklistApiTestSupport.CHECKLISTS + "/" + checklistId + "/media", body);
	}

	/** The media list, with the known version or without one. */
	public Result list(TestUser caller, String checklistId, String knownVersionOrNull) throws Exception {
		return checklists.api().get(caller, ChecklistApiTestSupport.CHECKLISTS + "/" + checklistId + "/media"
				+ (knownVersionOrNull == null ? "" : "?knownVersion=" + knownVersionOrNull));
	}

	/** The {@code knownVersion} of a list answer (spec A-30). */
	public static String knownVersion(tools.jackson.databind.JsonNode list) {
		tools.jackson.databind.JsonNode basis = list.path("basis");
		return list.path("setVersion").asLong() + "." + basis.path("identificationVersion").asLong() + "."
				+ basis.path("conditionVersion").asLong() + "." + basis.path("checklistVersion").asLong() + "."
				+ basis.path("stateEpoch").asLong() + "." + basis.path("viewDigest").asString();
	}

	/** Every key of {@code MediaCreateRequest} (spec 7.2). */
	public static Map<String, Object> body(String uploadKey, String spaceId, String category, String caption,
			Long sizeBytes, String contentSha256) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("uploadKey", uploadKey);
		body.put("spaceId", spaceId);
		body.put("category", category);
		body.put("caption", caption);
		body.put("sizeBytes", sizeBytes);
		body.put("contentSha256", contentSha256);
		return body;
	}

	/** A photo of the asset's exterior, 1000 bytes, with a fresh key. */
	public static Map<String, Object> exterior(String contentSha256) {
		return body(newKey(), null, "EXTERIOR_ACCESS", null, 1000L, contentSha256);
	}

	public static String newKey() {
		return UlidCreator.getMonotonicUlid().toString();
	}

	/** The real SHA-256 of the seed's UTF-8 bytes, lower-case hex. */
	public static String sha(String seed) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(seed.getBytes(StandardCharsets.UTF_8)));
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	// ==================== content upload (spec 4.4 step 2) ====================

	/** A photo of the asset's exterior declared with these bytes; returns its id (asserts 201). */
	public String created(TestUser caller, String checklistId, byte[] bytes) throws Exception {
		Result created = create(caller, checklistId, body(newKey(), null, "EXTERIOR_ACCESS", null,
				(long) bytes.length, sha(bytes)));
		org.junit.jupiter.api.Assertions.assertEquals(201, created.status(), created.rawBody());
		return created.body().path("media").path("id").asString();
	}

	/** The content PUT of the bytes with their digest. */
	public Result put(TestUser caller, String mediaId, byte[] bytes) throws Exception {
		return put(caller, mediaId, "application/octet-stream", digest(bytes), bytes);
	}

	public Result put(TestUser caller, String mediaId, String contentType, String digestOrNull, byte[] bytes)
			throws Exception {
		java.net.http.HttpRequest.Builder request = checklists.api().request(caller, MEDIA + "/" + mediaId
				+ "/content").header("Content-Type", contentType)
				.PUT(java.net.http.HttpRequest.BodyPublishers.ofByteArray(bytes));
		if (digestOrNull != null) {
			request.header("Content-Digest", digestOrNull);
		}
		return checklists.api().send(request.build());
	}

	/** RFC 9530: {@code sha-256=:<base64>:}. */
	public static String digest(byte[] bytes) {
		try {
			return "sha-256=:" + java.util.Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
					.digest(bytes)) + ":";
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public static String sha(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Deterministic bytes of the length, different per seed. */
	public static byte[] bytes(int length, int seed) {
		byte[] bytes = new byte[length];
		new java.util.Random(seed).nextBytes(bytes);
		return bytes;
	}

	/**
	 * A content PUT over a raw socket, so a test controls when bytes are sent (a stalled or slow body) and sees an
	 * answer sent before the body ends.
	 */
	public RawUpload openUpload(TestUser caller, String mediaId, long declaredLength, String digest)
			throws java.io.IOException {
		java.net.Socket socket = new java.net.Socket("localhost", checklists.api().uri("/").getPort());
		socket.setSoTimeout(30_000);
		String crlf = "\r\n";
		String head = "PUT " + MEDIA + "/" + mediaId + "/content HTTP/1.1" + crlf + "Host: localhost" + crlf
				+ "Authorization: Bearer " + caller.secret() + crlf + "Content-Type: application/octet-stream" + crlf
				+ "Content-Length: " + declaredLength + crlf + "Content-Digest: " + digest + crlf + crlf;
		socket.getOutputStream().write(head.getBytes(StandardCharsets.US_ASCII));
		socket.getOutputStream().flush();
		return new RawUpload(socket);
	}

	/** An upload in flight on its own connection. */
	public static final class RawUpload implements AutoCloseable {

		private final java.net.Socket socket;

		RawUpload(java.net.Socket socket) {
			this.socket = socket;
		}

		public RawUpload send(byte[] bytes) throws java.io.IOException {
			socket.getOutputStream().write(bytes);
			socket.getOutputStream().flush();
			return this;
		}

		/** Ends the request body here (the client sends no more) while the answer can still be read. */
		public void halfClose() throws java.io.IOException {
			socket.shutdownOutput();
		}

		/** The status line and headers of the answer, lower-case header names, or empty when none came in time. */
		public Map<String, String> answer(int timeoutMillis) throws java.io.IOException {
			socket.setSoTimeout(timeoutMillis);
			Map<String, String> head = new LinkedHashMap<>();
			java.io.InputStream in = socket.getInputStream();
			StringBuilder line = new StringBuilder();
			try {
				int previous = -1;
				for (int next = in.read(); next != -1; next = in.read()) {
					if (next == '\n' && previous == '\r') {
						String text = line.substring(0, line.length() - 1);
						line.setLength(0);
						if (text.isEmpty()) {
							break;
						}
						if (head.isEmpty()) {
							head.put(":status", text.split(" ")[1]);
						} else {
							int colon = text.indexOf(':');
							head.put(text.substring(0, colon).trim().toLowerCase(java.util.Locale.ROOT),
									text.substring(colon + 1).trim());
						}
					} else {
						line.append((char) next);
					}
					previous = next;
				}
			} catch (java.net.SocketTimeoutException e) {
				// no answer while the server still reads the body
			}
			return head;
		}

		/** The answer body read after {@link #answer}: what arrives within the time, as text (a chunked body keeps its sizes). */
		public String answerBody(int timeoutMillis) throws java.io.IOException {
			socket.setSoTimeout(timeoutMillis);
			java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
			byte[] chunk = new byte[4096];
			try {
				java.io.InputStream in = socket.getInputStream();
				for (int read = in.read(chunk); read != -1; read = in.read(chunk)) {
					body.write(chunk, 0, read);
					if (body.toString(StandardCharsets.UTF_8).contains("}]")) {
						break;
					}
				}
			} catch (java.net.SocketTimeoutException | java.net.SocketException e) {
				// whatever arrived is the answer
			}
			return body.toString(StandardCharsets.UTF_8);
		}

		/**
		 * Sends the bytes in chunks and then reads: true when the server closed the connection on the way (a write fails
		 * or the read ends), as the container does after an at-once answer once it swallowed its limit (2 MB).
		 */
		public boolean closedWhileSending(byte[] bytes) throws java.io.IOException {
			try {
				for (int at = 0; at < bytes.length; at += 64 * 1024) {
					socket.getOutputStream().write(bytes, at, Math.min(64 * 1024, bytes.length - at));
				}
				socket.getOutputStream().flush();
			} catch (java.net.SocketException e) {
				return true;
			}
			return closedByServer(5_000);
		}

		/** True when the server closed the connection: a read ends at once. */
		public boolean closedByServer(int timeoutMillis) throws java.io.IOException {
			socket.setSoTimeout(timeoutMillis);
			try {
				java.io.InputStream in = socket.getInputStream();
				byte[] rest = new byte[8192];
				while (true) {
					if (in.read(rest) == -1) {
						return true;
					}
				}
			} catch (java.net.SocketTimeoutException e) {
				return false;
			} catch (java.net.SocketException e) {
				return true;
			}
		}

		@Override
		public void close() throws java.io.IOException {
			socket.close();
		}
	}

	// ==================== stored rows ====================

	/** The states a photo row can be written in directly. */
	public enum Row {
		AWAITING_CONTENT, READY, REJECTED, FAILED, CANCELLED, UPLOAD_EXPIRED, REMOVED_READY, PROCESSING
	}

	/**
	 * Writes {@code count} photo rows of the uploader on the checklist in the state, each with its own key and hash
	 * (from the seed and its number), numbered after the checklist's last number; creates or moves the set row.
	 * Returns their ids.
	 */
	public List<String> insertPhotos(String checklistId, String uploaderId, Row row, int count, Instant createdAt,
			long sizeBytes, String hashSeed) {
		jdbc.update("insert into survey_media_sets (checklist_id, set_version, last_media_no, updated_at)"
				+ " values (?, 0, 0, ?) on conflict (checklist_id) do nothing", checklistId, ts(createdAt));
		Integer last = jdbc.queryForObject("select last_media_no from survey_media_sets where checklist_id = ?",
				Integer.class, checklistId);
		List<String> ids = new ArrayList<>();
		for (int i = 1; i <= count; i++) {
			String id = UlidCreator.getMonotonicUlid().toString();
			ids.add(id);
			insert(id, checklistId, uploaderId, last + i, row, createdAt, sizeBytes, sha(hashSeed + "#" + i), null,
					null, "DETAIL");
		}
		jdbc.update("update survey_media_sets set last_media_no = ?, set_version = set_version + ? where checklist_id = ?",
				last + count, count, checklistId);
		return ids;
	}

	/** One photo row with the given hash. */
	public String insertPhoto(String checklistId, String uploaderId, Row row, Instant createdAt, String sha256) {
		return insertPhoto(checklistId, uploaderId, row, createdAt, sha256, null, null, "DETAIL");
	}

	/** One photo row of the placement (space id and code, both null for the asset) and category. */
	public String insertPhoto(String checklistId, String uploaderId, Row row, Instant createdAt, String sha256,
			String spaceIdOrNull, String spaceCodeOrNull, String category) {
		jdbc.update("insert into survey_media_sets (checklist_id, set_version, last_media_no, updated_at)"
				+ " values (?, 0, 0, ?) on conflict (checklist_id) do nothing", checklistId, ts(createdAt));
		Integer last = jdbc.queryForObject("select last_media_no from survey_media_sets where checklist_id = ?",
				Integer.class, checklistId);
		String id = UlidCreator.getMonotonicUlid().toString();
		insert(id, checklistId, uploaderId, last + 1, row, createdAt, 1000L, sha256, spaceIdOrNull, spaceCodeOrNull,
				category);
		jdbc.update("update survey_media_sets set last_media_no = ?, set_version = set_version + 1"
				+ " where checklist_id = ?", last + 1, checklistId);
		return id;
	}

	private void insert(String id, String checklistId, String uploaderId, int mediaNo, Row row, Instant createdAt,
			long sizeBytes, String sha256, String spaceIdOrNull, String spaceCodeOrNull, String category) {
		Instant received = createdAt.plusSeconds(30);
		Instant finished = createdAt.plusSeconds(60);
		boolean content = row != Row.AWAITING_CONTENT && row != Row.UPLOAD_EXPIRED;
		String status = switch (row) {
			case REMOVED_READY -> "READY";
			default -> row.name();
		};
		String reason = switch (row) {
			case REJECTED -> "UNREADABLE";
			case FAILED -> "PROCESSING_UNAVAILABLE";
			case CANCELLED -> "REMOVED";
			case UPLOAD_EXPIRED -> "CONTENT_NOT_RECEIVED";
			default -> null;
		};
		boolean ready = row == Row.READY || row == Row.REMOVED_READY;
		boolean finishedRow = row != Row.AWAITING_CONTENT && row != Row.PROCESSING;
		boolean removed = row == Row.CANCELLED || row == Row.REMOVED_READY;
		jdbc.update("insert into survey_media (id, checklist_id, upload_key, media_no, version, space_id,"
				+ " space_code_at_upload, category, caption, uploaded_by_user_id, created_at, declared_size_bytes,"
				+ " source_sha256, status, status_reason, content_received_at, source_storage_key, source_format,"
				+ " ready_at, finished_at, removed_at, removed_by_user_id, removal_kind, content_delete_requested_at)"
				+ " values (?, ?, ?, ?, 0, ?, ?, ?, null, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
				id, checklistId, newKey(), mediaNo, spaceIdOrNull, spaceCodeOrNull, category, uploaderId, ts(createdAt),
				sizeBytes, sha256, status, reason,
				content ? ts(received) : null, content ? "quarantine/" + id + "/1" : null, ready ? "JPEG" : null,
				ready ? ts(finished) : null, finishedRow ? ts(finished) : null, removed ? ts(finished) : null,
				removed ? uploaderId : null, removed ? "HOLDER" : null,
				row == Row.REMOVED_READY ? ts(finished) : null);
	}

	/**
	 * A READY photo of the checklist with its three variants stored and their rows written (attempt 1, processor
	 * version {@code test-9}); returns its id.
	 */
	public String readyWithVariants(MediaObjectStore store, String checklistId, String uploaderId, byte[] full,
			byte[] preview, byte[] thumbnail) {
		String id = insertPhoto(checklistId, uploaderId, Row.READY, Instant.now().minus(java.time.Duration.ofMinutes(30)),
				sha(newKey()));
		Map<String, byte[]> variants = new LinkedHashMap<>();
		variants.put("FULL", full);
		variants.put("PREVIEW", preview);
		variants.put("THUMBNAIL", thumbnail);
		for (Map.Entry<String, byte[]> variant : variants.entrySet()) {
			String key = "media/" + id + "/1/" + variant.getKey();
			store.putCreateOnly(MediaObjectStore.Bucket.MEDIA, key, new java.io.ByteArrayInputStream(
					variant.getValue()), variant.getValue().length);
			jdbc.update("insert into survey_media_variants (media_id, variant, attempt, storage_key, sha256, size_bytes,"
					+ " width, height, processor_version, created_at) values (?, ?, 1, ?, ?, ?, 400, 300, 'test-9', ?)",
					id, variant.getKey(), key, sha(variant.getValue()), variant.getValue().length,
					ts(Instant.now()));
		}
		return id;
	}

	/** A holder removal (spec 7.1): {@code {correction}}, null when none is sent. */
	/**
	 * A submission row of the checklist, if it has none yet, whose frozen set holds the photo (checklist spec I-5). The
	 * checklist stays a DRAFT, so the media command reaches the reference check behind the read-only one.
	 */
	public void evidenceRow(String checklistId, String mediaId) {
		jdbc.update("insert into survey_checklist_submissions (checklist_id, submitted_by_user_id, submitted_at,"
				+ " template_code, template_version, kind, lead_intent, intent_cycle, visit_appointment_id,"
				+ " visit_performed_by, visit_arrived_at, visit_recorded_by, visit_recorded_at, rental_scope, spaces,"
				+ " group_versions, media_policy, content_sha256)"
				+ " select c.id, c.holder_user_id, now(), c.template_code, c.template_version, c.kind, 'LONG_TERM_RENT',"
				+ " c.intent_cycle, c.visit_appointment_id, c.holder_user_id, now(), c.holder_user_id, now(), 'WHOLE',"
				+ " '[]', '{}', 'MEDIA_V1_NO_PERMISSION', ? from survey_checklists c where c.id = ?"
				+ " on conflict do nothing", "c".repeat(64), checklistId);
		jdbc.update("insert into survey_checklist_evidence (checklist_id, media_id, category, full_sha256)"
				+ " select ?, m.id, m.category, ? from survey_media m where m.id = ?", checklistId, "d".repeat(64),
				mediaId);
	}

	public Result remove(TestUser caller, String mediaId, Map<String, Object> correctionOrNull) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("correction", correctionOrNull);
		return checklists.api().post(caller, MEDIA + "/" + mediaId + "/removal", body);
	}

	/** Moves an AWAITING_CONTENT row to RECEIVING, as a claim leaves it. */
	public void receiving(String mediaId) {
		jdbc.update("update survey_media set status = 'RECEIVING', receive_token = ?, receive_key = ?,"
				+ " receive_started_at = ? where id = ? and status = 'AWAITING_CONTENT'", newKey(),
				"quarantine/" + mediaId + "/1", ts(Instant.now()), mediaId);
	}

	/** Puts the security hold on a PROCESSING or READY row (spec A-23), as an ADMIN's hold leaves it. */
	public void held(String mediaId, String adminId, String reason) {
		jdbc.update("update survey_media set held_at = ?, held_by_user_id = ?, hold_reason = ?,"
				+ " hold_released_at = null, hold_released_by_user_id = null where id = ?", ts(Instant.now()),
				adminId, reason, mediaId);
	}

	/** The photo's jobs: kind, state and due time. */
	public List<Map<String, Object>> jobs(String mediaId) {
		return jdbc.queryForList("select kind, state, due_at, last_error_code from survey_media_jobs"
				+ " where media_id = ? order by id", mediaId);
	}

	/** One photo row with every column. */
	public Map<String, Object> row(String mediaId) {
		return jdbc.queryForMap("select * from survey_media where id = ?", mediaId);
	}

	/** An object of the local test store, its modification time set {@code age} ago (sweep ages, spec A-17). */
	public static void storedObject(MediaObjectStore store, MediaObjectStore.Bucket bucket, String key,
			java.time.Duration age) {
		store.putCreateOnly(bucket, key, new java.io.ByteArrayInputStream(new byte[] {1, 2, 3}), 3);
		try {
			java.nio.file.Files.setLastModifiedTime(objectPath(bucket, key), java.nio.file.attribute.FileTime.from(
					Instant.now().minus(age)));
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	/** Whether the local test store holds the object. */
	public static boolean objectExists(MediaObjectStore.Bucket bucket, String key) {
		return java.nio.file.Files.exists(objectPath(bucket, key));
	}

	private static java.nio.file.Path objectPath(MediaObjectStore.Bucket bucket, String key) {
		return java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "sunvibe-media-api-test", "store",
				bucket == MediaObjectStore.Bucket.QUARANTINE ? "quarantine-bucket" : "media-bucket").resolve(key);
	}

	/** A content read. */
	public Result read(TestUser caller, String mediaId, String variantAndQuery) throws Exception {
		return checklists.api().get(caller, MEDIA + "/" + mediaId + "/content/" + variantAndQuery);
	}

	/** The photo rows of the checklist by number: id, status, uploader, hash. */
	public List<Map<String, Object>> photos(String checklistId) {
		return jdbc.queryForList("select id, media_no, status, uploaded_by_user_id, source_sha256, removed_at,"
				+ " version from survey_media where checklist_id = ? order by media_no", checklistId);
	}

	public long setVersion(String checklistId) {
		List<Long> versions = jdbc.queryForList("select set_version from survey_media_sets where checklist_id = ?",
				Long.class, checklistId);
		return versions.isEmpty() ? -1 : versions.get(0);
	}

	public int setRows(String checklistId) {
		return jdbc.queryForObject("select count(*) from survey_media_sets where checklist_id = ?", Integer.class,
				checklistId);
	}

	/** The lead's media security events with their request ids, oldest first. */
	public List<Map<String, Object>> mediaEvents(String leadId) {
		return jdbc.queryForList("select user_id, resource_type, resource_id, action, outcome, request_id from"
				+ " survey_security_events where lead_id = ? and resource_type in ('MEDIA', 'MEDIA_SET')"
				+ " order by occurred_at, id", leadId);
	}

	// ==================== the create lock ====================

	/**
	 * The user's create lock (spec A-3: the advisory transaction lock in the media namespace) held by the test on its
	 * own connection, so requests pass every check before it and then queue behind it.
	 */
	public static HeldLock holdUserLock(DataSource dataSource, JdbcTemplate jdbc, String userId) throws SQLException {
		Connection connection = dataSource.getConnection();
		connection.setAutoCommit(false);
		try (PreparedStatement lock = connection.prepareStatement("select pg_advisory_xact_lock(?, hashtext(?))")) {
			lock.setInt(1, MediaSql.USER_LOCK_NAMESPACE);
			lock.setString(2, userId);
			lock.execute();
		}
		return new HeldLock(connection, jdbc);
	}

	/** Releases the lock by ending its transaction. */
	public record HeldLock(Connection connection, JdbcTemplate jdbc) implements AutoCloseable {

		/** Waits until {@code n} sessions wait for an advisory lock. */
		public void awaitWaiters(int n) throws InterruptedException {
			long deadline = System.nanoTime() + java.time.Duration.ofSeconds(30).toNanos();
			while (System.nanoTime() < deadline) {
				Integer waiting = jdbc.queryForObject("select count(*) from pg_locks where locktype = 'advisory'"
						+ " and not granted", Integer.class);
				if (waiting != null && waiting >= n) {
					return;
				}
				Thread.sleep(20);
			}
			throw new AssertionError(n + " requests never waited for the advisory lock");
		}

		@Override
		public void close() throws SQLException {
			connection.commit();
			connection.close();
		}
	}

	public static Timestamp ts(Instant instant) {
		return Timestamp.from(instant.truncatedTo(ChronoUnit.MICROS));
	}
}
