package com.vibegraph.auth.web;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.vibegraph.auth.domain.entity.ApiKey;
import com.vibegraph.auth.domain.ApiKeyDisabledBy;
import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.domain.entity.User;
import com.vibegraph.auth.repository.ApiKeyRepository;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.auth.service.AccountSettingsService;

class ApiKeyAuthFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_validApiKey_authenticatesMcpOrCliAndExposesSafeReference() throws Exception {
        ApiKeyRepository keyRepository = mock(ApiKeyRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        AccountSettingsService accountSettings = mock(AccountSettingsService.class);
        com.vibegraph.auth.repository.ProjectOwnershipRepository projectOwnershipRepository =
                mock(com.vibegraph.auth.repository.ProjectOwnershipRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        UUID userId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        ApiKey key = ApiKey.builder().id(keyId).userId(userId).projectId("project-1")
                .keyPrefix("vbg_12345678").keyHash("hash").build();
        when(keyRepository.findTop6ByKeyPrefixAndDeletedAtIsNullAndDisabledAtIsNullOrderByIdAsc(
                "vbg_12345678")).thenReturn(List.of(key));
        when(encoder.matches("vbg_123456789012345", "hash")).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder()
                .id(userId).email("cli@example.test").role(Role.USER).build()));
        when(projectOwnershipRepository.findOwnerId("project-1"))
                .thenReturn(Optional.of(userId));
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(
                keyRepository, userRepository, projectOwnershipRepository, accountSettings, encoder);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "vbg_123456789012345");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(request.getAttribute(ApiKeyAuthFilter.API_KEY_REF_ATTRIBUTE))
                .isEqualTo(keyId + ":vbg_12345678");
        assertThat(request.getAttribute(ApiKeyAuthFilter.API_KEY_CONTEXT_ATTRIBUTE))
                .isEqualTo(new ApiKeyRequestContext(keyId + ":vbg_12345678", "project-1"));
        verify(accountSettings).assertNotBlocked(userId);
        verify(keyRepository).save(key);
    }

    @Test
    void doFilter_currentProjectRoutes_authenticateWithBoundKey() throws Exception {
        ApiKeyRepository keyRepository = mock(ApiKeyRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        AccountSettingsService accountSettings = mock(AccountSettingsService.class);
        var projectRepository = mock(com.vibegraph.auth.repository.ProjectOwnershipRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        UUID userId = UUID.randomUUID();
        ApiKey key = ApiKey.builder().id(UUID.randomUUID()).userId(userId).projectId("project-1")
                .keyPrefix("vbg_12345678").keyHash("hash").build();
        when(keyRepository.findTop6ByKeyPrefixAndDeletedAtIsNullAndDisabledAtIsNullOrderByIdAsc(
                "vbg_12345678")).thenReturn(List.of(key));
        when(encoder.matches("vbg_123456789012345", "hash")).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder()
                .id(userId).email("cli@example.test").role(Role.USER).build()));
        when(projectRepository.findOwnerId("project-1")).thenReturn(Optional.of(userId));
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(
                keyRepository, userRepository, projectRepository, accountSettings, encoder);

        for (String method : List.of("GET", "POST")) {
            clearContext();
            String route = method.equals("GET") ? "/api/projects/current" : "/api/projects/current/analyze";
            MockHttpServletRequest request = new MockHttpServletRequest(method, route);
            request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "vbg_123456789012345");
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        }
    }

    @Test
    void doFilter_repeatedRequests_throttleLastUsedWrite() throws Exception {
        ApiKeyRepository keyRepository = mock(ApiKeyRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        AccountSettingsService accountSettings = mock(AccountSettingsService.class);
        var projectRepository = mock(com.vibegraph.auth.repository.ProjectOwnershipRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        UUID userId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        ApiKey key = ApiKey.builder().id(keyId).userId(userId).projectId("project-1")
                .keyPrefix("vbg_12345678").keyHash("hash").build();
        when(keyRepository.findTop6ByKeyPrefixAndDeletedAtIsNullAndDisabledAtIsNullOrderByIdAsc(
                "vbg_12345678")).thenReturn(List.of(key));
        when(encoder.matches("vbg_123456789012345", "hash")).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder()
                .id(userId).email("cli@example.test").role(Role.USER).build()));
        when(projectRepository.findOwnerId("project-1")).thenReturn(Optional.of(userId));
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(
                keyRepository, userRepository, projectRepository, accountSettings, encoder);

        for (int i = 0; i < 3; i++) {
            SecurityContextHolder.clearContext();
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
            request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "vbg_123456789012345");
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        }

        // F6: all three requests authenticate, but lastUsedAt is persisted only once
        // inside the throttle window instead of once per request.
        verify(keyRepository, times(1)).save(key);
    }

    @Test
    void doFilter_accountRoute_doesNotAuthenticateApiKey() throws Exception {
        ApiKeyRepository keyRepository = mock(ApiKeyRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        AccountSettingsService accountSettings = mock(AccountSettingsService.class);
        var projectRepository = mock(com.vibegraph.auth.repository.ProjectOwnershipRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(
                keyRepository, userRepository, projectRepository, accountSettings, encoder);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/account/profile");
        request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "vbg_123456789012345");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(keyRepository, org.mockito.Mockito.never())
                .findTop6ByKeyPrefixAndDeletedAtIsNullAndDisabledAtIsNullOrderByIdAsc(
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void doFilter_unboundKey_failsClosed() throws Exception {
        ApiKeyRepository keyRepository = mock(ApiKeyRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        AccountSettingsService accountSettings = mock(AccountSettingsService.class);
        var projectRepository = mock(com.vibegraph.auth.repository.ProjectOwnershipRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        ApiKey key = ApiKey.builder().id(UUID.randomUUID()).userId(UUID.randomUUID())
                .keyPrefix("vbg_12345678").keyHash("hash").build();
        when(keyRepository.findTop6ByKeyPrefixAndDeletedAtIsNullAndDisabledAtIsNullOrderByIdAsc(
                "vbg_12345678")).thenReturn(List.of(key));
        when(encoder.matches("vbg_123456789012345", "hash")).thenReturn(true);
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(
                keyRepository, userRepository, projectRepository, accountSettings, encoder);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "vbg_123456789012345");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_mixedJwtAndInvalidApiKey_failsClosed() throws Exception {
        ApiKeyRepository keyRepository = mock(ApiKeyRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        AccountSettingsService accountSettings = mock(AccountSettingsService.class);
        var projectRepository = mock(com.vibegraph.auth.repository.ProjectOwnershipRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(keyRepository.findTop6ByKeyPrefixAndDeletedAtIsNullAndDisabledAtIsNullOrderByIdAsc(
                "vbg_12345678")).thenReturn(List.of());
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(
                keyRepository, userRepository, projectRepository, accountSettings, encoder);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "vbg_123456789012345");
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("jwt", null));

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_disabledDeletedOrAdminLockedCandidate_failsClosed() throws Exception {
        for (ApiKey key : List.of(
                ApiKey.builder().id(UUID.randomUUID()).userId(UUID.randomUUID()).projectId("project-1")
                        .keyPrefix("vbg_12345678").keyHash("hash").disabledAt(Instant.now())
                        .disabledBy(ApiKeyDisabledBy.USER).build(),
                ApiKey.builder().id(UUID.randomUUID()).userId(UUID.randomUUID()).projectId("project-1")
                        .keyPrefix("vbg_12345678").keyHash("hash").deletedAt(Instant.now()).build(),
                ApiKey.builder().id(UUID.randomUUID()).userId(UUID.randomUUID()).projectId("project-1")
                        .keyPrefix("vbg_12345678").keyHash("hash").disabledAt(Instant.now())
                        .disabledBy(ApiKeyDisabledBy.ADMIN).build())) {
            clearContext();
            ApiKeyRepository keyRepository = mock(ApiKeyRepository.class);
            UserRepository userRepository = mock(UserRepository.class);
            AccountSettingsService accountSettings = mock(AccountSettingsService.class);
            var projectRepository = mock(com.vibegraph.auth.repository.ProjectOwnershipRepository.class);
            PasswordEncoder encoder = mock(PasswordEncoder.class);
            when(keyRepository.findTop6ByKeyPrefixAndDeletedAtIsNullAndDisabledAtIsNullOrderByIdAsc(
                    "vbg_12345678")).thenReturn(List.of(key));
            ApiKeyAuthFilter filter = new ApiKeyAuthFilter(
                    keyRepository, userRepository, projectRepository, accountSettings, encoder);
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
            request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "vbg_123456789012345");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    @Test
    void doFilter_expiredCandidate_failsClosed() throws Exception {
        ApiKeyRepository keyRepository = mock(ApiKeyRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        AccountSettingsService accountSettings = mock(AccountSettingsService.class);
        var projectRepository = mock(com.vibegraph.auth.repository.ProjectOwnershipRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        ApiKey key = ApiKey.builder().id(UUID.randomUUID()).userId(UUID.randomUUID()).projectId("project-1")
                .keyPrefix("vbg_12345678").keyHash("hash").expiresAt(Instant.now().minusSeconds(1)).build();
        when(keyRepository.findTop6ByKeyPrefixAndDeletedAtIsNullAndDisabledAtIsNullOrderByIdAsc(
                "vbg_12345678")).thenReturn(List.of(key));
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(
                keyRepository, userRepository, projectRepository, accountSettings, encoder);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "vbg_123456789012345");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_tooManyPrefixCandidates_failsClosedBeforeBcrypt() throws Exception {
        ApiKeyRepository keyRepository = mock(ApiKeyRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        AccountSettingsService accountSettings = mock(AccountSettingsService.class);
        var projectRepository = mock(com.vibegraph.auth.repository.ProjectOwnershipRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        List<ApiKey> candidates = java.util.stream.IntStream.range(0, 6)
                .mapToObj(index -> ApiKey.builder().id(UUID.randomUUID()).userId(UUID.randomUUID())
                        .projectId("project-" + index).keyPrefix("vbg_12345678").keyHash("hash-" + index).build())
                .toList();
        when(keyRepository.findTop6ByKeyPrefixAndDeletedAtIsNullAndDisabledAtIsNullOrderByIdAsc(
                "vbg_12345678")).thenReturn(candidates);
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(
                keyRepository, userRepository, projectRepository, accountSettings, encoder);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "vbg_123456789012345");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        verify(encoder, org.mockito.Mockito.never()).matches(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }
}
