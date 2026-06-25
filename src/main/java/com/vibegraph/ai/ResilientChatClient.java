package com.vibegraph.ai;

import java.util.Optional;

/**
 * A chat client that hides API-key and model failover behind a single call. Implementations rotate
 * over a matrix of (API key × model) and return the first successful reply, or {@link Optional#empty()}
 * when every combination is exhausted — never throwing to the caller.
 */
public interface ResilientChatClient {

    /**
     * Run the prompt through the failover matrix.
     *
     * @param prompt the fully-built prompt text
     * @return the first non-blank model reply, or empty if all keys × models failed
     */
    Optional<String> generate(String prompt);

    /** @return true when at least one (key, model) combination is configured and usable. */
    boolean isAvailable();

    /** A no-op client that is never available — used when LLM is disabled or unconfigured. */
    static ResilientChatClient unavailable() {
        return new ResilientChatClient() {
            @Override
            public Optional<String> generate(String prompt) {
                return Optional.empty();
            }

            @Override
            public boolean isAvailable() {
                return false;
            }
        };
    }
}
