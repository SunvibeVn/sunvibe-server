package vn.sunvibe.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import vn.sunvibe.contract.WireContract.Fault;

/**
 * The check of one answer against a small contract, no Spring (contract-wire spec 4.3, S-7): what is a fault, what stays
 * unchecked, and that a fault never carries a value of the body.
 */
class WireContractTest {

	private static final String THING = "/api/v1/things/7";
	private static final String TEMPLATE = "/api/v1/things/{id}";
	private static final String JSON = "application/json";

	private static final String CONTRACT = """
			{
			  "openapi": "3.1.0",
			  "paths": {
			    "/api/v1/things/{id}": {
			      "get": { "responses": {
			        "200": { "description": "A thing",
			          "content": { "application/json": { "schema": { "$ref": "#/components/schemas/Thing" } } } },
			        "403": { "description": "Refused",
			          "content": { "*/*": { "schema": { "$ref": "#/components/schemas/Problem" } } } }
			      } },
			      "delete": { "responses": { "204": { "description": "Gone" } } }
			    },
			    "/api/v1/things/latest": {
			      "get": { "responses": { "200": { "description": "The latest",
			        "content": { "*/*": { "schema": { "$ref": "#/components/schemas/Latest" } } } } } }
			    },
			    "/api/v1/staff/survey-media/{mediaId}/content": {
			      "put": { "responses": { "200": { "description": "Stored",
			        "content": { "*/*": { "schema": { "$ref": "#/components/schemas/Problem" } } } } } }
			    },
			    "/api/v1/broken": {
			      "get": { "responses": { "200": { "description": "Refers to nothing",
			        "content": { "*/*": { "schema": { "$ref": "#/components/schemas/Missing" } } } } } }
			    }
			  },
			  "components": { "schemas": {
			    "Thing": { "type": "object", "properties": {
			      "name": { "type": "string" },
			      "note": { "type": ["string", "null"] },
			      "kind": { "type": "string", "enum": ["A", "B"] },
			      "state": { "type": ["string", "null"], "enum": ["ON", "OFF", null] },
			      "count": { "type": "integer" },
			      "at": { "type": "string", "format": "date-time" },
			      "tags": { "type": "array", "items": { "type": "string" } },
			      "child": { "anyOf": [ { "$ref": "#/components/schemas/Problem" }, { "type": "null" } ] }
			    } },
			    "Latest": { "type": "object", "properties": { "latest": { "type": "boolean" } } },
			    "Problem": { "type": "object", "properties": { "code": { "type": "string" } } }
			  } }
			}
			""";

	private final WireContract contract = WireContract.of(CONTRACT);

	@Test
	void nullInANonNullField_isAFault() {
		assertEquals(List.of(fault("/name", "type")), thing("{\"name\":null}"));
	}

	@Test
	void valueOutsideAnEnum_isAFault() {
		assertEquals(List.of(fault("/kind", "enum")), thing("{\"kind\":\"C\"}"));
	}

	@Test
	void nullableEnumWithNull_refusesOtherValues() {
		assertEquals(List.of(), thing("{\"state\":null}"));
		assertEquals(List.of(fault("/state", "enum")), thing("{\"state\":\"BROKEN\"}"));
	}

	@Test
	void wrongType_isAFault() {
		assertEquals(List.of(fault("/count", "type")), thing("{\"count\":\"7\"}"));
	}

	@Test
	void brokenDateTime_isAFault() {
		assertEquals(List.of(fault("/at", "format")), thing("{\"at\":\"2026-11-05T19:59:18.1712426\"}"));
		assertEquals(List.of(), thing("{\"at\":\"2026-11-05T19:59:18.171Z\"}"));
	}

	/** One line per field, not one per row of a page: the location collapses the index, as the ledger does. */
	@Test
	void faultsAcrossAnArray_areOneFault() {
		assertEquals(List.of(fault("/tags/*", "type")), thing("{\"tags\":[\"a\",1,2,3]}"));
	}

	@Test
	void statusNotListed_isAFault() {
		assertEquals(List.of(new Fault("GET", TEMPLATE, 409, "", "status-not-listed")),
				contract.check("GET", THING, 409, JSON, bytes("{\"code\":\"X\"}")));
	}

	@ParameterizedTest
	@ValueSource(ints = {405, 415, 500})
	void status405415500NotListed_areNotChecked(int status) {
		assertEquals(List.of(), contract.check("GET", THING, status, JSON, bytes("{\"code\":\"X\"}")));
	}

	@Test
	void unknownPathWithA2xxBody_isAFault() {
		assertEquals(List.of(new Fault("GET", "/api/v1/nothing", 200, "", "unknown-path-with-body")),
				contract.check("GET", "/api/v1/nothing", 200, JSON, bytes("{}")));
		assertEquals(List.of(new Fault("POST", THING, 201, "", "unknown-path-with-body")),
				contract.check("POST", THING, 201, JSON, bytes("{}")));
	}

