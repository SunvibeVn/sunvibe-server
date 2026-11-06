package vn.sunvibe.survey.core.presentation.controller;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.survey.core.application.exception.MediaMethodNotAllowedException;
import vn.sunvibe.survey.core.application.query.MediaRead;
import vn.sunvibe.survey.core.application.usecase.SurveyMediaReadUseCase;
import vn.sunvibe.survey.core.presentation.converter.SurveyViewerResolver;

/**
 * The content reads of survey photos (survey-media spec 7.1, ADR-011 decision 2, A-24, A-25, A-30): GET only (HEAD
 * 405), the whole variant whatever {@code Range} says, the security headers of decision 2 and a name without any
 * client file name. A stream without progress for the no-progress timeout, or past its deadline, is stopped and its
 * store stream closed; no transaction or connection is open while bytes move.
 */
@RestController
@RequestMapping(SurveyMediaController.STAFF)
@Tag(name = "Survey photos (staff)")
// survey-media spec 7: sale staff and ADMIN only; SUPER_ADMIN passes hasRole('ADMIN') through the role hierarchy
@PreAuthorize("hasAnyRole('SALE','SALE_LEADER','SALES_MANAGER','ADMIN')")
public class SurveyMediaReadController {

    public static final String READ = "/survey-media/{mediaId}/content/{variant}";

    private static final int CHUNK = 64 * 1024;
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "media-read-watchdog");
        thread.setDaemon(true);
        return thread;
    });

    private final SurveyMediaReadUseCase readUseCase;
    private final SurveyViewerResolver viewerResolver;

    public SurveyMediaReadController(SurveyMediaReadUseCase readUseCase, SurveyViewerResolver viewerResolver) {
        this.readUseCase = readUseCase;
        this.viewerResolver = viewerResolver;
    }

    /**
     * GET /api/v1/staff/survey-media/{mediaId}/content/{variant}
     * <p>
     * One variant of a servable photo (spec 5.4 content GET order).
     */
    @GetMapping(READ)
    @Operation(operationId = "getSurveyMediaContent",
            summary = "Read one image of a survey photo (anyone who can see the lead)",
            description = "The whole JPEG variant (Range is ignored). Serving off answers 503 at once. Each read is "
                    + "access-logged (a thumbnail is covered by the list's row) and counted in the hourly budget of "
                    + "its variant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The image",
                    content = @Content(mediaType = "image/jpeg", schema = @Schema(type = "string",
                            format = "binary"))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED: field variant (not thumbnail, "
                    + "preview or full) or download (not true or false, or not on full)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such photo, or the caller may not see its "
                    + "lead",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "405", description = "METHOD_NOT_ALLOWED: HEAD; GET only",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "MEDIA_NOT_AVAILABLE (field status): not ready, "
                    + "removed, held, deleted or missing",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED: field concurrency (6 reads at once) or "
                    + "budget (the hourly budget of the variant)",
                    headers = @Header(name = "Retry-After", description = "Seconds to wait",
                            schema = @Schema(type = "integer")),
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "MEDIA_SERVING_DISABLED or MEDIA_SERVER_BUSY (with "
                    + "Retry-After); MEDIA_STORE_UNAVAILABLE: try again later",
                    headers = @Header(name = "Retry-After", description = "Seconds to wait",
                            schema = @Schema(type = "integer")),
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void read(@PathVariable("mediaId") String mediaId, @PathVariable("variant") String variant,
                     @Parameter(description = "true for an attachment; only on full")
                     @RequestParam(value = "download", required = false) String download,
                     HttpServletResponse response) throws IOException {
        readUseCase.requireServing();
        try (MediaRead read = readUseCase.open(viewerResolver.current(), viewerResolver.currentCanHold(), mediaId,
                variant, download)) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("image/jpeg");
            response.setContentLengthLong(read.length());
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Security-Policy", "default-src 'none'; sandbox");
            response.setHeader("Cross-Origin-Resource-Policy", "same-site");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, (read.download() ? "attachment" : "inline")
                    + "; filename=\"photo-" + read.mediaId() + ".jpg\"");
            stream(read, response.getOutputStream());
        }
    }

    /** spec 7.1: GET only; Spring would otherwise answer HEAD through the GET mapping. It exists only to refuse. */
    @Hidden
    @RequestMapping(value = READ, method = RequestMethod.HEAD)
    public void head() {
        throw new MediaMethodNotAllowedException();
    }

    /**
     * spec A-25: copies the variant, closing the store stream when no chunk was written for the no-progress timeout or
     * the deadline passed; the answer then ends early and the client sees an incomplete body. Package-private for its
     * unit test.
     */
    static void stream(MediaRead read, OutputStream out) throws IOException {
        AtomicLong lastProgress = new AtomicLong(System.nanoTime());
        long noProgressNanos = read.noProgressTimeout().toNanos();
        ScheduledFuture<?> watchdog = WATCHDOG.scheduleAtFixedRate(() -> {
            if (System.nanoTime() - lastProgress.get() > noProgressNanos || Instant.now().isAfter(read.deadline())) {
                // spec A-25: end the store stream only; the request thread releases the slots when it returns
                read.abortContent();
            }
        }, 200, 200, TimeUnit.MILLISECONDS);
        try {
            InputStream in = read.content();
            byte[] buffer = new byte[CHUNK];
            for (int n = in.read(buffer); n != -1; n = in.read(buffer)) {
                out.write(buffer, 0, n);
                lastProgress.set(System.nanoTime());
                if (Instant.now().isAfter(read.deadline())) {
                    throw new IOException("The survey photo stream passed its deadline.");
                }
            }
            out.flush();
        } catch (IOException e) {
            // ids only: the client or the watchdog ended the stream; the answer stays incomplete
            throw new IOException("The survey photo stream of " + read.mediaId() + " stopped.");
        } finally {
            watchdog.cancel(false);
        }
    }
}
