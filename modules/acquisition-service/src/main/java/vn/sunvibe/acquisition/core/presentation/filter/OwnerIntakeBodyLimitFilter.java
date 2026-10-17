package vn.sunvibe.acquisition.core.presentation.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import tools.jackson.databind.ObjectMapper;
import vn.sunvibe.acquisition.core.presentation.controller.OwnerIntakeController;
import vn.sunvibe.acquisition.core.presentation.exception.AcquisitionExceptionHandler;
import vn.sunvibe.acquisition.core.presentation.exception.PayloadTooLargeException;
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Caps the body of the public intake form at 16 KB (acquisition-owner-intake spec 5.1). A larger announced
 * {@code Content-Length} is answered 413 here, before anything is read; a body without a length (chunked) is
 * counted while it is read and {@link PayloadTooLargeException} stops the read past the limit, which the
 * acquisition exception handler answers with the same 413 body. A POST or chunked body without a length that is not
 * JSON is answered 415 here, because the container would parse it past the counting stream.
 * <p>
 * Runs after the correlation id filter and the rate limiter, and after Spring Security, so the 413 carries the
 * correlation id and the CORS headers a browser needs to read it. Only the intake path is filtered.
 */
@Component
@Order(0)
public class OwnerIntakeBodyLimitFilter extends OncePerRequestFilter {

    /** The body types the intake endpoint reads (those of the JSON message converter). */
    private static final List<MediaType> ACCEPTED_TYPES =
            List.of(MediaType.APPLICATION_JSON, new MediaType("application", "*+json"));

    private final ObjectMapper objectMapper;

    public OwnerIntakeBodyLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Decoded, normalised path, the way Spring MVC routes it (like the rate limiter). */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !OwnerIntakeController.PATH.equals(UrlPathHelper.defaultInstance.getPathWithinApplication(request));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > PayloadTooLargeException.MAX_BODY_BYTES) {
            writeError(response, HttpStatus.CONTENT_TOO_LARGE,
                    ErrorResponse.of(ErrorCode.PAYLOAD_TOO_LARGE, PayloadTooLargeException.MESSAGE, List.of()));
            return;
        }
        // Only the JSON reader goes through the counting stream: the container parses a multipart or form body
        // from the raw request, so such a body of unknown length could be any size. The endpoint takes only JSON,
        // so it is refused now with the 415 Spring MVC would give it later. Only a request that may carry such a
        // body: a POST, or any method with Transfer-Encoding; a GET without a body keeps its 405.
        if (contentLength < 0 && mayCarryBody(request) && !isJson(request.getContentType())) {
            response.setHeader(HttpHeaders.ACCEPT, MediaType.toString(ACCEPTED_TYPES));
            writeError(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorResponse.of(ErrorCode.MALFORMED_REQUEST,
                    AcquisitionExceptionHandler.REQUEST_NOT_PROCESSED_MESSAGE, List.of()));
            return;
        }
        filterChain.doFilter(new LimitedBodyRequest(request), response);
    }

    private void writeError(HttpServletResponse response, HttpStatus status, ErrorResponse<?> body)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    /**
     * A POST (the endpoint's own method; without a body it gets the same 415 from Spring MVC when it is not JSON) or
     * a request with a Transfer-Encoding header, which announces a body of unknown length.
     */
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
            if (count > PayloadTooLargeException.MAX_BODY_BYTES) {
                throw new PayloadTooLargeException();
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
