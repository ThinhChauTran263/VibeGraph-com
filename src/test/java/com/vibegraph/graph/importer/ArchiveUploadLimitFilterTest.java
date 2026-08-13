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

/**
 * A1/B1: the declared Content-Length must be rejected against the caller's effective plan limit
 * BEFORE multipart spooling — acceptance criterion 1 requires that no body byte reaches disk.
 * With the chain never invoked, nothing downstream (multipart resolver included) can run, which
 * is exactly the "spool directory does not grow" property at unit level.
 */
class ArchiveUploadLimitFilterTest {

    private static final long MIB = 1024L * 1024L;
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
        filter = new ArchiveUploadLimitFilter(accountSettingsService, currentUser,
                new ObjectMapper(), DataSize.ofMegabytes(2050));
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
    @DisplayName("FREE plan: declared 500 MiB > 100 MiB remaining -> 413 and the chain never runs (no spool)")
    void freePlanOversizedUploadIsRejectedBeforeAnyChainStep() throws Exception {
        when(accountSettingsService.quotaSnapshot(USER_ID)).thenReturn(snapshot(100 * MIB));
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(archivePost(500 * MIB), response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
        assertThat(chain.getRequest()).as("chain must not run -> nothing downstream can spool").isNull();
    }

    @Test
    @DisplayName("MAX plan: declared 1.5 GiB <= 2 GiB limit -> passes through (no high-plan regression)")
    void maxPlanWithinLimitPasses() throws Exception {
        when(accountSettingsService.quotaSnapshot(USER_ID)).thenReturn(snapshot(2048 * MIB));
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(archivePost(1536 * MIB), response, chain);

        assertThat(chain.getRequest()).as("request must continue to the controller").isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Missing Content-Length -> falls back to host ceiling downstream, no pre-rejection, no quota lookup")
    void missingContentLengthFallsBackToHostCeiling() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(archivePost(-1L), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(accountSettingsService, never()).quotaSnapshot(any());
    }

    @Test
    @DisplayName("Nearly exhausted quota: 95/100 MiB used, declared 50 MiB -> 413 (remaining, not plan total)")
    void nearlyExhaustedQuotaRejectsUploadLargerThanRemaining() throws Exception {
        when(accountSettingsService.quotaSnapshot(USER_ID)).thenReturn(snapshot(100 * MIB, 95 * MIB));
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(archivePost(50 * MIB), response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull();

        // A declared size within the 5 MiB remainder still passes the pre-check.
        MockFilterChain pass = new MockFilterChain();
        filter.doFilter(archivePost(4 * MIB), new MockHttpServletResponse(), pass);
        assertThat(pass.getRequest()).isNotNull();
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
    @DisplayName("ENTERPRISE (limit 0) -> host ceiling bounds the upload")
    void zeroPlanLimitFallsBackToHostCeiling() throws Exception {
        when(accountSettingsService.quotaSnapshot(USER_ID)).thenReturn(snapshot(0L));
        MockFilterChain pass = new MockFilterChain();
        filter.doFilter(archivePost(HOST_CEILING - 1), new MockHttpServletResponse(), pass);
        assertThat(pass.getRequest()).isNotNull();

        MockFilterChain reject = new MockFilterChain();
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(archivePost(HOST_CEILING + 1), rejected, reject);
        assertThat(rejected.getStatus()).isEqualTo(413);
        assertThat(reject.getRequest()).isNull();
    }

    @Test
    @DisplayName("Remaining above the host ceiling is capped by the host ceiling, never wider")
    void remainingAboveHostCeilingIsCapped() throws Exception {
        when(accountSettingsService.quotaSnapshot(USER_ID)).thenReturn(snapshot(4096 * MIB, 0L));
        MockFilterChain pass = new MockFilterChain();
        filter.doFilter(archivePost(HOST_CEILING), new MockHttpServletResponse(), pass);
        assertThat(pass.getRequest()).isNotNull();

        MockFilterChain reject = new MockFilterChain();
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(archivePost(HOST_CEILING + 1), rejected, reject);
        assertThat(rejected.getStatus()).isEqualTo(413);
        assertThat(reject.getRequest()).isNull();
    }

    @Test
    @DisplayName("Unresolvable identity -> host ceiling, never fail-open")
    void unresolvableIdentityUsesHostCeiling() throws Exception {
        when(currentUser.id()).thenThrow(new RuntimeException("no principal"));
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(archivePost(HOST_CEILING + 1), response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull();
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
        // Matching on the raw URI let it skip the pre-check and spool to the host ceiling.
        filter.doFilter(request("POST", "/api/projects/%69mport-archive", 500 * MIB), response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).as("chain must not run -> nothing downstream can spool").isNull();
    }

    @Test
    @DisplayName("Undecodable escape sequence passes through instead of raising a 500 out of the filter")
    void malformedEscapeSequencePassesThrough() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("POST", "/api/projects/%zzmport-archive", 500 * MIB),
                new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(accountSettingsService, never()).quotaSnapshot(any());
    }
}
