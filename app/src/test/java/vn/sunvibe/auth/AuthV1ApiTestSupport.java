package vn.sunvibe.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.Password;
import vn.sunvibe.auth.core.domain.model.user.vo.ProfileImage;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.model.user.vo.Username;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

/**
 * HTTP helpers for the {@code /api/v1/auth} and {@code /api/v1/me} end-to-end tests (auth-browser-session spec §10.1).
 * The user and login helpers mirror those of {@link RoleAdminApiTest}.
 * <p>
 * Cookies are parsed from {@code Set-Cookie} and sent in a {@code Cookie} header by the test itself:
 * {@code java.net.CookieManager} never sends a Secure cookie to the plain-HTTP test server. Tokens, cookie values and
 * passwords are never printed.
 */
final class AuthV1ApiTestSupport implements AutoCloseable {

	/** The allowed browser origin of the base profile; refresh and logout need it (spec §3.6). */
	static final String ORIGIN = "http://localhost:3000";
	static final String REFRESH_COOKIE = "sv_refresh";

	/**
	 * Shared by every test class in the JVM, so two classes on one database never pick the same phone; the 07 prefix
	 * keeps them apart from the 08 and 09 users of the other API tests.
	 */
	private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger(1);
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

	private final int port;
	private final UserDomainRepository userRepository;
	private final BCryptPasswordEncoder passwordEncoder;
	private final IdGenerator<String> ulidGenerator;
	// HTTP/1.1: one Cookie header exactly as the test wrote it
	private final HttpClient http = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	private final JsonMapper json = JsonMapper.builder().build();

	record Created(String userId, String username, String password) {
		@Override
		public String toString() {
			return "Created[" + username + "]";
		}
	}

	/**
	 * A session opened by {@code POST /api/v1/auth/login}.
	 *
	 * @param refreshCookie the {@code name=value} pair to send in a {@code Cookie} header
	 * @param rawSetCookie  the whole {@code Set-Cookie} header of the refresh cookie
	 */
	record Session(String accessToken, String refreshCookie, String rawSetCookie) {
		@Override
		public String toString() {
			return "Session[hidden]";
		}
	}

	AuthV1ApiTestSupport(int port, UserDomainRepository userRepository, BCryptPasswordEncoder passwordEncoder,
			IdGenerator<String> ulidGenerator) {
		this.port = port;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.ulidGenerator = ulidGenerator;
	}

	@Override
	public void close() {
		http.close();
	}

	JsonMapper json() {
		return json;
	}

	static String nextPhone() {
		return "07" + String.format("%08d", PHONE_SEQUENCE.getAndIncrement());
	}

	/** An account with a verified phone, stored directly (no registration flow). */
	Created createUser(UserStatus status, String... roles) {
		String username = nextPhone();
		String password = UUID.randomUUID().toString();
		Instant now = AppClock.currentUTC();
		AppUser user = AppUser.create(UserId.create(ulidGenerator.generateId()), "W10 user", Username.create(username),
				null, Password.create(passwordEncoder.encode(password)), ProfileImage.create(null), null,
				new HashSet<>(Set.of(roles)), now);
		user.verifyPhone(now);
		user.setUserStatus(status);
		return new Created(userRepository.save(user).getId().getValue(), username, password);
	}

	Session logIn(Created user) throws Exception {
		HttpResponse<String> login = post("/api/v1/auth/login", null, null, null, loginBody(user.username(),
				user.password()));
		assertEquals(200, login.statusCode(), "login failed, status " + login.statusCode());
		String accessToken = json.readTree(login.body()).path("accessToken").asString();
		List<String> setCookies = login.headers().allValues("Set-Cookie");
		String raw = setCookies.stream()
				.filter(c -> c.startsWith(REFRESH_COOKIE + "="))
				.findFirst().orElseThrow();
		return new Session(accessToken, REFRESH_COOKIE + "=" + cookieValue(setCookies, REFRESH_COOKIE), raw);
	}

