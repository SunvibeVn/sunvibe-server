package vn.sunvibe.survey;

import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;

import vn.sunvibe.survey.core.application.shared.external.media.ImageNormalizer;
import vn.sunvibe.survey.core.application.shared.external.media.MalwareScanner;
import vn.sunvibe.survey.core.domain.model.media.ProbeResult;
import vn.sunvibe.survey.core.domain.model.media.SourceFormat;

/**
 * The media tests' scanner and processor (survey-media spec A-18): the shared media context configures neither, so
 * these answer the health probes as a test sets them (PASSED by default) and scan every file clean. Processing runs
 * only in the worker tests, which build their own worker.
 */
public final class MediaTestProbes {

	public volatile ProbeResult scanner = ProbeResult.PASSED;
	public volatile ProbeResult processor = ProbeResult.PASSED;

	public void reset() {
		scanner = ProbeResult.PASSED;
		processor = ProbeResult.PASSED;
	}

	public MalwareScanner scanner() {
		return new MalwareScanner() {
			@Override
			public ScanVerdict scan(InputStream content) {
				return ScanVerdict.CLEAN;
			}

			@Override
			public ProbeResult probe(Instant now) {
				return scanner;
			}
		};
	}

	public ImageNormalizer normalizer() {
		return new ImageNormalizer() {
			@Override
			public Optional<SourceFormat> detect(InputStream source, long length) {
				throw new UnsupportedOperationException("no processor in the shared media context");
			}

			@Override
			public NormalizeResult normalize(InputStream source, long length) {
				throw new UnsupportedOperationException("no processor in the shared media context");
			}

			@Override
			public ProbeResult probe() {
				return processor;
			}

			@Override
			public String processorVersion() {
				return "test-9";
			}
		};
	}
}
