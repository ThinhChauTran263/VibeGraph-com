package com.vibegraph.auth.dto;

import java.util.UUID;

/** Owner-facing one-shot reveal of a previously stored API key secret. */
public record ApiKeyRevealResponse(UUID id, String secretKey) {
}
