package com.vibegraph.graph.importer;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.service.AccountQuotaSnapshot;
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.common.dto.response.ApiResponse;
import com.vibegraph.common.dto.response.ErrorResponse;
import com.vibegraph.graph.importer.config.ArchiveImportProperties;

/**
 * A1/B1: reject archive uploads whose declared {@code Content-Length} exceeds the server hard
 * limit BEFORE Spring's multipart resolver spools the body to disk.
 *
 * <p>The ceiling is the configured import hard limit ({@code vibegraph.import.archive.max-size},
 * capped by the host multipart ceiling), NOT the account quota: a 50MB archive holding 3MB of
 * {@code .java} must not be rejected up front just because the declared size exceeds the
 * remaining quota. The exact account-quota check runs after extraction, against the
 * materialized {@code .java} bytes, in {@code ArchiveImportServiceImpl}. The one quota-based
 * early rejection kept here is an EXHAUSTED account ({@code remainingBytes == 0}): any
 * materialized byte would exceed it, so rejecting every declared byte can never be a false
 * positive and preserves the audit's anti-amplification intent for the cheapest abuse case.
 *
 * <p>Placement: a plain servlet filter ordered AFTER the Spring Security chain (-100) — so the
 * SecurityContext is populated — and BEFORE the DispatcherServlet, which is where multipart
 * resolution (and therefore disk spooling) happens. Interceptors cannot do this: the dispatcher
 * resolves multipart before running them.
 *
 * <p>Fail-safe rules: a missing/invalid {@code Content-Length} or an unresolvable identity falls
 * back to the hard limit, never to "allow everything". Chunked uploads keep the existing
 * host-level multipart limits as their only bound, exactly as before. Unlimited plans
 * ({@code limitBytes == 0}, ENTERPRISE) are bounded by the hard limit alone.
 */
@Component
public class ArchiveUploadLimitFilter extends OncePerRequestFilter implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(ArchiveUploadLimitFilter.class);

    /** Ordered after the security chain (-100); identity must already be resolved. */
    private static final int ORDER = 0;

    private static final String ARCHIVE_IMPORT_PATH = "/api/projects/import-archive";

    /**
     * Resolves the DECODED path within the application. {@code getRequestURI()} is the raw URI,
     * but Spring's handler mapping (and Spring Security's matching) run on the decoded path, so
     * comparing the raw URI to a literal is bypassable: {@code POST /api/projects/%69mport-archive}
     * still reaches the import handler while failing {@code equals()} against the plain path —
     * which would re-open the very multipart spool this filter exists to prevent.
     */
    private static final UrlPathHelper PATH_HELPER = UrlPathHelper.defaultInstance;

    private final AccountSettingsService accountSettingsService;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;
    private final ArchiveImportProperties archiveImportProperties;
    private final long hostCeilingBytes;

    public ArchiveUploadLimitFilter(AccountSettingsService accountSettingsService,
                                    CurrentUser currentUser,
                                    ObjectMapper objectMapper,
                                    ArchiveImportProperties archiveImportProperties,
                                    @Value("${spring.servlet.multipart.max-request-size:2050MB}") DataSize maxRequestSize) {
        this.accountSettingsService = accountSettingsService;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
        this.archiveImportProperties = archiveImportProperties;
        this.hostCeilingBytes = maxRequestSize.toBytes();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!HttpMethod.POST.matches(request.getMethod())
                || !ARCHIVE_IMPORT_PATH.equals(decodedPathOrNull(request))) {
            chain.doFilter(request, response);
            return;
        }
        long declared = request.getContentLengthLong();
        if (declared <= 0) {
            // Missing/invalid Content-Length (e.g. chunked transfer): nothing trustworthy to
            // compare, so the host multipart ceiling continues to govern downstream. Not a
            // fail-open: the request is still bounded, just not pre-rejected here.
            chain.doFilter(request, response);
            return;
        }
        long ceiling = ceilingForCurrentUser();
        if (declared > ceiling) {
            rejectPayloadTooLarge(response, declared, ceiling);
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * The decoded, context-path-stripped request path, or {@code null} when it cannot be decoded.
     * An undecodable escape sequence can never reach the import handler either — Spring's own
     * mapping decode fails on the same input — so treating it as "not our path" stays safe and
     * keeps a malformed URI from turning into a 500 raised out of a servlet filter.
     */
    private String decodedPathOrNull(HttpServletRequest request) {
        try {
            return PATH_HELPER.getPathWithinApplication(request);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * The server hard limit (import max-size capped by the host multipart ceiling), except an
     * exhausted quota ({@code limitBytes > 0}, {@code remainingBytes == 0}) yields ceiling 0 so
     * every declared byte is rejected up front - a case where no false positive is possible.
     * Unlimited plans and unresolvable identities are bounded by the hard limit alone; the exact
     * quota check runs after extraction.
     */
    private long ceilingForCurrentUser() {
        AccountQuotaSnapshot snapshot;
        try {
            snapshot = accountSettingsService.quotaSnapshot(currentUser.id());
        } catch (RuntimeException ex) {
            return hardLimitBytes();
        }
        if (snapshot.limitBytes() > 0 && snapshot.remainingBytes() <= 0) {
            return 0L;
        }
        return hardLimitBytes();
    }

    private long hardLimitBytes() {
        return Math.min(archiveImportProperties.getMaxSize().toBytes(), hostCeilingBytes);
    }

    private void rejectPayloadTooLarge(HttpServletResponse response, long declared, long ceiling)
            throws IOException {
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse error = ErrorResponse.builder()
                .code("PAYLOAD_TOO_LARGE")
                .message("Declared upload size exceeds the allowed limit for this account")
                .build();
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(error));
        log.debug("Rejected archive upload: declared {} bytes exceeds ceiling {} bytes", declared, ceiling);
    }
}
