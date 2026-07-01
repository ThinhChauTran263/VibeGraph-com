package com.vibegraph.ai;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

@DisplayName("GeminiFailoverChatClient - API key x model rotation matrix")
class GeminiFailoverChatClientTest {

    private static final List<String> MODELS = List.of("model-1", "model-2");

    private ChatResponse resp(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private GeminiFailoverChatClient client(ChatModel key1, ChatModel key2) {
        return new GeminiFailoverChatClient(List.of(
                new GeminiFailoverChatClient.KeyedModel("key(\u20261111)", key1),
                new GeminiFailoverChatClient.KeyedModel("key(\u20262222)", key2)),
                MODELS, 5000L);
    }

    @Test
    @DisplayName("exhausts all models on key1, then rotates to key2 and succeeds")
    void rotatesKeyAfterAllModelsFail() {
        ChatModel key1 = mock(ChatModel.class);
        ChatModel key2 = mock(ChatModel.class);
        // key1: every model fails with a quota error.
        when(key1.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("429 RESOURCE_EXHAUSTED"));
        // key2: first model succeeds.
        when(key2.call(any(Prompt.class))).thenReturn(resp("OK"));

        Optional<String> out = client(key1, key2).generate("prompt");

        assertThat(out).contains("OK");
        // All models tried on key1 (2), then key2 model-1 (1).
        verify(key1, times(MODELS.size())).call(any(Prompt.class));
        verify(key2, times(1)).call(any(Prompt.class));
    }

    @Test
    @DisplayName("first key + first model success short-circuits (no key rotation)")
    void firstComboSucceeds() {
        ChatModel key1 = mock(ChatModel.class);
        ChatModel key2 = mock(ChatModel.class);
        when(key1.call(any(Prompt.class))).thenReturn(resp("FIRST"));

        Optional<String> out = client(key1, key2).generate("prompt");

        assertThat(out).contains("FIRST");
        verify(key1, times(1)).call(any(Prompt.class));
        verify(key2, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("every key x model failing returns empty (caller falls back)")
    void allCombosFailReturnsEmpty() {
        ChatModel key1 = mock(ChatModel.class);
        ChatModel key2 = mock(ChatModel.class);
        when(key1.call(any(Prompt.class))).thenThrow(new RuntimeException("503 UNAVAILABLE"));
        when(key2.call(any(Prompt.class))).thenThrow(new RuntimeException("429 quota"));

        Optional<String> out = client(key1, key2).generate("prompt");

        assertThat(out).isEmpty();
        verify(key1, times(MODELS.size())).call(any(Prompt.class));
        verify(key2, times(MODELS.size())).call(any(Prompt.class));
    }

    @Test
    @DisplayName("unavailable when no keys configured")
    void unavailableWithoutKeys() {
        GeminiFailoverChatClient empty = new GeminiFailoverChatClient(List.of(), MODELS, 5000L);
        assertThat(empty.isAvailable()).isFalse();
        assertThat(empty.generate("x")).isEmpty();
    }
}