	@Test
	void unknownPathOtherwise_isNotChecked() {
		assertEquals(List.of(), contract.check("GET", "/api/v1/nothing", 404, JSON, bytes("{\"code\":\"X\"}")));
		assertEquals(List.of(), contract.check("GET", "/api/v1/nothing", 200, null, new byte[0]));
		assertEquals(List.of(), contract.check("HEAD", THING, 405, JSON, bytes("{\"code\":\"X\"}")));
	}

	@Test
	void declaredJsonArrivingEmpty_isAFault() {
		assertEquals(List.of(new Fault("GET", TEMPLATE, 200, "", "declared-json-missing")),
				contract.check("GET", THING, 200, JSON, new byte[0]));
	}

	@Test
	void declaredJsonArrivingAsSomethingElse_isAFault() {
		Fault missing = new Fault("GET", TEMPLATE, 200, "", "declared-json-missing");

		assertEquals(List.of(missing), contract.check("GET", THING, 200, "text/plain", bytes("{\"name\":\"a\"}")));
		assertEquals(List.of(missing), contract.check("GET", THING, 200, JSON, bytes("{not json")));
	}

	@Test
	void bodyWithNoDeclaredContent_isAFault() {
		assertEquals(List.of(new Fault("DELETE", TEMPLATE, 204, "", "body-not-declared")),
				contract.check("DELETE", THING, 204, JSON, bytes("{}")));
		assertEquals(List.of(), contract.check("DELETE", THING, 204, null, new byte[0]));
	}

	@Test
	void corsRefusal403_isNotChecked() {
		byte[] refusal = bytes("Invalid CORS request");

		assertEquals(List.of(), contract.check("GET", THING, 403, "text/plain", refusal));
		assertEquals(List.of(), contract.check("DELETE", THING, 403, null, refusal));
		assertEquals(List.of(new Fault("GET", TEMPLATE, 403, "", "declared-json-missing")),
				contract.check("GET", THING, 403, "text/plain", bytes("Another refusal")));
		assertEquals(List.of(new Fault("DELETE", TEMPLATE, 204, "", "body-not-declared")),
				contract.check("DELETE", THING, 204, "text/plain", refusal));
	}

	@Test
	void nullInANullableField_passes() {
		assertEquals(List.of(), thing("{\"note\":null,\"child\":null}"));
	}

	@Test
	void nullableReferenceHoldingAnObject_isCheckedInside() {
		assertEquals(List.of(), thing("{\"child\":{\"code\":\"X\"}}"));

		List<Fault> faults = thing("{\"child\":{\"code\":7}}");

		assertFalse(faults.isEmpty());
		assertTrue(faults.stream().allMatch(fault -> fault.location().startsWith("/child")), faults.toString());
	}

	@Test
	void starMediaType_isFound() {
		assertEquals(List.of(), contract.check("GET", THING, 403, JSON, bytes("{\"code\":\"X\"}")));
		assertEquals(List.of(new Fault("GET", TEMPLATE, 403, "/code", "type")),
				contract.check("GET", THING, 403, JSON, bytes("{\"code\":7}")));
	}

	@Test
	void mostSpecificTemplate_wins() {
		assertEquals("/api/v1/things/latest", contract.templateOf("GET", "/api/v1/things/latest"));
		assertEquals(TEMPLATE, contract.templateOf("GET", THING));
		assertEquals(List.of(new Fault("GET", "/api/v1/things/latest", 200, "/latest", "type")),
				contract.check("GET", "/api/v1/things/latest", 200, JSON, bytes("{\"latest\":\"no\"}")));
	}

	@Test
	void templateOf_aMethodOnlyTheWiderTemplateHas_findsTheWiderTemplate() {
		assertEquals(TEMPLATE, contract.templateOf("DELETE", "/api/v1/things/latest"));
	}

	@Test
	void templateOf_noMatch_isTheRawPath() {
		assertEquals("/api/v1/nothing", contract.templateOf("GET", "/api/v1/nothing"));
		assertEquals(THING + "/more", contract.templateOf("GET", THING + "/more"));
	}

	@Test
	void contentUploadPut_isChecked() {
		String upload = "/api/v1/staff/survey-media/m1/content";

		assertEquals(List.of(new Fault("PUT", "/api/v1/staff/survey-media/{mediaId}/content", 200, "/code", "type")),
				contract.check("PUT", upload, 200, JSON, bytes("{\"code\":7}")));
	}

	@Test
	void isBinaryRead_onlyGetOnAVariant() {
		assertTrue(WireContract.isBinaryRead("GET", "/api/v1/staff/survey-media/m1/content/preview"));
		assertFalse(WireContract.isBinaryRead("GET", "/api/v1/staff/survey-media/m1/content/preview/"));
		assertFalse(WireContract.isBinaryRead("HEAD", "/api/v1/staff/survey-media/m1/content/preview"));
		assertFalse(WireContract.isBinaryRead("GET", "/api/v1/staff/survey-media/m1/content"));
		assertFalse(WireContract.isBinaryRead("PUT", "/api/v1/staff/survey-media/m1/content"));
		assertFalse(WireContract.isBinaryRead("GET", "/api/v1/staff/survey-media/m1"));
		assertFalse(WireContract.isBinaryRead("GET", "/api/v1/other/m1/content/preview"));
	}