	String loginBody(String username, String password) throws Exception {
		return json.writeValueAsString(Map.of("username", username, "password", password));
	}

	/**
	 * A POST; every argument but {@code path} may be null, and then that header (or the body) is not sent. A JSON body
	 * is sent with {@code Content-Type: application/json}.
	 */
	HttpResponse<String> post(String path, String origin, String cookieHeader, String bearer, String jsonBody)
			throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).timeout(REQUEST_TIMEOUT);
		if (origin != null) {
			request.header("Origin", origin);
		}
		if (cookieHeader != null) {
			request.header("Cookie", cookieHeader);
		}
		if (bearer != null) {
			request.header("Authorization", "Bearer " + bearer);
		}
		if (jsonBody != null) {
			request.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(jsonBody));
		} else {
			request.POST(HttpRequest.BodyPublishers.noBody());
		}
		return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	/** A GET; {@code bearer} may be null, and then no {@code Authorization} header is sent. */
	HttpResponse<String> get(String path, String bearer) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).timeout(REQUEST_TIMEOUT).GET();
		if (bearer != null) {
			request.header("Authorization", "Bearer " + bearer);
		}
		return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	/** A request to {@code path} with the usual timeout, for the headers and methods the helpers above do not cover. */
	HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(uri(path)).timeout(REQUEST_TIMEOUT);
	}

	HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
		return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	JsonNode body(HttpResponse<String> response) {
		String raw = response.body();
		return raw == null || raw.isBlank() ? json.missingNode() : json.readTree(raw);
	}

	/** The value of the first {@code Set-Cookie} named {@code name}, or null when none is set. */
	static String cookieValue(List<String> setCookies, String name) {
		return setCookies.stream()
				.filter(c -> c.startsWith(name + "="))
				.map(c -> {
					int end = c.indexOf(';');
					return c.substring(name.length() + 1, end < 0 ? c.length() : end);
				})
				.findFirst().orElse(null);
	}

	/**
	 * The {@code Cookie} header a browser would send to {@code requestPath} from these {@code Set-Cookie} headers:
	 * every non-empty cookie whose {@code Path} matches it (RFC 6265 §5.1.4); null when none does. Every cookie this
	 * API sets carries a {@code Path}; one without it counts as {@code /}.
	 */
	static String cookieHeaderFor(List<String> setCookies, String requestPath) {
		List<String> pairs = setCookies.stream()
				.filter(c -> pathMatches(attribute(c, "Path"), requestPath))
				.map(c -> c.substring(0, c.indexOf(';') < 0 ? c.length() : c.indexOf(';')))
				.filter(pair -> !pair.endsWith("="))
				.toList();
		return pairs.isEmpty() ? null : String.join("; ", pairs);
	}

	/**
	 * RFC 6265 §5.1.4 path-match: the paths are equal, or the cookie path is a prefix of the request path that ends with
	 * {@code /} or is followed by {@code /} in it.
	 */
	static boolean pathMatches(String cookiePath, String requestPath) {
		String path = cookiePath == null || !cookiePath.startsWith("/") ? "/" : cookiePath;
		if (requestPath.equals(path)) {
			return true;
		}
		return requestPath.startsWith(path)
				&& (path.endsWith("/") || requestPath.charAt(path.length()) == '/');
	}

	/** The value of a {@code Set-Cookie} attribute (case-insensitive name), or null when it is absent. */
	static String attribute(String setCookie, String name) {
		String[] parts = setCookie.split(";");
		for (int i = 1; i < parts.length; i++) {
			String part = parts[i].trim();
			int eq = part.indexOf('=');
			String key = eq < 0 ? part : part.substring(0, eq);
			if (key.equalsIgnoreCase(name)) {
				return eq < 0 ? "" : part.substring(eq + 1);
			}
		}
		return null;
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}
}
