package com.vibegraph.diagram.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.vibegraph.ai.ResilientChatClient;
import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.UseCaseElement;

import lombok.extern.slf4j.Slf4j;

/**
 * Tier 2 LLM refiner (Requirement 5). Asks a chat model to improve awkward use-case labels
 * (e.g. {@code "View Checkouts"} → {@code "Checkout"}) under hard guardrails:
 *
 * <ul>
 *   <li><b>Fact-grounded input</b>: only the inferred use-case ids/names/domains are sent — never raw
 *       source code — preserving the source-code boundary.</li>
 *   <li><b>Schema-constrained output</b>: a flat JSON object {@code {id: newLabel}}; temperature 0 is
 *       set for determinism.</li>
 *   <li><b>Grounding rejection</b>: only renames for ids that exist in the model are applied; any
 *       hallucinated id is dropped. The LLM can never add/remove use cases, actors, or relations.</li>
 *   <li><b>Deterministic fallback</b>: any error (LLM down, malformed JSON, …) returns the input
 *       unchanged.</li>
 * </ul>
 *
 * <h2>Key & model rotation</h2>
 * Failover over the matrix of (API key × model) lives behind {@link ResilientChatClient}: this
 * refiner just asks it to {@link ResilientChatClient#generate(String)} and treats an empty result as
 * "use deterministic labels". That keeps the relabel grounding logic here and the
 * quota/rotation/timeout logic in one reusable place.
 */
@Slf4j
public class LlmUseCaseRefiner implements UseCaseSemanticRefiner {

    private static final String PROMPT_HEADER = """
            You audit UML use-case goal labels and fix ONLY the ones that read awkwardly.

            STRICT RULES:
            1. DEFAULT IS KEEP. Most labels are already correct — leave them unchanged.
            2. A label is GOOD (keep, do NOT output it) when it is "Verb + real business noun":
               e.g. "View Products", "View Analytics", "Manage Orders", "Register Account",
               "Log In", "Manage Payments". These are final — never rephrase them to synonyms.
            3. A label is AWKWARD (fix it) ONLY when the object is a nominalized verb / gerund or a
               non-noun pluralized into a fake entity:
               "View Checkouts" -> "Checkout", "View Shippings" -> "Track Shipment",
               "Manage Loggings" -> "Manage Logs", "Manage Caffeines" -> "Manage Caffeine".
            4. Do NOT swap a correct verb for a synonym (NOT "View Products" -> "Browse Products",
               NOT "View Analytics" -> "Analyze Data"). Keep the verb; only fix the object/phrasing.
            5. Do NOT invent ids. Do NOT add or remove use cases. Do NOT add commentary.
            6. PRESERVE any trailing scope qualifier exactly: " (Own)" or " (All)". These mark
               user-vs-admin scope and MUST stay (e.g. keep "Manage Orders (Own)" as-is).

            OUTPUT: a JSON object containing ONLY the ids you are CHANGING, mapping id -> new label.
            If nothing needs changing, return exactly {}.
            Example (only "UC_ViewCheckouts" was awkward): {"UC_ViewCheckouts":"Checkout"}.
            Use cases (JSON):
            """;

    private final ResilientChatClient chatClient;
    private final ObjectMapper objectMapper;
    // Cache the LLM decision (raw response) keyed by a hash of the fact input, so an unchanged graph
    // never pays the LLM latency twice (R5.3). Only successful responses are cached; failures retry.
    // B-M6: bounded + TTL (was an unbounded ConcurrentHashMap growing heap without limit).
    private final Cache<String, String> responseCache = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    public LlmUseCaseRefiner(ResilientChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<UseCaseElement> refineLabels(String systemName, List<UseCaseElement> useCases) {
        if (useCases == null || useCases.isEmpty()) {
            return useCases;
        }
        String facts;
        try {
            facts = factJson(useCases);
        } catch (Exception ex) {
            log.warn("LLM use-case refiner: could not build fact payload; using deterministic labels.");
            return useCases;
        }
        // Cache by input hash: an unchanged set of goals reuses the prior LLM decision (no second call).
        String cacheKey = sha256(facts);
        String cached = responseCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("LLM use-case refiner cache HIT; reusing prior relabels.");
            return applyRelabels(useCases, cached);
        }
        // The client runs the API-key × model failover matrix; empty == every combination failed.
        Optional<String> response = chatClient.generate(PROMPT_HEADER + facts);
        if (response.isEmpty()) {
            log.warn("LLM relabel unavailable (all keys/models failed); using deterministic labels.");
            return useCases;
        }
        String raw = response.get();
        log.info("LLM use-case refiner relabel response on {} use cases: {}", useCases.size(), raw);
        responseCache.put(cacheKey, raw);
        return applyRelabels(useCases, raw);
    }

    /** Stable hex SHA-256 of the fact payload, used as the relabel cache key. */
    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available; fall back to the raw payload as the key.
            return input;
        }
    }

    /** Fact-grounded payload: only inferred ids/names/domains — never source code. */
    private String factJson(List<UseCaseElement> useCases) throws com.fasterxml.jackson.core.JsonProcessingException {
        List<Map<String, String>> facts = new ArrayList<>();
        for (UseCaseElement uc : useCases) {
            Map<String, String> f = new LinkedHashMap<>();
            f.put("id", uc.getId());
            f.put("name", uc.getName());
            f.put("domain", uc.getDomain());
            facts.add(f);
        }
        return objectMapper.writeValueAsString(facts);
    }

    /**
     * Apply grounded relabels: parse the JSON map, and rename only use cases whose id is present in
     * the model with a non-blank new label. Returns a NEW list (inputs are not mutated). Unknown ids
     * and malformed JSON are ignored — a malformed response yields the input unchanged.
     */
    List<UseCaseElement> applyRelabels(List<UseCaseElement> useCases, String response) {
        Map<String, String> relabels = parseRelabels(response);
        if (relabels.isEmpty()) {
            return useCases;
        }
        List<UseCaseElement> out = new ArrayList<>(useCases.size());
        for (UseCaseElement uc : useCases) {
            String newName = relabels.get(uc.getId());
            if (newName != null && !newName.isBlank() && !newName.equals(uc.getName())) {
                out.add(UseCaseElement.builder()
                        .id(uc.getId())
                        .name(newName.trim())
                        .domain(uc.getDomain())
                        .level(uc.getLevel())
                        .source(uc.getSource())
                        .sourceEndpoint(uc.getSourceEndpoint())
                        .confidence(uc.getConfidence())
                        .build());
            } else {
                out.add(uc);
            }
        }
        return out;
    }

    private Map<String, String> parseRelabels(String response) {
        if (response == null || response.isBlank()) {
            return Map.of();
        }
        String json = stripCodeFence(response).trim();
        try {
            Map<String, String> parsed = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, String.class));
            return parsed == null ? Map.of() : parsed;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.debug("Could not parse LLM relabel JSON; ignoring. Cause: {}", ex.getMessage());
            return Map.of();
        }
    }

    /** Strip a ```json ... ``` (or ``` ... ```) markdown fence the model may wrap the JSON in. */
    private String stripCodeFence(String text) {
        String t = text.trim();
        if (!t.startsWith("```")) {
            return t;
        }
        int firstNl = t.indexOf('\n');
        if (firstNl < 0) {
            return t;
        }
        String body = t.substring(firstNl + 1);
        int closing = body.lastIndexOf("```");
        return closing >= 0 ? body.substring(0, closing) : body;
    }
}
