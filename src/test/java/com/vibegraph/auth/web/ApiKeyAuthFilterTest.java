package com.vibegraph.auth.web;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.vibegraph.auth.domain.ApiKey;
import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.repository.ApiKeyRepository;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.auth.service.AccountSettingsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        UUID userId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        ApiKey key = ApiKey.builder().id(keyId).userId(userId).keyPrefix("vbg_12345678")
                .keyHash("hash").build();
        when(keyRepository.findAll()).thenReturn(List.of(key));
        when(encoder.matches("vbg_123456789012345", "hash")).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder()
                .id(userId).email("cli@example.test").role(Role.USER).build()));
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(keyRepository, userRepository, accountSettings, encoder);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "vbg_123456789012345");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(request.getAttribute(ApiKeyAuthFilter.API_KEY_REF_ATTRIBUTE))
                .isEqualTo(keyId + ":vbg_12345678");
        verify(accountSettings).assertNotBlocked(userId);
        verify(keyRepository).save(key);
    }
}
