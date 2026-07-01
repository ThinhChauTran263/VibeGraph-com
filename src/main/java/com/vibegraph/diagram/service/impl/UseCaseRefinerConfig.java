package com.vibegraph.diagram.service.impl;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibegraph.ai.ResilientChatClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Wires exactly one {@link UseCaseSemanticRefiner} (Requirement 5).
 *
 * <ul>
 *   <li>{@link LlmUseCaseRefiner} — when {@code vibegraph.usecase.llm.enabled=true} AND a
 *       {@link ResilientChatClient} is available (at least one Gemini API key + model configured);</li>
 *   <li>{@link NoopUseCaseRefiner} — otherwise (deterministic default).</li>
 * </ul>
 *
 * <p>All API-key/model failover lives in {@link ResilientChatClient}; this config only decides
 * whether the LLM tier is on.
 */
@Configuration
@Slf4j
public class UseCaseRefinerConfig {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public UseCaseSemanticRefiner useCaseSemanticRefiner(
            ObjectProvider<ResilientChatClient> chatClientProvider,
            @Value("${vibegraph.usecase.llm.enabled:false}") boolean llmEnabled) {
        ResilientChatClient chatClient = chatClientProvider.getIfAvailable();
        if (llmEnabled && chatClient != null && chatClient.isAvailable()) {
            log.info("Tier 2 use-case refiner: LLM ENABLED (chatClient={}).",
                    chatClient.getClass().getSimpleName());
            return new LlmUseCaseRefiner(chatClient, objectMapper);
        }
        log.info("Tier 2 use-case refiner: NOOP (llmEnabled={}, chatClientAvailable={}).",
                llmEnabled, chatClient != null && chatClient.isAvailable());
        return new NoopUseCaseRefiner();
    }
}
