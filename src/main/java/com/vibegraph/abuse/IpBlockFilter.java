package com.vibegraph.abuse;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibegraph.common.dto.response.ApiResponse;
import com.vibegraph.common.dto.response.ErrorResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class IpBlockFilter extends OncePerRequestFilter {

    private final ClientAddressResolver addressResolver;
    private final IpBlockService blockService;
    private final ObjectMapper objectMapper;

    public IpBlockFilter(ClientAddressResolver addressResolver, IpBlockService blockService,
            ObjectMapper objectMapper) {
        this.addressResolver = addressResolver;
        this.blockService = blockService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String address = addressResolver.resolve(request);
        var activeBlock = blockService.findActive(address);
        if (activeBlock.isPresent()) {
            writeBlocked(response, activeBlock.get().getSafeReason());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeBlocked(HttpServletResponse response, String reason) throws IOException {
        response.setStatus(403);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(ErrorResponse.builder()
                .code("IP_BLOCKED")
                .message(reason == null || reason.isBlank() ? "Request blocked by administrator" : reason)
                .build()));
    }
}
