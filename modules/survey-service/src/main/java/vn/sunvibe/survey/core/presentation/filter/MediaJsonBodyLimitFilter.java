package vn.sunvibe.survey.core.presentation.filter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;

/**
 * Caps the JSON bodies of the survey-media create, removal, hold and release at 16 KiB, counted while reading and
 * before parsing (spec A-11, the create order (0) of 5.4; SV-053). A larger announced {@code Content-Length} is
 * answered 413 here, before anything is read; a body without a length (chunked) is counted while it is read and
 * {@link MediaBodyTooLargeException} stops the read past the limit, which the survey exception handler answers with
 * the same 413 body. A body without a length that is not JSON is answered 415 here, because the container could parse
 * it past the counting stream. Modelled on the owner intake's body limit.
 * <p>
 * Runs after the correlation id filter and Spring Security, so the 413 carries the correlation id and a caller without
 * a token gets its 401 first. Only the four JSON paths are filtered; the content upload has its own rules.
 */
@Component
@Order(0)
public class MediaJsonBodyLimitFilter extends OncePerRequestFilter {

    private static final Pattern FILTERED = Pattern.compile(
            "^/api/v1/staff/(survey-checklists/[^/]+/media|survey-media/[^/]+/(removal|hold|hold-release))/?$");
    /** The body types the endpoints read (those of the JSON message converter). */
    private static final List<MediaType> ACCEPTED_TYPES =
            List.of(MediaType.APPLICATION_JSON, new MediaType("application", "*+json"));

    private final JsonMapper jsonMapper;

    public MediaJsonBodyLimitFilter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /** Decoded, normalised path, the way Spring MVC routes it. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !FILTERED.matcher(UrlPathHelper.defaultInstance.getPathWithinApplication(request)).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > MediaBodyTooLargeException.MAX_BODY_BYTES) {
            writeError(response, HttpStatus.CONTENT_TOO_LARGE, ErrorResponse.of(ErrorCode.PAYLOAD_TOO_LARGE,
                    MediaBodyTooLargeException.MESSAGE, List.of()));
            return;
        }
        if (contentLength < 0 && mayCarryBody(request) && !isJson(request.getContentType())) {
            response.setHeader(HttpHeaders.ACCEPT, MediaType.toString(ACCEPTED_TYPES));
            writeError(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorResponse.of(ErrorCode.MALFORMED_REQUEST,
                    "The request cannot be processed.", List.of()));
            return;
        }
        filterChain.doFilter(new LimitedBodyRequest(request), response);
    }

    private void writeError(HttpServletResponse response, HttpStatus status, ErrorResponse<?> body)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(body));
    }

    /** A POST, or a request with a Transfer-Encoding header, which announces a body of unknown length. */
    private static boolean mayCarryBody(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod())
                || request.getHeader(HttpHeaders.TRANSFER_ENCODING) != null;
    }

    /** {@code application/json} or {@code application/*+json}; a missing or unreadable type is not JSON. */
    private static boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        try {
            MediaType type = MediaType.parseMediaType(contentType);
            return ACCEPTED_TYPES.stream().anyMatch(accepted -> accepted.includes(type));
        } catch (InvalidMediaTypeException e) {
            return false;
        }
    }

    /** The same request whose body stream fails once more than the limit has been read. */
    private static final class LimitedBodyRequest extends HttpServletRequestWrapper {

        private ServletInputStream body;

        LimitedBodyRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (body == null) {
                body = new LimitedInputStream(super.getInputStream());
            }
            return body;
        }

        /** An unknown charset is {@link UnsupportedEncodingException}, as the servlet contract says, not a 500. */
        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset;
            try {
                charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            } catch (IllegalArgumentException e) {
                // The name came from the client, so it is not repeated
                throw new UnsupportedEncodingException("The request character encoding is not supported.");
            }
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class LimitedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private long count;

        LimitedInputStream(ServletInputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            int next = delegate.read();
            if (next != -1) {
                counted(1);
            }
            return next;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = delegate.read(buffer, offset, length);
            if (read > 0) {
                counted(read);
            }
            return read;
        }

        private void counted(int bytes) {
            count += bytes;
            if (count > MediaBodyTooLargeException.MAX_BODY_BYTES) {
                throw new MediaBodyTooLargeException();
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}
