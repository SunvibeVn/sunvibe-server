package vn.sunvibe.contract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import vn.sunvibe.contract.WireContract.Fault;

/**
 * What the API tests sent (contract-wire spec 4.3). {@link #shared()} serves the whole test JVM, because about twenty
 * Spring contexts each hold a filter and the JUnit extension has no Spring; the check's own tests use a ledger of their
 * own, so their deliberate faults stay out of the report. Every counter is a {@link LongAdder} in a concurrent map and the
 * pending faults a concurrent queue, so server threads record without a lock. It keeps no value of a body, only JSON
 * locations and counts.
 */
final class WireLedger {

	private record Counts(LongAdder nulls, LongAdder values) {
	}

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final String SEPARATOR = "\t";

	private static final WireLedger SHARED = new WireLedger();

	private final Map<String, LongAdder> answers = new ConcurrentHashMap<>();
	private final Map<String, Counts> fields = new ConcurrentHashMap<>();
	private final Map<String, LongAdder> faults = new ConcurrentHashMap<>();
	private final Queue<Fault> pending = new ConcurrentLinkedQueue<>();

	static WireLedger shared() {
		return SHARED;
	}

	/** Called before the body reaches the client, so a client that has its answer has been counted. */
	void record(String method, String template, int status, List<Fault> found, byte[] body) {
		pending.addAll(found);
		found.forEach(fault -> faults.computeIfAbsent(line(fault), key -> new LongAdder()).increment());
		String answer = answer(method, template, status);
		answers.computeIfAbsent(answer, key -> new LongAdder()).increment();
		JsonNode json = parsed(body);
		if (json != null) {
			walk(answer, "", json);
		}
	}

	boolean seen(String method, String template, int status) {
		return answers(method, template, status) > 0;
	}

	long answers(String method, String template, int status) {
		LongAdder count = answers.get(answer(method, template, status));
		return count == null ? 0 : count.sum();
	}

	/** The faults since the last drain, each once: the next test class is not blamed for them. */
	List<Fault> drainFaults() {
		List<Fault> drained = new ArrayList<>();
		for (Fault fault = pending.poll(); fault != null; fault = pending.poll()) {
			drained.add(fault);
		}
		return drained;
	}

	/**
	 * Answers per operation and status, null and value counts per location, and every fault of the run, one tab-separated
	 * row each. Written only when the run saw answers, so a run without them, such as exportOpenApi, keeps the last report.
	 */
	void writeReport(Path file) throws IOException {
		if (answers.isEmpty()) {
			return;
		}
		List<String> lines = new ArrayList<>();
		new TreeMap<>(answers).forEach((key, count) -> lines.add("answer" + SEPARATOR + key + SEPARATOR + count.sum()));
		new TreeMap<>(fields).forEach((key, counts) -> lines.add("field" + SEPARATOR + key + SEPARATOR + counts.nulls().sum()
				+ SEPARATOR + counts.values().sum()));
		new TreeMap<>(faults).forEach((key, count) -> lines.add("fault" + SEPARATOR + key + SEPARATOR + count.sum()));
		Files.createDirectories(file.toAbsolutePath().getParent());
		Files.write(file, lines);
	}

	private static JsonNode parsed(byte[] body) {
		if (body.length == 0) {
			return null;
		}
		try {
			return JSON.readTree(body);
		}
		catch (JacksonException ex) {
			// Not JSON, such as "Invalid CORS request": the answer is counted without a walk (spec 4.3)
			return null;
		}
	}

	private void walk(String answer, String location, JsonNode node) {
		if (node.isObject()) {
			node.properties().forEach(field -> visit(answer, location + '/' + field.getKey(), field.getValue()));
		}
		else if (node.isArray()) {
			node.forEach(item -> visit(answer, location + "/*", item));
		}
	}

	private void visit(String answer, String location, JsonNode value) {
		Counts counts = fields.computeIfAbsent(answer + SEPARATOR + location,
				key -> new Counts(new LongAdder(), new LongAdder()));
		(value.isNull() ? counts.nulls() : counts.values()).increment();
		walk(answer, location, value);
	}

	private static String answer(String method, String template, int status) {
		return method + SEPARATOR + template + SEPARATOR + status;
	}

	private static String line(Fault fault) {
		return answer(fault.method(), fault.pathTemplate(), fault.status()) + SEPARATOR + fault.location() + SEPARATOR
				+ fault.keyword();
	}
}
