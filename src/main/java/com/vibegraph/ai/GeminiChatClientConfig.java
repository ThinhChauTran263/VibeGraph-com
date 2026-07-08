package com.vibegraph.ai;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

import com.google.genai.Client;

import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds the {@link ResilientChatClient} bean: one {@link GoogleGenAiChatModel} per configured API
 * key (the key is fixed inside each model's {@code Client}), wrapped in a key×model failover matrix.
 *
 * <p>OFF by default — if no API keys are configured ({@code vibegraph.ai.gemini.api-keys}, which
 * also falls back to the single {@code GEMINI_API_KEY}), an {@link ResilientChatClient#unavailable()}
 * is returned and the LLM refiner stays in deterministic mode.
 *
 * <p>Each per-key model is built with {@code maxAttempts=1} (our matrix owns retries, not Spring's
 * RetryTemplate) so a 429 surfaces immediately and rotation is fast.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(GeminiRotationProperties.class)
public class GeminiChatClientConfig {

    @Bean
    public ResilientChatClient resilientChatClient(
            GeminiRotationProperties props,
            @Value("${spring.ai.google.genai.api-key:}") String fallbackKey) {
        List<String> keys = props.sanitizedKeys();
        // Fallback: if no multi-key list was given (or GEMINI_API_KEYS= is present but blank), use the
        // single configured key so existing single-key setups keep working.
        if (keys.isEmpty() && fallbackKey != null && !fallbackKey.isBlank()) {
            keys = List.of(fallbackKey.trim());
        }
        List<String> models = props.sanitizedModels();

        if (keys.isEmpty() || models.isEmpty()) {
            log.info("Gemini rotation DISABLED (apiKeys={}, models={}).", keys.size(), models.size());
            return ResilientChatClient.unavailable();
        }

        List<GeminiFailoverChatClient.KeyedModel> keyedModels = new ArrayList<>(keys.size());
        for (String key : keys) {
            // The genai Client carries the API key; build() does no network I/O.
            Client client = Client.builder().apiKey(key).build();
            GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                    .genAiClient(client)
                    // Default model is overridden per request; set the primary as a sane default.
                    .defaultOptions(GoogleGenAiChatOptions.builder()
                            .model(models.get(0))
                            .temperature(0.0)
                            .build())
                    // Disable Spring-level retry: the failover matrix is our retry strategy.
                    .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                    .observationRegistry(ObservationRegistry.NOOP)
                    .build();
            keyedModels.add(new GeminiFailoverChatClient.KeyedModel(mask(key), model));
        }

        log.info("Gemini rotation ENABLED: {} API key(s) × {} model(s), timeoutMs={}.",
                keyedModels.size(), models.size(), props.timeoutMsOrDefault());
        return new GeminiFailoverChatClient(keyedModels, models, props.timeoutMsOrDefault());
    }

    /** Log-safe key label: never reveal the full secret. e.g. {@code key(…aB12)}. */
    private static String mask(String key) {
        String tail = key.length() <= 4 ? key : key.substring(key.length() - 4);
        return "key(\u2026" + tail + ")";
    }
}
