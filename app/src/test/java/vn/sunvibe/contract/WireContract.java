package vn.sunvibe.contract;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The committed contract as a check of one answer (contract-wire spec 4.3). It finds the operation by method and path, the
 * response by status under {@code application/json} or a wildcard, and validates the body with the OpenAPI 3.1 dialect,
 * format assertions on. A fault names where and which rule, never a message or a value, because a message can quote the
 * body. The exceptions of the spec run in its order; there are no others.
 */
final class WireContract {

	/** Where and which rule; {@code location} is a JSON pointer, empty for a rule about the whole answer. */
	record Fault(String method, String pathTemplate, int status, String location, String keyword) {
	}

	private record Template(String path, Pattern pattern, int literals) {
	}

	private static final String STATUS_NOT_LISTED = "status-not-listed";
	private static final String UNKNOWN_PATH_WITH_BODY = "unknown-path-with-body";
	private static final String DECLARED_JSON_MISSING = "declared-json-missing";
	private static final String BODY_NOT_DECLARED = "body-not-declared";
	private static final String CHECK_FAILED = "check-failed";

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final String BASE = "https://contract.sunvibe.test/openapi.json";
	private static final Set<String> METHODS = Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");
	private static final List<String> JSON_MEDIA_TYPES = List.of("application/json", "*/*");
	/** Framework refusals and injected failures; a status the operation lists is checked like any other. */
	private static final Set<Integer> UNCHECKED_WHEN_NOT_LISTED = Set.of(405, 415, 500);
	private static final byte[] CORS_REFUSAL = "Invalid CORS request".getBytes(StandardCharsets.UTF_8);
	private static final Pattern BINARY_READ = Pattern.compile("^/api/v1/staff/survey-media/[^/]+/content/[^/]+$");
	private static final Pattern PARAMETER = Pattern.compile("\\{[^}]+}");
	private static final Pattern ARRAY_INDEX = Pattern.compile("/\\d+(?=/|$)");

	private static volatile WireContract shared;

	private final JsonNode paths;
	private final Map<String, List<Template>> templatesByMethod;
	private final SchemaRegistry registry;
	private final Map<String, Schema> schemas = new ConcurrentHashMap<>();

	private WireContract(String contractJson) {
		this.paths = JSON.readTree(contractJson).path("paths");
		this.templatesByMethod = templates(paths);
		this.registry = SchemaRegistry.withDialect(Dialects.getOpenApi31(),
				builder -> builder.schemas(Map.of(BASE, contractJson)));
	}

	static WireContract of(String contractJson) {
		return new WireContract(contractJson);
	}

	/**
	 * The contract of the build, read on the first answer: {@code contract.file}, or the repo's file from an IDE run, whose
	 * working directory is {@code app}. A failure stays a failure of each check, so it is never a silent skip.
	 */
	static WireContract shared() {
		WireContract loaded = shared;
		if (loaded == null) {
			synchronized (WireContract.class) {
				loaded = shared;
				if (loaded == null) {
					loaded = of(read(Path.of(System.getProperty("contract.file", "../api/openapi.json"))));
					shared = loaded;
				}
			}
		}
		return loaded;
	}

	private static String read(Path file) {
		try {
			return Files.readString(file);
		}
		catch (IOException ex) {
			throw new IllegalStateException("The contract cannot be read: " + file, ex);
		}
	}

	/** True only for {@code GET} on a media variant, which streams binary; {@code PUT .../content} answers JSON. */
	static boolean isBinaryRead(String method, String uri) {
		return "GET".equals(method) && BINARY_READ.matcher(uri).matches();
	}

	/** The most specific template of this method that matches, or the raw path when none does. */
	String templateOf(String method, String uri) {
		Template found = find(method, uri);
		return found == null ? uri : found.path();
	}

	List<Fault> check(String method, String uri, int status, String contentType, byte[] body) {
		String template = templateOf(method, uri);
		try {
			return checked(method, uri, template, status, contentType, body);
		}
		catch (RuntimeException ex) {
			return List.of(new Fault(method, template, status, "", CHECK_FAILED));
		}
	}

