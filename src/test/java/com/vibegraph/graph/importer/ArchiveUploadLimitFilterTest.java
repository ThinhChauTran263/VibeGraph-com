package com.vibegraph.graph.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.service.AccountQuotaSnapshot;
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.graph.importer.config.ArchiveImportProperties;

/**
 * A1/B1 (revised): the declared Content-Length is rejected against the SERVER HARD LIMIT
 * BEFORE multipart spooling. The account quota is checked exactly, against the materialized
 * {@code .java} bytes, after extraction - so the gate must NOT pre-reject an upload that
 * merely exceeds the remaining quota (a 50MB archive holding 3MB of .java has to get
 * through). The single quota-based early rejection left is an exhausted account
 * (remaining 0), where no false positive is possible.
 */
class ArchiveUploadLimitFilterTest {

    private static final long MIB = 1024L * 1024L;
    private static final long HARD_LIMIT = DataSize.ofMegabytes(200).toBytes();
    private static final long HOST_CEILING = DataSize.ofMegabytes(2050).toBytes();

    private static final String IMPORT_PATH = "/api/projects/import-archive";
    private static final UUID USER_ID = UUID.randomUUID();

    private CurrentUser currentUser;
    private AccountSettingsService accountSettingsService;
    private ArchiveUploadLimitFilter filter;

    @BeforeEach
    void setUp() {
        currentUser = mock(CurrentUser.class);
        accountSettingsService = mock(AccountSettingsService.class);
        ArchiveImportProperties properties = new ArchiveImportProperties();
        properties.setMaxSize(DataSize.ofBytes(HARD_LIMIT));
        filter = new ArchiveUploadLimitFilter(accountSettingsService, currentUser,
                new ObjectMapper(), properties, DataSize.ofBytes(HOST_CEILING));
        when(currentUser.id()).thenReturn(USER_ID);
    }

    private HttpServletRequest archivePost(long contentLength) {
        return request("POST", IMPORT_PATH, contentLength);
    }

