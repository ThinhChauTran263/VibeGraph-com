package com.vibegraph.diagram.service.impl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibegraph.ai.ResilientChatClient;
import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.UseCaseElement;

@DisplayName("LlmUseCaseRefiner - grounded relabelling with deterministic fallback")
class LlmUseCaseRefinerTest {

    private final ResilientChatClient chatClient = mock(ResilientChatClient.class);
    private final LlmUseCaseRefiner refiner = new LlmUseCaseRefiner(chatClient, new ObjectMapper());

    private UseCaseElement uc(String id, String name) {
        return UseCaseElement.builder().id(id).name(name).domain("Order").level("business")
                .source("domain:Order").confidence(0.8).build();
    }

    @Test
    @DisplayName("applies a relabel only to an id that exists in the model")
    void appliesGroundedRelabel() {
        List<UseCaseElement> in = List.of(uc("UC_ViewCheckouts", "View Checkouts"),
                uc("UC_ViewProducts", "View Products"));

        List<UseCaseElement> out = refiner.applyRelabels(in, "{\"UC_ViewCheckouts\":\"Checkout\"}");

        assertThat(out).extracting(UseCaseElement::getName).containsExactly("Checkout", "View Products");
        assertThat(out.get(0).getId()).isEqualTo("UC_ViewCheckouts");
        assertThat(out.get(0).getConfidence()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("ignores a hallucinated id not present in the model (grounding rejection)")
    void rejectsHallucinatedId() {
        List<UseCaseElement> in = List.of(uc("UC_ViewProducts", "View Products"));

        List<UseCaseElement> out = refiner.applyRelabels(in, "{\"UC_Ghost\":\"Anything\"}");

        assertThat(out).extracting(UseCaseElement::getName).containsExactly("View Products");
    }

    @Test
    @DisplayName("strips a markdown code fence around the JSON")
    void stripsCodeFence() {
        List<UseCaseElement> in = List.of(uc("UC_ViewShippings", "View Shippings"));

        List<UseCaseElement> out = refiner.applyRelabels(in,
                "```json\n{\"UC_ViewShippings\":\"Track Shipment\"}\n```");

        assertThat(out).extracting(UseCaseElement::getName).containsExactly("Track Shipment");
    }

    @Test
    @DisplayName("malformed JSON leaves the labels unchanged (fallback)")
    void malformedJsonFallsBack() {
        List<UseCaseElement> in = List.of(uc("UC_ViewProducts", "View Products"));

        List<UseCaseElement> out = refiner.applyRelabels(in, "not json at all");

        assertThat(out).isEqualTo(in);
    }

    @Test
    @DisplayName("an unavailable client (all keys/models failed) falls back to deterministic labels")
    void clientUnavailableFallsBack() {
        when(chatClient.generate(anyString())).thenReturn(Optional.empty());
        List<UseCaseElement> in = List.of(uc("UC_ViewProducts", "View Products"));

        List<UseCaseElement> out = refiner.refineLabels("Shop System", in);

        assertThat(out).isEqualTo(in);
    }

    @Test
    @DisplayName("a successful client reply relabels grounded ids end-to-end")
    void successfulCallRelabels() {
        when(chatClient.generate(anyString())).thenReturn(Optional.of("{\"UC_ViewCheckouts\":\"Checkout\"}"));
        List<UseCaseElement> in = List.of(uc("UC_ViewCheckouts", "View Checkouts"));

        List<UseCaseElement> out = refiner.refineLabels("Shop System", in);

        assertThat(out).extracting(UseCaseElement::getName).containsExactly("Checkout");
    }

    @Test
    @DisplayName("an empty {} reply keeps all labels (LLM decided nothing was awkward)")
    void emptyObjectKeepsLabels() {
        when(chatClient.generate(anyString())).thenReturn(Optional.of("{}"));
        List<UseCaseElement> in = List.of(uc("UC_ViewProducts", "View Products"),
                uc("UC_ViewAnalytics", "View Analytics"));

        List<UseCaseElement> out = refiner.refineLabels("Shop System", in);

        assertThat(out).extracting(UseCaseElement::getName).containsExactly("View Products", "View Analytics");
    }

    @Test
    @DisplayName("identical input is cached: the client is called once, the second call reuses the result")
    void cachesByInputHash() {
        when(chatClient.generate(anyString())).thenReturn(Optional.of("{\"UC_ViewProducts\":\"Browse Products\"}"));
        List<UseCaseElement> in = List.of(uc("UC_ViewProducts", "View Products"));

        List<UseCaseElement> first = refiner.refineLabels("Shop System", in);
        List<UseCaseElement> second = refiner.refineLabels("Shop System", in);

        assertThat(first).extracting(UseCaseElement::getName).containsExactly("Browse Products");
        assertThat(second).extracting(UseCaseElement::getName).containsExactly("Browse Products");
        // Second invocation served from cache — the client is only hit once.
        verify(chatClient, times(1)).generate(anyString());
    }
}
