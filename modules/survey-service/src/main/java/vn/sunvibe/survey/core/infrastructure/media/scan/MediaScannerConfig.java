package vn.sunvibe.survey.core.infrastructure.media.scan;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.sunvibe.survey.core.application.shared.media.MediaClock;
import vn.sunvibe.survey.core.application.shared.external.media.MalwareScanner;
import vn.sunvibe.survey.core.application.shared.external.media.ScannerSilentException;
import vn.sunvibe.survey.core.domain.model.media.ProbeResult;
import vn.sunvibe.survey.core.infrastructure.config.MediaProperties;
import vn.sunvibe.survey.core.infrastructure.config.MediaProperties.ScannerKind;

import java.io.InputStream;
import java.time.Instant;

/** The scanner of {@code app.survey.media.scanner} (survey-media spec 5.1): none, clamav or the dev/test stub. */
@Configuration(proxyBeanMethods = false)
public class MediaScannerConfig {

    @Bean
    MalwareScanner malwareScanner(MediaProperties media, MediaClock clock) {
        if (media.getScanner() == ScannerKind.STUB) {
            return new StubMalwareScanner(clock);
        }
        if (media.getScanner() == ScannerKind.CLAMAV) {
            MediaProperties.Clamav clamav = media.getClamav();
            if (clamav.getSocket() == null && (clamav.getHost() == null || clamav.getHost().isBlank())) {
                throw new IllegalStateException("app.survey.media.clamav needs a host or a socket with scanner=clamav");
            }
            return new ClamAvScanner(clamav.getHost(), clamav.getPort(), clamav.getSocket(), clamav.getTimeout());
        }
        return new MalwareScanner() {
            @Override
            public ScanVerdict scan(InputStream content) {
                throw new ScannerSilentException();
            }

            @Override
            public ProbeResult probe(Instant now) {
                return ProbeResult.INCOMPLETE;
            }
        };
    }
}