    private HttpServletRequest request(String method, String uri, long contentLength) {
        // MockHttpServletRequest has no long-typed content-length setter and int overflows
        // beyond 2 GiB, so the request is mocked directly.
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getContextPath()).thenReturn("");
        when(request.getContentLengthLong()).thenReturn(contentLength);
        return request;
    }

    private AccountQuotaSnapshot snapshot(long limitBytes) {
        return snapshot(limitBytes, 0L);
    }

    private AccountQuotaSnapshot snapshot(long limitBytes, long usedBytes) {
        long remaining = usedBytes >= limitBytes ? 0L : limitBytes - usedBytes;
        return new AccountQuotaSnapshot(usedBytes, limitBytes, remaining, "X", "X", null);
    }

    @Test
    @DisplayName("Declared above the server hard limit -> 413 and the chain never runs (no spool)")
    void uploadAboveHardLimitIsRejectedBeforeAnyChainStep() throws Exception {
        when(accountSettingsService.quotaSnapshot(USER_ID)).thenReturn(snapshot(100 * MIB));
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(archivePost(HARD_LIMIT + MIB), response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
        assertThat(chain.getRequest()).as("chain must not run -> nothing downstream can spool").isNull();
    }

    @Test
    @DisplayName("Declared above remaining quota but below the hard limit -> passes (exact quota check runs after extraction)")
    void uploadAboveRemainingQuotaButBelowHardLimitPasses() throws Exception {
        // 95/100 MiB used: the old gate rejected a 50 MiB upload here even though the
        // materialized .java footprint could be tiny; the revised gate lets it through.
        when(accountSettingsService.quotaSnapshot(USER_ID)).thenReturn(snapshot(100 * MIB, 95 * MIB));
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(archivePost(50 * MIB), response, chain);

        assertThat(chain.getRequest()).as("request must continue to the controller").isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Missing Content-Length -> falls back to the hard limit downstream, no pre-rejection, no quota lookup")
    void missingContentLengthFallsBackToHardLimit() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(archivePost(-1L), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(accountSettingsService, never()).quotaSnapshot(any());
    }

    @Test
    @DisplayName("Exhausted quota (remaining 0, finite plan) rejects every declared byte - not mistaken for ENTERPRISE")
    void exhaustedQuotaIsNotTreatedAsUnlimited() throws Exception {
        when(accountSettingsService.quotaSnapshot(USER_ID)).thenReturn(snapshot(100 * MIB, 100 * MIB));
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(archivePost(1L), response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("ENTERPRISE (limit 0) -> hard limit bounds the upload, not the host ceiling")
    void zeroPlanLimitIsBoundedByHardLimit() throws Exception {
        when(accountSettingsService.quotaSnapshot(USER_ID)).thenReturn(snapshot(0L));
        MockFilterChain pass = new MockFilterChain();
        filter.doFilter(archivePost(HARD_LIMIT - 1), new MockHttpServletResponse(), pass);
        assertThat(pass.getRequest()).isNotNull();

        MockFilterChain reject = new MockFilterChain();
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(archivePost(HARD_LIMIT + 1), rejected, reject);
        assertThat(rejected.getStatus()).isEqualTo(413);
        assertThat(reject.getRequest()).isNull();
    }

    @Test
    @DisplayName("Large plan is bounded by the hard limit, never wider")
    void largePlanIsBoundedByHardLimit() throws Exception {
        when(accountSettingsService.quotaSnapshot(USER_ID)).thenReturn(snapshot(4096 * MIB, 0L));
        MockFilterChain pass = new MockFilterChain();
        filter.doFilter(archivePost(HARD_LIMIT - 1), new MockHttpServletResponse(), pass);
        assertThat(pass.getRequest()).isNotNull();

        MockFilterChain reject = new MockFilterChain();
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(archivePost(HARD_LIMIT + 1), rejected, reject);
        assertThat(rejected.getStatus()).isEqualTo(413);
        assertThat(reject.getRequest()).isNull();
    }

    @Test
    @DisplayName("Unresolvable identity -> hard limit, never fail-open")
    void unresolvableIdentityUsesHardLimit() throws Exception {
        when(currentUser.id()).thenThrow(new RuntimeException("no principal"));
        MockFilterChain pass = new MockFilterChain();
        filter.doFilter(archivePost(HARD_LIMIT - 1), new MockHttpServletResponse(), pass);
        assertThat(pass.getRequest()).isNotNull();

        MockFilterChain reject = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(archivePost(HARD_LIMIT + 1), response, reject);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(reject.getRequest()).isNull();
    }

    @Test
    @DisplayName("Other routes and methods are untouched")
    void unrelatedRequestsPassWithoutQuotaLookup() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("POST", "/api/projects/import-github", Long.MAX_VALUE / 2),
                new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(accountSettingsService, never()).quotaSnapshot(any());
    }

    @Test
    @DisplayName("Percent-encoded import path is still guarded: Spring routes the decoded path, so must this filter")
    void percentEncodedImportPathIsStillGuarded() throws Exception {
        when(accountSettingsService.quotaSnapshot(USER_ID)).thenReturn(snapshot(100 * MIB));
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // "%69" is "i": the raw URI differs from IMPORT_PATH but Spring's handler mapping
        // decodes before matching, so this request DOES reach the archive import handler.
        filter.doFilter(request("POST", "/api/projects/%69mport-archive", HARD_LIMIT + MIB), response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).as("chain must not run -> nothing downstream can spool").isNull();
    }

    @Test
    @DisplayName("Undecodable escape sequence passes through instead of raising a 500 out of the filter")
    void malformedEscapeSequencePassesThrough() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("POST", "/api/projects/%zzmport-archive", HARD_LIMIT + MIB),
                new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(accountSettingsService, never()).quotaSnapshot(any());
    }
}
