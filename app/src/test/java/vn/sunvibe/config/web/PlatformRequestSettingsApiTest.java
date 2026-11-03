package vn.sunvibe.config.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.web.filter.FormContentFilter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import vn.sunvibe.config.SecurityConfig;
import vn.sunvibe.shared.logging.RequestId;
import vn.sunvibe.survey.MediaApiTestBase;

/**
 * Platform settings of survey-media (spec A-13; plan Task A12; SM-20 unauthenticated part; SV-045, SV-052): no
 * form-content filter and no multipart resolver read a body ahead of security, so an unauthenticated PUT with a large
 * form or multipart body gets its 401 while the client is still sending; each request carries a server request id in
 * its log context. Real HTTP.
 */
class PlatformRequestSettingsApiTest extends MediaApiTestBase {

	private static final long FIFTY_MIB = 50L * 1024 * 1024;

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private ApplicationContext context;

	private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
	private final Logger securityLog = (Logger) LoggerFactory.getLogger(SecurityConfig.class);

	@BeforeEach
	void captureLogs() {
		logs.start();
		securityLog.addAppender(logs);
	}

	@AfterEach
	void releaseLogs() {
		securityLog.detachAppender(logs);
	}

	@Test
	void unauthenticatedFormPut_largeBody_refusedBeforeRead() throws Exception {
		assertRefusedWhileSending("application/x-www-form-urlencoded");
	}

	@Test
	void unauthenticatedMultipartPut_largeBody_refusedBeforeRead() throws Exception {
		assertRefusedWhileSending("multipart/form-data; boundary=sunvibe");
	}

	@Test
	void formContentFilter_notRegistered() {
		assertThat(context.getBeansOfType(FormContentFilter.class)).isEmpty();
	}

	@Test
	void multipartResolver_notRegistered() {
		assertThat(context.containsBean("multipartResolver")).isFalse();
	}

	@Test
	void requestId_isUlidInMdcPerRequest() throws Exception {
		send("GET", "/api/v1/staff/owner-leads", null, 0);
		send("GET", "/api/v1/staff/owner-leads", null, 0);

		List<String> ids = logs.list.stream().map(event -> event.getMDCPropertyMap().get(RequestId.MDC_KEY)).toList();
		assertThat(ids).hasSizeGreaterThanOrEqualTo(2);
		assertThat(ids).allMatch(id -> id != null && id.matches("[0-9A-HJKMNP-TV-Z]{26}"));
		assertThat(ids).doesNotHaveDuplicates();
	}

	/** Sends the headers of a 50 MiB PUT and 64 KiB of it, then expects the 401 before sending more. */
	private void assertRefusedWhileSending(String contentType) throws Exception {
		List<String> head = send("PUT", "/api/v1/staff/owner-leads/01JLEADPLATFORMAAAAAAAAAAA", contentType,
				64 * 1024);

		assertTrue(!head.isEmpty() && head.get(0).contains(" 401"), String.valueOf(head));
	}

	private List<String> send(String method, String path, String contentType, int bodyBytesToSend) throws Exception {
		try (Socket socket = new Socket("localhost", port)) {
			socket.setSoTimeout(5_000);
			OutputStream out = socket.getOutputStream();
			StringBuilder request = new StringBuilder(method + " " + path + " HTTP/1.1\r\nHost: localhost:" + port
					+ "\r\n");
			if (contentType != null) {
				request.append("Content-Type: ").append(contentType).append("\r\nContent-Length: ").append(FIFTY_MIB)
						.append("\r\n");
			}
			request.append("\r\n");
			out.write(request.toString().getBytes(StandardCharsets.US_ASCII));
			byte[] chunk = new byte[1024];
			java.util.Arrays.fill(chunk, (byte) 'a');
			for (int sent = 0; sent < bodyBytesToSend; sent += chunk.length) {
				out.write(chunk);
			}
			out.flush();
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(),
					StandardCharsets.US_ASCII));
			List<String> lines = new ArrayList<>();
			try {
				for (String line = in.readLine(); line != null && !line.isEmpty(); line = in.readLine()) {
					lines.add(line);
				}
			} catch (SocketTimeoutException e) {
				// no answer while the body is incomplete: the server is reading it
			}
			return lines;
		}
	}
}