	private List<Fault> checked(String method, String uri, String template, int status, String contentType, byte[] body) {
		if (status == 403 && Arrays.equals(body, CORS_REFUSAL)) {
			return List.of();
		}
		if (isBinaryRead(method, uri)) {
			return List.of();
		}
		if (find(method, uri) == null) {
			return status / 100 == 2 && body.length > 0 ? rule(method, template, status, UNKNOWN_PATH_WITH_BODY) : List.of();
		}
		JsonNode response = paths.path(template).path(method.toLowerCase(Locale.ROOT)).path("responses")
				.path(String.valueOf(status));
		if (response.isMissingNode()) {
			return UNCHECKED_WHEN_NOT_LISTED.contains(status) ? List.of() : rule(method, template, status, STATUS_NOT_LISTED);
		}
		JsonNode content = response.path("content");
		if (content.isEmpty()) {
			return body.length == 0 ? List.of() : rule(method, template, status, BODY_NOT_DECLARED);
		}
		if (!isJson(contentType, body)) {
			return rule(method, template, status, DECLARED_JSON_MISSING);
		}
		return validate(method, template, status, mediaTypeOf(content), body);
	}

	private List<Fault> validate(String method, String template, int status, String mediaType, byte[] body) {
		Schema schema = schemas.computeIfAbsent(method + ' ' + template + ' ' + status,
				key -> load(method, template, status, mediaType));
		List<Error> errors = schema.validate(new String(body, StandardCharsets.UTF_8), InputFormat.JSON,
				context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)));
		return errors.stream().map(error -> new Fault(method, template, status, location(error), error.getKeyword()))
				.distinct().toList();
	}

	/** One fault per field, not one per row of a page: the index collapses, as in the ledger. */
	private static String location(Error error) {
		return ARRAY_INDEX.matcher(error.getInstanceLocation().toString()).replaceAll("/*");
	}

	private Schema load(String method, String template, int status, String mediaType) {
		Schema schema = registry.getSchema(SchemaLocation.of(BASE + "#/paths/" + token(template) + '/'
				+ method.toLowerCase(Locale.ROOT) + "/responses/" + status + "/content/" + token(mediaType) + "/schema"));
		schema.initializeValidators();
		return schema;
	}

	private static String mediaTypeOf(JsonNode content) {
		return JSON_MEDIA_TYPES.stream().filter(content::has).findFirst()
				.orElseThrow(() -> new IllegalStateException("The response declares no JSON media type"));
	}

	private static boolean isJson(String contentType, byte[] body) {
		if (body.length == 0 || contentType == null || !contentType.toLowerCase(Locale.ROOT).contains("json")) {
			return false;
		}
		try {
			JSON.readTree(body);
			return true;
		}
		catch (JacksonException ex) {
			return false;
		}
	}

	private Template find(String method, String uri) {
		return templatesByMethod.getOrDefault(method, List.of()).stream()
				.filter(template -> template.pattern().matcher(uri).matches()).findFirst().orElse(null);
	}

	private static List<Fault> rule(String method, String template, int status, String keyword) {
		return List.of(new Fault(method, template, status, "", keyword));
	}

	/** A JSON pointer token inside a URI fragment: escapes {@code ~} and {@code /}, then encodes the braces. */
	private static String token(String text) {
		return text.replace("~", "~0").replace("/", "~1").replace("{", "%7B").replace("}", "%7D");
	}

	private static Map<String, List<Template>> templates(JsonNode paths) {
		Map<String, List<Template>> byMethod = new HashMap<>();
		paths.properties().forEach(path -> path.getValue().properties().forEach(operation -> {
			if (METHODS.contains(operation.getKey())) {
				byMethod.computeIfAbsent(operation.getKey().toUpperCase(Locale.ROOT), key -> new ArrayList<>())
						.add(template(path.getKey()));
			}
		}));
		Comparator<Template> mostLiteralFirst = Comparator.comparingInt(Template::literals).reversed()
				.thenComparing(Template::path);
		byMethod.values().forEach(list -> list.sort(mostLiteralFirst));
		return byMethod;
	}

	private static Template template(String path) {
		StringBuilder regex = new StringBuilder();
		int from = 0;
		var parameters = PARAMETER.matcher(path);
		while (parameters.find()) {
			regex.append(Pattern.quote(path.substring(from, parameters.start()))).append("[^/]+");
			from = parameters.end();
		}
		regex.append(Pattern.quote(path.substring(from)));
		return new Template(path, Pattern.compile(regex.toString()), PARAMETER.matcher(path).replaceAll("").length());
	}
}
