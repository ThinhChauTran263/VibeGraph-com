package com.vibegraph.abuse;

import com.vibegraph.abuse.entity.RequestEvent;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestEventResponseTest {

    @Test
    void from_RawLookingApiKeyRef_ReturnsMaskedReference() {
        RequestEvent event = RequestEvent.builder()
                .id(UUID.randomUUID())
                .apiKeyRef("key-id:vbg_rawsupersecretvalue")
                .ipAddress("203.0.113.10")
                .route("/mcp")
                .method("POST")
                .status(200)
                .eventType("REQUEST")
                .occurredAt(Instant.parse("2026-07-19T10:00:00Z"))
                .build();

        RequestEventResponse response = RequestEventResponse.from(event);

        assertThat(response.apiKeyRef()).isEqualTo("vbg_raws****");
        assertThat(response.apiKeyRef()).doesNotContain("supersecretvalue");
    }
}
