package vn.sunvibe.contract;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import vn.sunvibe.contract.WireContract.Fault;

/**
 * Fails a test class whose answers broke the contract, and writes the report after each class, for the whole {@code app}
 * test task through autodetection (contract-wire spec 4.3). The report holds counts and JSON locations, never a value.
 */
public class WireContractExtension implements BeforeAllCallback, AfterAllCallback {

	private static final Path REPORT = Path.of("build/reports/wire-contract/report.txt");

	private static volatile boolean ran;

	private final WireLedger ledger;
	private final Path report;

	public WireContractExtension() {
		this(WireLedger.shared(), REPORT);
	}

	WireContractExtension(WireLedger ledger, Path report) {
		this.ledger = ledger;
		this.report = report;
	}

	static boolean ran() {
		return ran;
	}

	@Override
	public void beforeAll(ExtensionContext context) {
		ran = true;
	}

	@Override
	public void afterAll(ExtensionContext context) throws IOException {
		ledger.writeReport(report);
		List<Fault> faults = ledger.drainFaults();
		if (!faults.isEmpty()) {
			throw new AssertionError(describe(faults));
		}
	}

	private static String describe(List<Fault> faults) {
		return faults.stream().distinct()
				.map(fault -> fault.method() + ' ' + fault.pathTemplate() + ' ' + fault.status() + ' ' + fault.location() + ' '
						+ fault.keyword())
				.sorted().collect(Collectors.joining("\n  ", "Answers that differ from api/openapi.json:\n  ", ""));
	}
}
