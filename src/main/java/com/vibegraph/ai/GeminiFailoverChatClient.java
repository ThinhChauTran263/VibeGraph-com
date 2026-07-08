package com.vibegraph.ai;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

import lombok.extern.slf4j.Slf4j;

/**
 * Failover matrix over <b>API key × model</b> for the Google GenAI (Gemini) Developer API.
 *
 * <h2>Why one {@link ChatModel} per key</h2>
 * The API key is baked into the underlying {@code com.google.genai.Client} when the
 * {@code GoogleGenAiChatModel} is built — it is NOT a per-request option (unlike the model name,
 * which we override per request via {@link GoogleGenAiChatOptions}). So to rotate keys we hold one
 * pre-built {@link ChatModel} per key and switch the whole instance.
 *
 * <h2>Rotation order (smart failover)</h2>
 * <pre>
 *   for each KEY (key1, key2, ...):
 *       for each MODEL (model1 → model2 → model3 → model4):
 *           try key+model
 *           on 429 / 503 / timeout / blank  → rotate to the next MODEL on the SAME key
 *       all models failed on this key       → mark key exhausted, rotate to the next KEY
 *   every key × model failed                → return empty (caller falls back gracefully)
 * </pre>
 *
 * Each attempt is time-bounded so a slow/hanging model cannot block the request. API keys are never
 * logged in full — only a masked label ({@code key#1(…ab12)}).
 */
@Slf4j
public class GeminiFailoverChatClient implements ResilientChatClient {

    /** One pre-built model bound to a single API key, plus a log-safe masked label. */
    public record KeyedModel(String maskedKey, ChatModel chatModel) {}

    private final List<KeyedModel> keyedModels;
    private final List<String> models;
    private final long timeoutMs;
    private final ExecutorService executor;

    public GeminiFailoverChatClient(List<KeyedModel> keyedModels, List<String> models, long timeoutMs) {
        this.keyedModels = List.copyOf(keyedModels);
        this.models = List.copyOf(models);
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 12000L;
        ThreadFactory daemonFactory = r -> {
            Thread t = new Thread(r, "gemini-failover");
            t.setDaemon(true);
            return t;
        };
        this.executor = Executors.newCachedThreadPool(daemonFactory);
    }

    @Override
    public boolean isAvailable() {
        return !keyedModels.isEmpty() && !models.isEmpty();
    }

    @Override
    public Optional<String> generate(String prompt) {
        if (!isAvailable()) {
            log.warn("Gemini failover client not available (keys={}, models={}).",
                    keyedModels.size(), models.size());
            return Optional.empty();
        }

        for (int k = 0; k < keyedModels.size(); k++) {
            KeyedModel keyed = keyedModels.get(k);
            int keyNo = k + 1;

            // Try every model on THIS key before giving up on the key.
            for (int m = 0; m < models.size(); m++) {
                String model = models.get(m);
                Future<String> future = null;
                try {
                    // Override the MODEL per request (cheap); the KEY is fixed by this ChatModel instance.
                    // temperature=0 for deterministic, cacheable relabels.
                    Prompt request = new Prompt(prompt, GoogleGenAiChatOptions.builder()
                            .model(model)
                            .temperature(0.0)
                            .build());
                    future = executor.submit(() -> extractText(keyed.chatModel().call(request)));
                    String response = future.get(timeoutMs, TimeUnit.MILLISECONDS);

                    if (response != null && !response.isBlank()) {
                        log.info("Gemini OK via {} + model '{}' (key #{}/{}, model #{}/{}).",
                                keyed.maskedKey(), model, keyNo, keyedModels.size(), m + 1, models.size());
                        return Optional.of(response);
                    }
                    log.warn("{} + model '{}' returned blank — rotating model.", keyed.maskedKey(), model);

                } catch (TimeoutException te) {
                    if (future != null) {
                        future.cancel(true);
                    }
                    log.warn("{} + model '{}' timed out after {}ms — rotating model.",
                            keyed.maskedKey(), model, timeoutMs);
                } catch (Exception ex) {
                    log.warn("{} + model '{}' failed [{}]: {} — rotating model.",
                            keyed.maskedKey(), model, classify(ex), rootMessage(ex));
                }
            }

            // Every model on this key failed: the key is exhausted (quota) or invalid. Rotate key.
            boolean hasNextKey = k < keyedModels.size() - 1;
            log.warn("API {} exhausted: all {} models failed. {}", keyed.maskedKey(), models.size(),
                    hasNextKey ? "Rotating to next API key..." : "No more API keys.");
        }

        log.error("All {} API keys × {} models exhausted. Caller will fall back.",
                keyedModels.size(), models.size());
        return Optional.empty();
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    /** Classify the failure for readable logs. Distinguishes 429 (quota) from 503 (overload). */
    private String classify(Exception ex) {
        String msg = rootMessage(ex).toLowerCase(Locale.ROOT);
        if (msg.contains("429") || msg.contains("resource_exhausted") || msg.contains("quota")
                || msg.contains("rate limit")) {
            return "HTTP 429 - Quota/Rate limit";
        }
        if (msg.contains("503") || msg.contains("unavailable") || msg.contains("overloaded")
                || msg.contains("high demand")) {
            return "HTTP 503 - Server high demand";
        }
        return ex.getClass().getSimpleName();
    }

    /** Unwrap ExecutionException so the real cause message (e.g. the 429) shows in logs. */
    private String rootMessage(Throwable ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