	@Test
	void binaryRead_isNotChecked() {
		assertEquals(List.of(), contract.check("GET", "/api/v1/staff/survey-media/m1/content/preview", 400, JSON,
				bytes("{\"code\":7}")));
	}

	@Test
	void exceptionInTheCheck_isAFault() {
		assertEquals(List.of(new Fault("GET", "/api/v1/broken", 200, "", "check-failed")),
				contract.check("GET", "/api/v1/broken", 200, JSON, bytes("{}")));
	}

	@Test
	void fault_holdsNoBodyValue() {
		String phoneInBody = "0909123456-private";

		List<Fault> faults = thing("{\"kind\":\"" + phoneInBody + "\",\"count\":\"" + phoneInBody + "\",\"at\":\"" + phoneInBody + "\"}");

		assertEquals(3, faults.size(), faults.toString());
		assertFalse(faults.toString().contains(phoneInBody), faults.toString());
	}

	/** Three answers share schemas, so their first load races too; the ledger takes every answer once. */
	@Test
	void manyThreads_giveTheSameFaults(@TempDir Path directory) throws Exception {
		WireContract shared = WireContract.of(CONTRACT);
		WireLedger ledger = new WireLedger();
		record Call(String method, String uri, String template, String body, Fault fault) {
		}
		String upload = "/api/v1/staff/survey-media/{mediaId}/content";
		List<Call> calls = List.of(
				new Call("GET", THING, TEMPLATE, "{\"name\":null}", fault("/name", "type")),
				new Call("GET", THING, TEMPLATE, "{\"child\":{\"code\":\"X\"},\"kind\":\"C\"}", fault("/kind", "enum")),
				new Call("PUT", "/api/v1/staff/survey-media/m1/content", upload, "{\"code\":7}",
						new Fault("PUT", upload, 200, "/code", "type")));
		CountDownLatch start = new CountDownLatch(1);
		List<Future<List<Fault>>> results = new ArrayList<>();
		List<Fault> expected = new ArrayList<>();

		try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
			for (int i = 0; i < 240; i++) {
				Call call = calls.get(i % calls.size());
				expected.add(call.fault());
				results.add(pool.submit(() -> {
					start.await();
					byte[] body = bytes(call.body());
					List<Fault> faults = shared.check(call.method(), call.uri(), 200, JSON, body);
					ledger.record(call.method(), call.template(), 200, faults, body);
					return faults;
				}));
			}
			start.countDown();
			for (int i = 0; i < results.size(); i++) {
				assertEquals(List.of(expected.get(i)), results.get(i).get(60, TimeUnit.SECONDS));
			}
		}

		assertEquals(240, ledger.drainFaults().size());
		assertEquals(160, ledger.answers("GET", TEMPLATE, 200));
		assertEquals(80, ledger.answers("PUT", upload, 200));
		Path report = directory.resolve("report.txt");
		ledger.writeReport(report);
		assertTrue(Files.readAllLines(report).contains("field\tGET\t" + TEMPLATE + "\t200\t/name\t80\t0"));
	}

	@Test
	void ledger_countsNullAndValuePerLocation(@TempDir Path directory) throws Exception {
		WireLedger ledger = new WireLedger();
		String operation = "GET\t/ledger/{id}\t200";
		byte[] body = bytes("{\"items\":[{\"a\":null,\"b\":1},{\"a\":\"body-value\",\"b\":2}],\"c\":null}");

		ledger.record("GET", "/ledger/{id}", 200, List.of(), body);
		ledger.record("GET", "/ledger/{id}", 200, List.of(), bytes("Invalid CORS request"));
		Path report = directory.resolve("nested/report.txt");
		ledger.writeReport(report);

		List<String> lines = Files.readAllLines(report);
		assertTrue(ledger.seen("GET", "/ledger/{id}", 200));
		assertFalse(ledger.seen("GET", "/ledger/{id}", 404));
		assertFalse(Files.readString(report).contains("body-value"), lines.toString());
		assertTrue(lines.contains("answer\t" + operation + "\t2"), lines.toString());
		assertTrue(lines.contains("field\t" + operation + "\t/items/*/a\t1\t1"), lines.toString());
		assertTrue(lines.contains("field\t" + operation + "\t/items/*/b\t0\t2"), lines.toString());
		assertTrue(lines.contains("field\t" + operation + "\t/c\t1\t0"), lines.toString());
	}

	@Test
	void ledger_keepsFaultsUntilDrained() {
		WireLedger ledger = new WireLedger();
		Fault fault = new Fault("GET", "/ledger/{id}", 200, "/a", "type");

		ledger.record("GET", "/ledger/{id}", 200, List.of(fault), bytes("{\"a\":null}"));

		assertEquals(List.of(fault), ledger.drainFaults());
		assertEquals(List.of(), ledger.drainFaults());
	}

	private List<Fault> thing(String body) {
		return contract.check("GET", THING, 200, JSON, bytes(body));
	}

	private static Fault fault(String location, String keyword) {
		return new Fault("GET", TEMPLATE, 200, location, keyword);
	}

	private static byte[] bytes(String text) {
		return text.getBytes(StandardCharsets.UTF_8);
	}
}
