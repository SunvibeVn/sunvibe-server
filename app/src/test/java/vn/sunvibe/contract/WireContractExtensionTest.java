package vn.sunvibe.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import vn.sunvibe.contract.WireContract.Fault;

/**
 * A fault fails the test class and names the field, without a value of the body, and is not blamed on the next class
 * (contract-wire spec 4.3, plan Review Focus 1). A ledger of its own, so nothing here reaches the report of the run.
 */
class WireContractExtensionTest {

	private final WireLedger ledger = new WireLedger();

	@Test
	void afterAll_withAFault_failsTheClassAndNamesTheField(@TempDir Path directory) throws Exception {
		String phoneInBody = "0909123456-private";
		Fault fault = new Fault("GET", "/api/v1/extension-test/{id}", 200, "/phone", "type");
		ledger.record("GET", "/api/v1/extension-test/{id}", 200, List.of(fault),
				("{\"phone\":\"" + phoneInBody + "\"}").getBytes(StandardCharsets.UTF_8));
		Path report = directory.resolve("report.txt");
		WireContractExtension extension = new WireContractExtension(ledger, report);

		AssertionError failure = assertThrows(AssertionError.class, () -> extension.afterAll(null));

		assertTrue(failure.getMessage().contains("GET /api/v1/extension-test/{id} 200 /phone type"), failure.getMessage());
		assertFalse(failure.getMessage().contains(phoneInBody), failure.getMessage());
		assertFalse(Files.readString(report).contains(phoneInBody));
		assertEquals(List.of(), ledger.drainFaults());
	}

	@Test
	void afterAll_withNoAnswer_passesAndWritesNoReport(@TempDir Path directory) throws Exception {
		Path report = directory.resolve("report.txt");

		new WireContractExtension(ledger, report).afterAll(null);

		assertFalse(Files.exists(report));
	}
}
